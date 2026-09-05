# 257. A diagnostic is asked for, not logged

Date: 2026-09-05

## Status

Accepted. Closes four `TODO.md` entries that share one shape: the toolkit knows
something is wrong and says nothing, or says it where nobody is listening.

## Context

Four entries, written at four different times, arrive at the same conclusion from
four directions.

> **Nothing warns when a declaration is dropped for being unsupported** — in an
> *application's* stylesheet. … the four of them cost more to find than a warning
> would have cost to read. **The toolkit's own sheets are linted now** (ADR-0215),
> on the value half as well (ADR-0216); what is left open is the author writing
> their own. And the second record is the argument that a louder log is not the
> answer: a dropped *value* already warns, and `group-box-title` drew square
> corners for months anyway.

> **An application's stylesheet can still be all classes, and nothing says so.** …
> A warning that is usually wrong is the log ADR-0243 has just finished
> quietening; **what would help instead is a diagnostic somebody asks for**, next
> to the `hud`.

> **`flex-grow` means nothing inside a `scroll`, and nothing says so.** … The
> showcase had it on five screens where it did nothing and on one where it was
> load-bearing, which is exactly how long it takes for a dead declaration to look
> like a live one.

> **A row height that disagrees with the stylesheet is a silent layout error.**
> … the symptom is rows drifting out of step with the scrollbar, and it gets
> worse the further down the model you are.

The first two name the answer in as many words: **asked for**, not logged. The
second two are the same problem one level down — a widget that knows a
declaration will do nothing and draws it anyway.

## Decision

### `css.lint`, which is the test with the test taken off it

`SupportedPropertyTest` had the machinery already and it worked: resolve every
rule through the **real** cascade, hand every declaration to the **real**
`ComputedStyle`, and report what came back. What it did not have was a caller
other than itself. It installed a logback appender on `ComputedStyle`, set it to
`DEBUG`, and read the complaints back out as sentences.

`StyleLint` is that, returning `Finding` values. An application asks:

```java
new StyleLint(everythingLoaded).check(mine).forEach(f -> LOG.warn("{}", f));
```

**Two sheets, not one**, and it is not ceremony: half of what a declaration means
is what its `var()`s stood for, and a sheet linted without its theme reports every
colour in it as a value the engine refuses. That was 164 false findings while the
test was being written, and the constructor is where the lesson is kept.

A finding carries the **line and column** the parser saw, which a log line never
did.

### `ComputedStyle.applies` is four lines, and could not have been fewer

The question a lint asks is "does the engine do anything with this?", and the
answer was already sitting in the control flow: **`with` returns `this` in
exactly two places and both of them are failures** — the `default` arm, where
§8's subset has no such property, and `dropped`, where it has one and the value
would not parse. Every success goes through a wither and every wither allocates.

So identity is the answer, and it cannot drift, because it is not a copy of the
behaviour — it *is* the behaviour. A list of supported properties would have been
a second source of truth that needed editing every time the first one changed.

That is a real constraint on `ComputedStyle` rather than a happy accident, and it
is written down in both places: an arm that returned `this` on success would
silently become "does nothing".

**The two failures are not told apart.** Distinguishing "no such property" from
"no such value" means the engine *reporting* rather than being asked — a sink
threaded through thirty switch arms — for a difference the author reads off §8's
list in either case. What was invisible is that the rule does nothing.

### An unresolvable `var()` is the resolver's report and not a finding

A `var()` naming a token nothing defines takes the whole declaration with it
before the engine ever sees one, and the resolver already says so **once**, which
is the shape [ADR-0243](0243-a-missing-token-is-a-message-not-a-stream.md)
settled on. Saying it again here would be a second mechanism for one fault,
disagreeing with the first the day either changes. A test asserts the silence so
that the next reader finds the reason rather than the gap.

### Two widgets that knew and did not say

Both follow ADR-0251's `warnIfNestedOnTheSameAxis` exactly — a diagnostic and
never a refusal, deduplicated in a static set, because turning a rule into a
crash is worse than the rule going unheard.

**`ScrollContent`** says once that a child inside it declares `flex-grow` and will
get nothing. Read off the **boxes** rather than off the cascade, which makes it
exact and free: `flex-grow` is resolved by the time `render` is handed its
children, so it is a field comparison — and it catches a widget that set the
growth itself, which no rule in any stylesheet would have shown.

