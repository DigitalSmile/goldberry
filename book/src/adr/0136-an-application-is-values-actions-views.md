# 136. An application is values, actions, views

Date: 2026-08-19

## Status

Accepted. Names the shape the previous eleven records arrived at, and writes it
down as [a guide](../applications.md) rather than leaving it to be inferred from
the showcase.

## Context

Nine records between ADR-0125 and ADR-0135 changed how an application is written,
each for a local reason: a model stopped holding `Property`, then stopped holding
accessors, then stopped asking for frames, then stopped merging registries. Each
step was an improvement and none of them said what the *result* was.

That gap shows up as questions no record answers. Where does a scroll offset go?
Is a derived getter logic or a value? What may a widget know? Somebody reading
the log gets eleven local decisions and has to infer the shape, which is exactly
the thing a decision log is bad at.

The showcase demonstrated it and did not explain it — and until this record, did
not even follow it: `ShowcaseModel` was 248 lines of values *and* the methods that
changed them.

## Decision

**Four kinds of class, and the arrows only point one way.**

```
Values ────────► Views ────────► Actions ────────► Values
   (read)          (report)         (assign)         (notify)
```

- **Values** — a `@Model` class of plain fields. Knows nothing: no widget, no
  window, no toolkit type beyond `@Bind`.
- **Actions** — a `@Model` record wrapping the values. One method per thing a
  control can ask for. Knows the values, and nothing else.
- **Views** — `Widget` records, or a `.kdl` document. Knows the values it reads
  and the actions it calls, and **cannot write**, because what it is handed is an
  `Observable` with no `set` on it.
- **Application** — one `implements Application`. The only class that knows a
  window exists.

`models()` is the whole of the wiring: from that one list the toolkit resolves a
document's names, repaints when a value asks, and restyles when a value declared
`restyle = true` moves.

### Values are a class; actions are a record

Not a style preference — each is the only shape that works. A record's components
are final, and a bound field has to be assignable, so the values cannot be a
record. The actions hold one thing immutably and have no state of their own, so
they are the half a record fits exactly. "Wanting a mutable field on the actions"
is the signal that the thing is state and belongs with the values; the showcase's
`added` counter went that way.

### Splitting is available, not required

An application that keeps its values and its methods in one class loses nothing.
The split costs `private` on the fields — a class that assigns to another's field
has to see it, and the package is the smallest visibility that allows it
(ADR-0134). Worth it when the values are worth reading on their own; not worth it
for a model with three fields.

### Derived reads stay with the values

`theme()` turning `"light"` into `Theme.NORD_LIGHT` is a question about the fields
with exactly one answer. Putting it in a class named for writes would be worse
than leaving it. "No logic in the model" means no *transitions*, not no
projections.

## Consequences

`ShowcaseModel` went from 248 lines to 125, and is now fields and four
projections. `ShowcaseActions` is 157 lines of one-line methods. Neither is
shorter than the sum was; what changed is that one of them can be read in
isolation and answers "what does this application know?".

**The showcase now has three models** — values, actions, and the window itself —
which is the shape the guide describes and a useful proof that multi-model wiring
is not a special case.

**Views gained a constructor parameter.** `Content(model, plus)` became
`Content(model, actions, plus)`, and so on. That is the arrow made explicit: a
view that reads and reports now says so in its signature, where before it took one
object that did both.

**`book/src/applications.md` is the deliverable**, and this record exists mainly
to date it and say why it was needed. A shape that lives only in a showcase is a
shape every reader re-derives.

**The risk is drift.** The guide describes what the showcase does, and nothing
enforces the agreement. A test could — "no `@Action` on a class with `@Bind`
fields", say — and deliberately does not: the split is a recommendation, and
mechanically enforcing a recommendation turns it into a rule nobody agreed to.
