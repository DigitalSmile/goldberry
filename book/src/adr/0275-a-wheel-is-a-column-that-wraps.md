# 275. A wheel is a column that wraps

Date: 2026-09-06

## Status

Accepted. Builds `docs/core-widgets.md` §4's `time-picker`, and fixes the
`date-picker` popover width reported against ADR-0274.

## Context

§4 writes `date-picker` and `time-picker` in one entry, and they differ in one
thing: what is inside the popover. A date's is §10's `calendar`; a time's is "an
hour/minute/second column set", which §10 does not specify and §2 does not give
metrics for — its row is `field = text-input; popup radius 12, padding 8; day
cell 32 square`, and every number on it is the date half.

## The width bug, and why it was the wrong rule borrowed

`date-picker` opened its calendar with the **field's width** as the popup's
minimum. That is `select`'s rule and ADR-0145's argument — "a list narrower than
the control it hangs off reads as a mistake" — and it is wrong here for a reason
the argument does not survive: **a list's rows stretch and a grid's cells do
not.** A month is seven cells of `--gb-calendar-day` and can be no other width,
so a floor produces a panel as wide as the field with the grid stranded at one
end of it. On the showcase's Forms screen the field is 350 points wide and the
grid is 224, which is what "the popover width is full" looks like.

Both pickers now ask for **no minimum**, and both say so with a named constant
rather than a bare zero, because the interesting thing about the number is that it
is a different answer from `select`'s to the same question.

## The wheels

**A wheel and not a scrolling list.** Sixty minutes in a viewport is the other
answer and it costs a `scroll` per column, a `ScrollController` per column and a
`Located` cell to reveal — a popover whose height depends on how much of a list
it decided to show, and a first frame that has to scroll before it is right.
Instead: **five rows centred on the value, wrapping at both ends**, which is what
every platform's own time picker does. The popover is one height always; there is
nothing to scroll into view because the value is already in the middle; and
`23 → 00` is one press rather than a journey back up sixty rows.

The wrap is what makes the quiet neighbours honest. A column showing
`58 59 00 01 02` is telling the truth about what comes next, where a list clamped
at `59` would stop.

**The arrows split by axis, and that is the difference from a calendar.** A
calendar is a grid and all four arrows move a *cell*. A column set is a row of
independent wheels, so `Up`/`Down` change a value and `Left`/`Right` change which
column — which is why §4 could give the two pickers the same sentence ("arrows
move within the grid") and mean different things by it. `Home`/`End` are the ends
of the **column**, because a row of three is two presses wide already.

**The wheels report on every turn**, where a calendar's roving day reports
nothing until `Enter`. There is no "not yet" state for an hour: the columns always
show *some* time, and a user turning one is changing the value they can see. It is
also what keeps the field in step, since §4 puts the field in charge and a field
that only caught up on `Enter` would show a stale time beside a wheel showing the
real one. `Enter` therefore commits what is already committed, which is not a
no-op — it is what closes the popover, and a control with no keyboard way to say
"done" is one a keyboard cannot finish with.

**`selected` is a class and not `:checked`.** A chosen date is one of a *set* a
user picked from, which is what `:checked` says everywhere else in this catalog.
An hour is one *digit of one value*: a column always has exactly one, nobody chose
it, and it changes when its neighbour does not.

## Three pickers, one control

`PickerField`, `PickerToggle` and `PickerPanel` moved into `…form.parts` — the
package that already exists for a part more than one field-shaped widget needs,
and which is public-within-module and **not exported**, so the only callers are
the packages in this module that build one.

`PickerField` answers `cssType()` with **what it was given**, which is the one
unusual thing here: everything else in the catalog answers with a literal. The
three pickers are one kind of thing that a *stylesheet* has to tell apart — §2
gives `date-picker`/`time-picker` one row and `color-picker` another — so a shared
`picker` type would make those rows unwriteable. It is not a hole in ADR-0065's
rule: a part is styleable and not constructible, and this is neither a part nor
constructible by an application.

## What a sweep caught

`WidgetWitherTest` failed on `TimePicker.precision(its own value)`. The wither was
rebuilding the **format** as well, so that a picker growing a seconds column got a
field that could show one — and two `TimeFormat`s built the same way are unequal,
because a `DateTimeFormatter` has no value equality.

The fix is better than the thing it replaced: `format` is **null until somebody
sets it**, and `resolvedFormat()` derives the locale's default for the current
precision. The format still follows the precision, and it follows because nobody
pinned it rather than because a wither reached over and rewrote it. `format(null)`
puts it back, which is what lets that wither take its own value too.

## Consequences

- **`--gb-time-cell-height` is `--gb-list-row-height`** and the column width is
  its own. Two digits in a 32-tall box would be square, and three columns of
  squares reads as a calculator. `design-system.md` §2 gained a row, as
  `calendar-header` did, rather than the metrics being undocumented.
- **`min`/`max` do not wrap, and the constructor refuses a pair that would.** A
  shift from 22:00 to 06:00 is two ranges, and a picker that let a bound wrap
  would have no way to say which of the two a time at 03:00 was in. An application
  with a night shift supplies a predicate, which can say it.
- **The default format differs by precision**, which is the one place `TimeFormat`
  writes a pattern: `FormatStyle.SHORT` on a time is never seconds, so a picker
  with a seconds column would have a field that cannot show one, and no
  `FormatStyle` produces a time with seconds and without a zone.
- **`value(String)` was missing from both pickers** and is there now. The *text*,
  not a typed value, because that is what these controls hold.
- **§4 has one widget left**, `color-picker`, and it is the third user of
  `PickerField`.

## Alternatives considered

- **Scrolling columns.** Priced above.
- **Keeping the field-width floor and centring the grid in it.** A popover twice
  as wide as its content, with the anchor edge meaning nothing.
- **`TimePrecision.columns()` as `ordinal() + 1`.** Error Prone refuses it and is
  right: an ordinal is a declaration order, and a constant reordered here would
  silently change how many wheels a picker draws.
- **A separate `TimePickerBox`.** A hundred and twenty lines that would have to
  stay identical to `date-picker`'s by hand, for one string.