**`ListRow`** says once that the pitch its list is spacing rows at is not the
height `list-row` resolved to. Read off the **cascade**: `list-row` declares
`height: var(--gb-list-row-height)`, so the number exists before the row is laid
out and no `Measured` round trip is needed to learn it. A row whose height is
`auto` says nothing, because there is no declared number to disagree with.

That is a simpler mechanism than the entry predicted — it asked for "a `Measured`
assertion on the first built row" — and it reaches the same fault a frame
earlier.

### The pitch check has to see the mismatch twice, and ADR-0254 is why

**The first build of a tree has no cascade.** A `Stateful` widget builds once
inside the `ElementTree` constructor, before any renderer has taken the tree on,
so a list reading `--gb-list-row-height` answers the token's *default* on that
build and the stylesheet's value on the next
([ADR-0254](0254-a-build-may-ask-the-cascade-for-a-number.md)). Under a compact
density that is one frame of a 32px pitch against 26px rows: a real disagreement,
for one frame, that nobody sees and that settles by itself.

Reported naively, the form that **cannot** be wrong would have been the noisiest
one. So a mismatch is believed on its second sighting: a genuine one recurs every
frame for as long as the list is up, and a settling one is seen once and never
again, because the pair it is keyed under stops occurring.

That forced the second decision. The check runs on **one row of the window**, not
on all of them — every row resolves the same height, so twenty rows would report
twenty times per frame and "seen twice" could not tell a frame from a sibling.
The first built row carries it, which is where the entry said the assertion
belonged.

## Alternatives considered

- **A louder log.** The thing both entries rule out, and ADR-0216 is the evidence:
  a dropped value already warns at `WARN` and `group-box-title` drew square
  corners for months. A stream nobody is watching is a stream at either level.
- **Failing the build on a finding.** Not the toolkit's call. `Finding.Kind`
  carries `isDefect()` so an application can draw the line itself — a dead
  declaration is a drawing that is not happening, and an untyped rule draws
  correctly and costs the cascade more than it needs to.
- **Reporting untyped rules from the engine.** They are already
  `StyleResolver.untypedSelectors()`, which existed for `RuleBucketTest` and
  needed only somewhere to be asked from.
- **A `Measured` callback on every row.** What the entry proposed. It is a frame
  late, it adds a callback component to a widget that already has three, and the
  number it would measure is one the cascade already resolved.
- **Threading a diagnostic sink through the engine.** The exact version of the
  two-kind split, and thirty edited switch arms in the hot path of the frame loop
  for a distinction the author does not need.
- **A `hud`-style overlay.** What the `TODO.md` entry literally suggested — "next
  to the `hud`". A screen is a worse place to read a hundred findings than a list
  is, and nothing stops one being built on top of this later.

## Consequences

- **`SupportedPropertyTest` lost a hundred and sixty lines**, and with them a
  logback appender, a log-level override, two sentence-matching filters and its
  own two guard tests — which existed because a change to either log's wording
  would have made it pass by seeing nothing at all. Findings are values, so the
  guards are `StyleLintTest`'s and are assertions about behaviour.
- **It gained two sweeps it could not previously afford**: the **light theme** and
  the **compact density**. A `var()` that resolves to something legal in one theme
  and to nothing in the other is a rule that draws on one and not the other, and
  no golden of the dark theme could have shown it. Both pass.
- **`css.lint` is not `@NullMarked`**, and the reason is not this package.
  `StyleElement` documents three members as "or null" — `type()`, `id()` and
  `parent()` — and annotates none, inside a `css` package that *is* marked. The
  lint's probe is the first implementation of that interface to be written in a
  marked package, and it cannot say what the interface's own javadoc says.
  Recorded in `TODO.md` rather than fixed in passing: annotating it moves every
  implementation and every caller.
- **`ListRow` grew a `double`**, not a callback. It is set on one row of the
  window and zero on the rest, and both facts are documented on the component,
  because "the same number on every row would be fine" is what a later reader
  will think.
- **Three static report sets now exist** — `ComputedStyle`'s, `ScrollState`'s and
  now `ScrollContent`'s and `ListRow`'s — each with a `forget` for tests. That is
  a pattern rather than a mechanism, and the fourth one is the point at which
  somebody should extract it.
