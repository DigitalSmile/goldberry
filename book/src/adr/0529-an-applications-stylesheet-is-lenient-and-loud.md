# ADR-0529: An application's stylesheet is lenient and loud

- **Status:** Accepted. Amends [ADR-0049](0049-the-css-engine-stops-at-computedstyle.md)
  ("the subset is enforced, not approximated") for an application's sheets,
  and [ADR-0257](0257-a-diagnostic-is-asked-for-not-logged.md) and
  [ADR-0216](0216-a-corner-is-four-numbers-and-a-lint-reads-values-too.md)
  for the unknown property and the lint nobody ran.
- **Date:** 2026-10-02
- **Relates to:** [ADR-0215](0215-a-property-the-engine-drops-is-a-rule-that-does-nothing.md),
  [ADR-0243](0243-a-missing-token-is-a-message-not-a-stream.md),
  [ADR-0526](0526-a-media-query-is-asked-of-the-window.md),
  [ADR-0528](0528-a-node-knows-its-place-among-its-siblings.md),
  `docs/goldberry-gaps.md` #4 and #17

## Context

Deploy Orc, an application on Goldberry, did not start. Its `orc.css` said
`.acct-step > :last-child { … }`, `CssParser.compound` threw
`CssSyntaxException: unknown pseudo-class ":last-child"` inside
`Stylesheet.resource`, which ran in a static initializer, and the JVM reported
`ExceptionInInitializerError`.

ADR-0049 made that throw on purpose: "a toolkit reads a stylesheet its own
application shipped, and there a dropped rule is a widget that is the wrong
colour with nothing in the log". The same report found the engine already had
three answers to one question, "this sheet asks for something the subset
lacks":

- a selector feature refused the **whole sheet**, with a line and column;
- a known property with a bad value dropped the **declaration**, with one
  warning (ADR-0215, ADR-0243);
- an unknown property was dropped at **debug**, which nobody reads.

ADR-0257 then made the lint a value an application asks for, and nothing asked:
the report found `StyleLint` and noted that nothing runs it.

ADR-0049's argument is right about the toolkit's own sheets. A rule in
`controls.css` that matched nothing is a control drawn wrong in every
application, and the build is the place to find out. It is wrong about an
application's sheet, which is very often written against a browser prototype
first. There, refusing the sheet turns one missing feature into an
application that does not start, which is worse than any rule being missing.

## Decision

**A sheet is parsed strict or lenient, by whose sheet it is. Lenient drops the
rule and says so once; strict refuses the sheet.**

- `ParseMode.STRICT` and `ParseMode.LENIENT`, in `css.parse`. `CssParser.parse`
  and `parseSheet(css)` stay strict, so the parser's own tests and anything
  that calls it directly keep ADR-0049's behaviour.
- `Stylesheet.parse` and `Stylesheet.resource` choose by layer when nobody
  says: `TOOLKIT_BASE` is strict, every other layer lenient. The toolkit's
  theme-layer sheets (the two Nord themes, compact density, the scroll-bar
  gutter) and `controls.css` ask for strict by name, since the theme layer is
  also where an application's own theme goes.
- What lenient forgives is **a construct outside the subset**: an unknown
  pseudo-class, `::before`/`::after`, `[attr]`, `+` and `~`, `:has()` and the
  other functional pseudo-classes, an at-rule other than `@media`,
  `@starting-style` and `@keyframes`, and `@keyframes` inside `@media`. The
  rule asking for it is skipped through the end of its block, one warning names
  the selector as written, the sheet and the line, and a `DroppedRule` is kept
  on `Stylesheet.dropped()`. `CssSyntaxException.isUnsupportedFeature()` is the
  line between the two kinds of error.
- What neither mode forgives is **a malformed sheet**: an unclosed block, a
  declaration with no value, a numeric id. Those are mistakes rather than
  ambitions, the recovery point after one is a guess, and hot reload depends on
  a half-saved file being refused so it can keep the last good sheet.
- **An unknown property warns once per property per sheet** when the sheet is
  read, naming the sheet, the first line and the number of declarations. The
  per-node `default` arm in `ComputedStyle` stays at debug, because there it
  runs per node per restyle. `ComputedStyle.isProperty(name)` answers the
  question by running the engine's own switch with no value, which is
  ADR-0257's argument for `applies`: no second list to drift.
- **`StyleLint` reports dropped rules** as `Finding.Kind.DROPPED_RULE`, and
  `SupportedPropertyTest` holds the showcase's sheet to none.
- **`-Dgoldberry.css.lint=true` runs the lint.** Goldberry had no development
  mode to hang it on, so this is an opt-in property, read when the launcher
  builds its renderer: it lints the `APPLICATION` sheets against everything in
  force and logs each finding at warn, each sheet once. Off by default, so a
  release build pays one property read.
- Two properties from the same report join the subset: the `flex` shorthand,
  with CSS's expansions (`flex: 1` is `1 1 0%`, `none` is `0 0 auto`), and
  `row-gap` and `column-gap`, with `gap` taking one or two lengths. `Box` and
  `ComputedStyle` carry the two gutters separately, and Yoga is given each.

## Consequences

- Deploy Orc starts with its sheet as written. `:last-child` is in the subset
  now (ADR-0528); the `::before` and `:has(input:checked)` rules it worked
  around are dropped with a line each instead of stopping the application.
- ADR-0049's "`:hovered` should not be a rule that silently never matches"
  still holds in both modes: strict refuses it, and lenient says so at warn
  with the line. What changed is that the application starts.
- The toolkit's sheets are exactly as strict as before, and a new toolkit sheet
  in `TOOLKIT_BASE` is strict without saying so. A toolkit sheet in `THEME` must
  ask, which the four that exist do.
- An application that wants ADR-0049's behaviour for its own sheet passes
  `ParseMode.STRICT`, and a test that wants it calls `CssParser.parse`.
- `display`, `letter-spacing`, `filter`, `backdrop-filter`, `z-index` and
  `visibility` from the same list are still not in the subset. They are now
  reported once each instead of never.
