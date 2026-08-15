# ADR-0005: CSS subset and KDL as the contracts

- **Status:** Accepted (recorded retroactively)
- **Date:** 2026-08-15
- **Relates to:** `docs/ARCHITECTURE.md` §8, §9, §10

## Context

A toolkit needs a way to express styling and a way to express structure. Both are
usually invented in-house, and both in-house inventions have the same failure
mode: they are almost, but not quite, a thing the user already knows. Every
proprietary styling DSL ends up reimplementing a worse cascade; every bespoke
markup format ends up needing comments, escaping, and a schema.

There is also a second-order requirement. Hot reload is a goal (§8), and a design
system with themes (§10) needs variables and a cascade with defined precedence.
Both are far easier against a format with real semantics than against a builder
API.

## Decision

**Styling: a genuine CSS subset**, parsed by a pure-Java css-syntax-compatible
tokenizer — no native code. Selectors are limited to type, `.class`, `#id`,
descendant, child, and six pseudo-classes, with standard specificity. Cascade
layers are fixed at four: toolkit base → theme → application → inline. Custom
properties and `var()` are the theming mechanism, which is what makes Nord light
and dark a stylesheet swap rather than a code path (§10).

The property split is a design invariant, not an implementation detail: *layout*
properties compile directly to Yoga, *paint* properties resolve into an immutable
`ComputedStyle`. A property that cannot be assigned to one side or the other does
not belong in the subset.

**Markup: KDL 2.0**, and the markup schema — not the Java API — is the stable
contract. The Java builder API is generated to stay in lockstep with it.

**The parity invariant, enforced by test:** every built-in widget is constructible
from Java, constructible from KDL, and styleable via CSS. A widget that is not is
a build failure.

## Alternatives considered

- **A proprietary styling DSL.** Rejected: it would have to grow a cascade,
  variables, and specificity anyway, and would arrive at a worse CSS that nobody
  already knows.
- **Full CSS.** Rejected: unbounded scope. Grid, floats, and the full selector
  grammar are years of work for features a desktop toolkit does not need. The
  subset is drawn where flexbox ends.
- **XML or FXML for markup.** Rejected: verbose, and FXML's reflective handler
  binding is exactly the magic §9 avoids — wiring is explicit
  (`Kdl.inflate(doc).bind(controller)`).
- **JSON or YAML.** Rejected: JSON has no comments; YAML's ambiguity is a known
  source of configuration bugs. KDL is designed for hand-authored documents.
- **Java builders only.** Rejected: no hot reload, and no format for tooling to
  target.

## Consequences

- Users bring existing CSS knowledge, and theming is a stylesheet swap.
  Stylesheets are runtime-loadable, so hot reload preserves application state.
- Users also bring existing CSS *expectations*, and will hit the subset's edges.
  The boundary must be documented precisely, or every missing selector reads as a
  bug. `calc()` is deferred and will be missed.
- Invalidation is coarse in v1 — a pseudo-class or class change recomputes the
  subtree. This is a known performance cliff on large trees, accepted for v1.
- KDL 2.0 needs a Java parser. **Open:** whether a suitable one exists at the
  required maturity or whether Goldberry writes its own. This must be settled
  before M2 can be scheduled honestly.
- The parity invariant costs something on every new widget — three surfaces to
  implement instead of one — and that is the point: it is what stops the KDL and
  CSS paths from quietly becoming second-class.
