# 305. A chip is a badge you can press

Date: 2026-09-13

## Status

Accepted. Adds `chip` to `docs/core-widgets.md` §3 and a metrics row to
`design-system.md` §3, which is the order `design-system.md` §5 requires — a spec
*and* a §3 row *and* gallery coverage before code.

## Context

`badge` has been in the catalog since [ADR-0087](0087-a-semantic-fill-brings-its-own-foreground.md),
and §3 describes it as a "count/status **chip**". `select multiple` grew a
`select-chip` part with a remove affordance
([ADR-0182](0182-a-select-may-hold-more-than-one.md)). The word was doing three
jobs and the catalog had no widget by that name.

What was actually missing is the thing every other toolkit calls a chip and
neither of those two is: **a small rounded label a user can choose and take
away.** A filter in a row of filters. A tag on a document. A token in a recipient
field. `badge` cannot be any of them — it is a leaf with text, it is not
focusable, it has no state and no keyboard — and `select-chip` is a part, which
by [ADR-0065](0065-a-part-is-styleable-and-not-constructible.md) means nobody can
build one.

The temptation was to grow `badge` a `press` and be done. That is the decision
this record exists to refuse.

## Decision

**A separate widget, and the line between the two is what it reports.**

> A **badge** answers *what is true* — three unread, one build failing. A **chip**
> answers *what you picked*.

Everything else follows from that one sentence:

- A chip is **focusable**, carries `:checked`, and has a keyboard. A badge has
  none of the three, and adding them would make every status badge in every
  application a Tab stop.
- A chip takes `press` and `dismiss`. A badge takes neither.
- A chip **selects nothing itself**: `press` raises and the application decides,
  which is [ADR-0063](0063-data-flows-down-events-flow-up.md)'s loop and the
  same one `radio-group` and `tabs` run. `bind=` is the other half, so a row of
  filters is writable as a document.

### Where they share, and where they part

They share the **semantic hue tokens** — `--gb-badge-danger-bg` and its four
siblings — because §1.2's "aurora hues only with semantic meaning" is one claim
and two sets of five that have to be kept agreeing is how they stop agreeing.

They part on the **rest fill**, which is the one place the analogy fails. A badge
takes `--gb-badge-bg` (`--gb-surface-2`), which is right for a plate you read
*beside* something. A chip takes `--gb-button-bg`, because it is a control at rest
and has to be a findable target.

Metrics: height **24** (a badge's 20 is a plate, and §2.2's 24 is a target),
`body` rather than `caption` (a filter you choose is content, not metadata),
radius `full`, `flex-shrink: 0` — a chip that gave width back would ellipse the
word a user is choosing between, and the answer to a narrow row is a wrapped one
([ADR-0192](0192-a-row-of-chips-wraps-and-the-chevron-does-not.md)).

### The dot and the icon are one slot

§3's row says "an optional leading **dot or icon**", and the constructor refuses
both. They are the same status at two resolutions — a dot is a status nobody has
to recognise, an icon is one they do — so asking for each is a question with no
answer, and a refusal beats a drawing decision nobody can predict.

### A dot takes the foreground its own fill guarantees contrast against

One rule, and it is here because the obvious alternative is wrong in a way only
an image shows. `chip.success chip-dot { background: var(--gb-success) }` reads
correctly and puts a green dot on a green plate: the first golden of it came out
with **no dot at all**. So a filled variant's dot is that variant's *text* token,
and the `.outlined` forms — which have no fill — take the hue.

The useful pairing is the second: a quiet pill with a live status on it, which is
what a dot is for.

### Keyboard

`Space`/`Enter` press. `Delete` **and** `Backspace` dismiss — both, unlike
`tab`'s one. A tab lives in a strip a user walks with the arrows, where `Delete`
is the forward-facing key; a chip is commonly the last thing before a text field,
where `Backspace` is what a hand reaches for. Binding one and not the other would
make it a coin toss.

## Consequences

**The × is not a Tab stop**, which is `tab-close`'s and `select-chip-remove`'s
rule: a row of five filters is five stops, not ten. That is only defensible
because the chip itself answers `Delete`, and it is why both delete keys are
bound rather than one.

**A read-only chip is not focusable either.** A chip with no `press` and no
`dismiss` is a `badge` with a different type name, and the check that says so is
the one place the two widgets really are the same thing.

**`chip` shares `badge`'s hue tokens, so a theme that re-tints one re-tints the
other.** That is the intent and it is also a constraint: a theme wanting a
different chip palette has to add tokens rather than change these.

**A default chip is flush with a `card` on the Nord dark theme**, because
`--gb-button-bg` and a card's own fill are both `--nord2`. That is a property of
the palette rather than of this widget — the default `button` and the bare
`badge` have it too — and the showcase's filter row uses `.outlined` and says so
rather than papering over it here. A chip set that needs to read on a card asks
for `.outlined`, and `chip.outlined:checked` exists so that such a set can still
show which one is on.

## Alternatives considered

**Give `badge` a `press` and a `selected`.** Rejected, and it was the cheap one.
Every status badge in every application would become a Tab stop the moment the
capability existed, because "it has a handler" is not something a stylesheet or a
reviewer can see. Two names for two behaviours is the whole of the fix.

**Make `chip` a `button` variant — `button.chip`.** Rejected: a button *does*
something and reports nothing about itself afterwards; a chip is a thing that is
on or off and whose whole point is which. They would also have disagreed about
`:checked`, which a button has no meaning for.

**Let `selected` be supplied by a parent, as a `tab`'s is.** Rejected, and it is
the one place this widget deliberately differs from `tabs`: a strip exists to hold
the invariant that exactly one is chosen, and a row of filter chips has none, two
or all of them on. There is no parent to hold an invariant that does not exist, so
`selected` is an ordinary attribute and `bind=` drives it.

**A `chip-group` to own the selection.** Rejected for the same reason and one
more: the useful chip rows in real applications are not groups — a tag row is a
list that shortens, a filter row is several independent booleans, and a recipient
row is a text field with tokens in it. A group would fit none of the three.
