# 215. A property the engine drops is a rule that does nothing

Date: 2026-08-29

## Status

Accepted. Fixes a rule shipped in ADR-0214 and closes a `TODO.md` entry open
since ADR-0109.

## Context

`table-head` shipped with `border-bottom: 1px solid var(--gb-border)`, which
§8's subset does not have: there is one `border` and no per-edge longhands. The
engine did exactly what it promises — logged at debug and carried on — so the
line §3's metrics row asks for under a table header was never drawn, and the
golden image was accepted with it missing.

**This is the fourth time.** `TODO.md` has recorded it since ADR-0109: "`margin`
is not in §8's subset, which `tab-new` found after `border-bottom` and
`currentColor`. Three properties a widget reached for and did not find, all
silently ignored — the subset is right to be small, and nothing warns when a
declaration is dropped." `menubar` documents the same wall in a comment in the
stylesheet itself.

The engine's behaviour is right and is not what needs changing. A stylesheet
naming `box-shadow` before it is implemented should not stop a window opening,
because that stylesheet may be an *application's*. What was missing is that the
toolkit's own sheets were being held to the same lenient standard as a stranger's.

## Decision

**The rule is a node.** `table-rule` is a box one pixel tall with a background,
which is `separator`'s answer to the same problem and the only one the subset
allows. Not a new `border-bottom` property: per-edge borders are a change to the
box model and to Yoga's edge handling, and one table's underline is not the case
that should decide it.

**The toolkit's own stylesheets are linted, and an application's are not.**
`SupportedPropertyTest` resolves every rule the catalog and the showcase ship
through the **real** `ComputedStyle` and fails on anything it reports as
unsupported. The asymmetry is the point: leniency is for stylesheets the toolkit
did not write.

**It asserts the behaviour rather than a copy of it.** There is no list of
supported properties in the test to drift out of step with the engine — it
attaches an appender and reads what the cascade actually said. A property added
to the engine tomorrow needs no edit here; one removed is caught the same day.

**It lives in `:example`.** That module already ships logback, so capturing the
cascade's own output costs no new dependency on `:widgets`; and §14 makes the
gallery the visual regression corpus, which is where a declaration that draws
nothing is a screen that has been photographed wrong.

**Custom properties are excluded.** `--gb-accent: …` reaches the same branch and
is logged the same way, and it is not a fault: custom properties are the
resolver's, computed for `var()` substitution before `ComputedStyle` sees a
declaration (ADR-0049). Every theme is nothing but those, so the unfiltered check
reported 158 failures on a healthy tree — which is how the filter came to exist
rather than being foreseen.

**The check checks itself.** A third test feeds it `border-bottom` and asserts it
is caught, because a change to the log's wording or to the appender wiring would
otherwise make the other two pass by seeing nothing at all.

## Alternatives considered

- **Adding `border-bottom` to the subset.** It is the fourth request, which is an
  argument for it — and it is a change to the box model rather than to a parser:
  Yoga takes per-edge border widths, but the painter draws one stroked rounded
  rectangle (ADR-0064), so a single-edge border is a different drawing and not a
  different number. Worth doing when something needs an edge the subset cannot
  fake; a 1px node is not a workaround here so much as what a rule *is*.
- **Raising the log from debug to warn.** It is still a log, and the entry this
  closes says why that is not enough: one line per property per stylesheet is a
  message, but it arrives at start-up on a stream nobody is reading, and the
  three previous instances all had it.
- **A hand-written set of supported properties**, checked against the sheets.
  Cheaper to write and the failure mode is drift — a property added to the engine
  and not to the list makes the test fail on a healthy tree, and the reflex fix is
  to edit the list rather than to question it.
- **Failing the *engine* on an unsupported property in a toolkit sheet**, by
  marking sheets as trusted. It puts a test's concern in the runtime, and it would
  turn a cosmetic mistake into a window that does not open.

## Consequences

- **One more test guards a whole class of mistake**, and it found nothing else:
  `border-bottom` in `table-head` and `padding-bottom` in the showcase's `#peaks`
  were the only two live instances in the tree.
- **`:example` has logback on its test classpath**, where it previously had it
  only at runtime.
- **The four table goldens changed**, because the rule is now drawn. The
  difference between the accepted image and the corrected one is a single line of
  pixels — which is how a missing rule survives review, and an argument for the
  check rather than against the golden.
- **An application still gets silence.** The `TODO.md` entry is narrowed rather
  than closed: what warns is a test over the toolkit's sheets, and an author
  writing `margin` in their own stylesheet still gets a debug line and a
  declaration that does nothing.
