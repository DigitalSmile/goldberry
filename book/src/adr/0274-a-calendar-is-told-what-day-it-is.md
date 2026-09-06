# 274. A calendar is told what day it is

Date: 2026-09-06

## Status

Accepted. Builds `docs/core-widgets.md` §10's `calendar` and §4's `date-picker`,
and closes the `Validator` entry the TODO list said the picker would open.

## Context

Two widgets, one pair: §4's picker is "a `text-input` that parses, plus a
`popover` holding a `calendar`", and §10's calendar is a widget in its own right
with three selection models, two kinds of gate, a per-day renderer and a keyboard
of its own. Building the picker without the calendar is not possible and building
the calendar first is most of the work.

## The finding, which was an existing rule refusing to bend

The first version read the clock. `CalendarView.today` defaulted to
`LocalDate.now()` and the grid opened on `YearMonth.now()`, which is what every
calendar API does and what nobody thinks twice about.

`DeterminismTest` failed:

> Method `CalendarView.resolvedToday()` calls method `ZoneId.systemDefault()` …
> ADR-0203: a time axis is time, and which zone it is drawn in is the
> application's answer. One seam is what makes passing UTC enough to pin a
> chart's picture

The rule was written for `TimeAxis` and it is exactly as true here, for a reason
that had not been anticipated: **an instant is only a date in some zone**, and a
calendar that decided which one would be answering a question only the
application can. So:

- **[CalendarView#month] is required.** The grid is told which month to show;
  a change to it pages the grid, cross-fade and all.
- **`today` may be null, and null means no day is marked.** A calendar that has
  not been told what today is does not guess.

Neither is a workaround. Both are better API, and they buy what ADR-0203 bought:
a golden image of September 2026 is the same image tomorrow, and in Auckland.
`DatePicker` inherits both.

## The calendar

- **`DateSelection` is one value for three models**, where `list` uses a
  `Selection` enum and a separate set of rows. The split works for a list because
  its three models differ only in *how many* rows may be chosen; a calendar's
  third is not a count. A range of two dates is not two dates — everything
  between them is shaded and neither end means anything without the other — so
  the mode and the dates travel together and `contains`, `isStart`, `isEnd` and
  `covers` are four different questions.
- **A range's ends are `:checked` and its middle is not**, which is what makes
  §2's "radius `full` on the selected day, **range ends only**" one CSS rule
  rather than a rule and an exception.
- **Grid gap zero is load-bearing**, and §2 says so without saying why: a range is
  drawn by shading the days between its ends, and a gap would break that shading
  into seven stripes a week.
- **Six rows always.** A month occupies four to six weeks, and a grid that changed
  height between them would move everything under it — a popover would resize
  under the pointer, and §3.1's cross-fade would be a cross-fade between two
  shapes. The spare cells come from the months either side, drawn quieter and
  still pressable.
- **The cross-fade is two months, and one is out of flow.** A single grid dipping
  to transparent is a dissolve *to the background*, which on a popover reads as a
  blink. So the incoming month is in flow and sizes the box while the outgoing one
  is absolutely positioned over it at the complementary opacity — a `CalendarMonthLayer`
  each, so the pinning is `inset: 0 0 auto 0` on one node rather than a row height
  multiplied by an index that `render` cannot measure. It lands correctly because
  ADR-0272 made an absolute child respect its containing block's padding.
- **One Tab stop, and the cells are parts.** §10 asks for "one Tab stop with a
  roving day", and a `FocusScope` is the other way to say it and the wrong one: a
  scope roves between *focusable* children, and forty-two focusable cells is
  forty-two Tab stops from anywhere the scope does not reach. So `CalendarBox`
  takes every key and the roving day is a class.
- **The arrows clamp to `min` and `max` and not to the disabled predicate.** A
  bound is a window and a predicate is a rule inside it; a `Right` that skipped
  four days because a weekend was refused is a grid whose arrows lie. The roving
  day may therefore sit on a refused date, which draws as `:disabled` and cannot
  be chosen.
- **A month header, which §10 does not ask for.** It gives this widget only a
  keyboard for changing month, and §2's "header row `caption`" is the *weekday*
  row. A calendar a mouse cannot page is not a calendar, so `calendar-header` is
  an addition — written down here, and `docs/design-system.md` §2 gained a row for
  it in the same change rather than it being an undocumented part. Neither arrow
  is focusable, for `TabClose`'s reason.

## The picker

- **The text is the state and the date is derived.** §4: "the typed field is the
  source of truth, not the popup". Holding a `LocalDate` and rendering it into the
  field has to answer what the field says while somebody is halfway through
  typing, and every answer to that either fights the caret or eats characters.
- **The grid writes text into the field**, exactly as a user would, so a value
  takes one path and is parsed in one place.
- **One gate, two readers.** §4: "`min`, `max` and a `disabled` predicate gate
  both the field and the grid, so an unreachable date cannot be typed either."
  `allows` is that one place. A refused date is left *in the field* and not
  committed — deleting what somebody typed is how a field loses a keystroke they
  were halfway through.
- **`Alt+Down` is taken on the capture pass**, because `text-input` reads a plain
  `Down` as "go to the end of the line" and does not ask about the modifier.
  Teaching `text-input` about pickers was the alternative and is worse.
- **The only date syntax the toolkit writes is a range's separator.** §4 forbids
  inventing one and no locale service answers "how does this language join two
  dates", so a range is an en dash with spaces — and parsing accepts a plain
  hyphen too, because it is what a keyboard has. That looseness is why the
  separator cannot be a bare hyphen: `9-1-2026` is a date in some locales.
- **A document's `change` carries text and Java's carries a value.** §9's valued
  actions cross as a `String` and nothing else, so a document is handed the
  formatted date and an application in Java is handed a `DateSelection`. Found by
  the binding weaver refusing the showcase's first handler, which is the check
  doing its job.

## The `Validator` seam, closed by composition

The TODO list had said, for two milestones:

> A `Validator` is over a `String`, and `date-picker` will want otherwise. … That
> is a second seam — a field that validates a *parsed* value — rather than a
> change to this one, and it is the picker's to open.

It is `Validator.parsing(parse, message, rule)`: a rule over the parsed value,
**as a rule over the text it was parsed from**. `Field` needed no change at all —
it still holds a `Validator<String>`, `FieldState` still reads its control's
binding as text, and neither knows a date was involved. That is the evidence the
second seam was a composition rather than a type parameter, and it keeps
`Validator`'s own doctrine intact: what the user typed is text until something
parses it, and a validator is exactly the thing that decides whether it can be.

`parse` may throw or answer null and both mean the same thing, because
`java.time` throws and a hand-written parser returns null, and a seam that took
only one would make the other an application writing a try/catch to satisfy a
method.

## Consequences

- **Three architecture sweeps caught this before any test did**, and each was a
  real decision rather than a formality: `DeterminismTest` on the clock,
  `TokenClosureTest` on a `--gb-accent-on` that does not exist, and
  `SemanticsSweepTest` on two parts that *overrode* `isFocusable` to say false —
  which is what makes a type owe a role. They do not override it now, and the
  default already said what they meant.
- **`border-radius: full` is not a thing**, and the first calendar asked for it in
  two rules. §2 writes `full` and `controls.css` spells it as half the height in
  points, because §8's subset has no keyword and no `calc()`. It cost a token —
  `--gb-calendar-day-radius`, beside `--gb-calendar-day` so a density moves both —
  and it was found by looking at the image, since a dropped declaration warns and
  fails nothing.
- **`Role.GRID` is new**, and distinct from `GROUP` by the arrows: a group is a
  boundary with content in it, a grid promises all four arrows mean something and
  that a cell has a position in two axes.
- **Two widgets owe the same M5 entry.** §10 asks for "each cell's full date as
  its name" and §4 for "the formatted date as its value text", and `Semantics`
  carries a role, a name and a liveness with no channel for either. That is the
  entry `code-input` opened; it now has three widgets behind it.
- **§4 has two widgets left** — `time-picker`, which is this one with three
  columns instead of a grid, and `color-picker`. Both reuse everything here except
  the month.

## Alternatives considered

- **Reading the clock and adding a second seam beside `TimeAxis`.** Two doors is
  one more than ADR-0203 allows, and the argument for one door does not weaken
  when a second widget wants it.
- **A `FocusScope` over the cells.** Priced above: forty-two Tab stops.
- **Dipping the grid to transparent instead of cross-fading.** Cheaper, and it
  reads as the popover blinking.
- **A `Validator<Object>` on `Field`.** A breaking change to a shipped API, to
  express something composition already expresses.
- **A `time-picker` in the same change.** It shares the field, the popover, the
  gates and the revert, and shares nothing with the grid. It is a smaller piece of
  work now than it was this morning, and a separate one.
