# 247. `start` is CSS, and `flex-start` is Yoga

Date: 2026-09-05

## Status

Accepted. Answers the question left open by
[ADR-0216](0216-a-corner-is-four-numbers-and-a-lint-reads-values-too.md)'s
`TODO.md` entry.

## Context

The entry recorded a fix and left a question:

> The toolkit accepts Yoga's spelling of these keywords and not CSS's aliases, so
> `start`, `end` and `space-between`-style names have exactly one correct form
> each and a document that uses the other gets a warning rather than a mapping.
> Whether to accept the aliases is open; accepting them means a second table to
> keep in step with Yoga's enum.

The framing has one word wrong in it, and the word is what decides the answer:
**`align-items: start` is not an alias, it is CSS.** Box Alignment Level 3 defines
`start` and `end` for `align-items`, `align-self` and `justify-content`, and
every browser takes them. Yoga has only `flex-start` and `flex-end`.

So this was not a toolkit choosing one spelling among two conveniences. It was a
toolkit **dropping a declaration the specification allows**, and telling the
author they had made a mistake. It filled the Panels screen's console for long
enough to need deduplicating before anybody asked whether the declaration was
actually wrong.

## Decision

**`start` and `end` resolve to `FLEX_START` and `FLEX_END`.** Two entries in one
map, applied in `keyword` — the one place every enum-valued property is parsed,
so `align-items`, `align-self` and `justify-content` all get it without three
edits.

**After the enum's own lookup, not before.** An enum that ever gains a constant
called `START` keeps its own meaning rather than being shadowed by a mapping
written for a different one. This is a one-line ordering that costs nothing and
removes a whole class of future surprise.

**Two entries and no more**, which is the part that keeps the "second table to
keep in step" cost at zero:

- `start`/`end` are writing-mode-relative in full CSS and identical to the flex
  pair in a subset with one writing mode and no grid. That is what makes the
  mapping **exact** rather than approximate.
- `left` and `right` are deliberately absent. They are `justify-content` only,
  they are *not* the same as `start`/`end` under RTL, and §2.4's bidi support
  (ADR-0218) means the toolkit cannot promise they would stay equivalent. They
  are dropped like any keyword it has not got.

## Alternatives considered

- **Keep exactly one spelling and the warning.** Defensible for a private
  vocabulary and wrong for CSS: §5 says the stylesheet language *is* CSS, and a
  subset that refuses valid CSS is a subset with a bug rather than a boundary.
- **Map the whole of Box Alignment.** `self-start`, `self-end`, `normal`,
  `safe`/`unsafe` — none of which Yoga can express, so each would be a mapping
  that is nearly right, which is worse than a drop.
- **Translate in the parser rather than in `keyword`.** The parser does not know
  which property it is reading, and a translation applied to every ident would
  reach `font-family: start`.
- **Accept them and warn anyway.** A warning about correct code is what this
  record is removing.

## Consequences

- **Two existing tests changed meaning and were rewritten**, both in the group
  that exists *because* of this typo. `alwaysDropped` used `align-items: start`
  as its example of a value the toolkit has not got and now uses one it really
  has not got, `sideways`. And "`start` is not `flex-start`" is now "`start` and
  `flex-start` are the same value" — the original report was right that the value
  never reached Yoga and wrong that the author had made a mistake.
- **The deduplication ADR-0216 built is untouched and still needed.** A dropped
  declaration is still reported once; there is simply one fewer thing that gets
  dropped.
- **`keyword` grew a helper**, `constant`, so the direct lookup and the alias
  lookup are one expression each rather than two nested try/catches.
- **Four new tests**: both spellings on all three properties, the flex spellings
  still meaning what they did, and `left` still being refused with the reason in
  the name.
