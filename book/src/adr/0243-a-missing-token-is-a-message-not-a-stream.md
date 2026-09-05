# 243. A missing token is a message, not a stream

Date: 2026-09-05

## Status

Accepted. Closes the `TODO.md` entry opened by
[ADR-0121](0121-a-tour-is-a-veil-and-a-sequence.md), and applies
[ADR-0216](0216-a-corner-is-four-numbers-and-a-lint-reads-values-too.md)'s answer
one stage earlier in the same pipeline.

## Context

The entry names both the problem and the fix:

> Nothing warns that a `var()` resolved to nothing — it logs, per node, per
> frame. One missing token is a stream rather than a message, which is how two of
> them survived long enough to reach a user. `TokenClosureTest` and
> `ShowcaseTokensTest` now fail the build instead, so the log is no longer the
> only line of defence; the log itself is still noise. Saying it once per property
> per stylesheet would make it a diagnostic.

`ComputedStyle` had already met this exact problem one stage later and solved it
(ADR-0216): a stylesheet is **static**, so a declaration that cannot be applied
cannot be applied on the next frame either — but a style is resolved per element
per invalidation, so one typo reported itself sixty times a second for as long as
the screen it was on kept moving. Its comment puts it better than a summary
would: *"That is not a louder warning, it is a quieter log."*

The cascade has two warnings with the same shape and neither was deduplicated:
the unresolvable `var()`, and a custom property that refers to itself.

## Decision

**The same mechanism, one stage earlier — and an instance field rather than a
static one.**

`ComputedStyle`'s `REPORTED` is static because `ComputedStyle` is a record with
static factory methods and there is nowhere else to put it, and it needs a public
`forgetReportedDrops()` so tests in two modules can clear it.

`StyleResolver` is an object, built per stylesheet set and living as long as the
renderer that holds it. So "once per resolver" **is** "once per stylesheet",
which is what the entry asked for, and it comes out better in three ways:

- **A theme swap reports again, correctly.** Swapping builds a new renderer and
  therefore a new resolver, and what the *new* theme is missing is news rather
  than a repeat.
- **Tests get isolation from constructing a resolver**, not from remembering to
  call a static `forget` hook — the failure mode where the second test in a class
  depends on whether the first tripped the same warning.
- **Nothing leaks between unrelated stylesheet sets** in one JVM.

### Keyed by property *and* element type

The entry says "once per property per stylesheet", and this is one refinement of
it. The same token failing on `button` and on `text` is two facts, and which
types an unresolvable token reaches is exactly the blast radius somebody
debugging it wants. It stays bounded either way: a stylesheet has finitely many
declarations and a tree finitely many types, which is the property that makes
this a diagnostic rather than a stream.

A **cycle** is keyed by the property name alone, because a custom property
referring to itself is a fact about the property and not about whatever element
happened to ask for it first.

`REPORT_LIMIT` is `ComputedStyle`'s 512 and its argument, unchanged: past that
many distinct drops something is generating them, and a log that went quiet would
hide it.

## Alternatives considered

- **Reuse `ComputedStyle`'s static set.** One mechanism for two stages sounds
  tidier and welds the resolver's lifetime to a static that nothing resets on a
  theme swap — so the first theme's missing token would silence the second
  theme's report of the same property.
- **Log at DEBUG instead of WARN.** That is the state ADR-0216 already argued
  against for dropped values: `group-box-title` drew square corners for months
  behind a DEBUG line. Quieting a diagnostic is not the same as making it one.
- **Report only the first drop and nothing after.** It loses the blast radius,
  and the one line you get names whichever element happened to resolve first —
  which is frame-order dependent and therefore not reproducible.
- **Count and summarise at the end of a frame.** There is no end-of-frame hook in
  the cascade, and a count without the property name is not something anybody can
  act on.
- **Leave it, since `TokenClosureTest` and `ShowcaseTokensTest` now fail the
  build.** Those cover the toolkit's own sheets. An application's are exactly
  what the log is for, and it is the application author who cannot read it.

## Consequences

- **`substitute` and `expandVar` are instance methods now.** They were static and
  the cycle report needed the field; both are private-or-package and neither had
  a caller outside this class, so the change is invisible.
- **`reportedDrops()` is package-private, for the test.** The thing worth
  asserting is that one bad `var()` is *one* message however many elements hit
  it, and only `slf4j-api` is on the classpath — there is no appender to read the
  log back from, and a logging backend bought for one assertion would be a
  dependency this does not need. The accessor says so in its own comment.
- **Four tests, three of which fail against the old code**: five resolves of one
  element report once, a second element type is a second report, a new resolver
  reports again, and a self-referring property does not grow per frame.
- **`descend` walks depth, not siblings** — which the first draft of two of those
  tests got wrong and which cost a `NoSuchElementException` to find out. They
  build a fresh `window > type` tree per case instead, and the helper says why.
- **The drop itself is unchanged.** Only the *report* is once; making the drop
  conditional would be a stylesheet that behaved differently on the second frame,
  which is the trap `ComputedStyle`'s own test names.
