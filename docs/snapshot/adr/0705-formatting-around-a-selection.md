# ADR-0705: Formatting around a selection

- **Status:** Accepted, as D8 of `docs/rich-text-input.md` (taken as
  recommended on 2026-11-14), and phase 4 of that plan.
- **Date:** 2026-11-14
- **Relates to:** ADR-0704 (a field takes formatting), ADR-0703 (text carries
  marks)

## Context

ADR-0704 gave `rich-input` the formatting keys. A pointer user needs a way that
is not a key, a link needs an address, a menu user expects the commands in the
text menu, and a composer sends on `Enter`. D8 asked whether the toolkit draws
a bar over a selection (a) or only publishes where the selection is and lets
the application draw one (b).

## Decision

**A stock bar, replaceable (D8 (a)).** A settled selection (a selection not
being dragged) in a focused, editable field opens a `format-bar` above its
first line: icon buttons for bold, italic, underline, strike-through, code,
spoiler, link and clear formatting, named in the toolkit's words (`Labels`),
a button lit (`.on`) when its style covers the whole selection.
`RichInput.formatting(List<FormatButton>)` gives other buttons, or none; a
`FormatButton` is an icon, a name and a `FormatCommand`, the same commands the
formatting keys give. The bar is an attached popup, which never takes the
keyboard, so a press on it leaves the selection where it was. It follows the
selection, and goes when the selection collapses, a composition starts, the
field loses the focus, or `Escape` is pressed.

**A link editor.** `Ctrl+K`, or the bar's Link, opens a `link-editor` under the
selection: an address field, Apply, and Remove link when the words link
somewhere already. With nothing selected and the caret in a link, the link is
selected first. An address with no scheme is `https`; one that is not an
address is refused and the editor stays open. Applying or removing hands the
keyboard back to the field; a field the application gave no id takes one of
its own for that.

**The text menu has a Format submenu**: every command, ticked where it is on,
with its keys. `TextMenu` takes rows of a field's own after its stock ones.

**`Enter` sends when there is somewhere to send.** `RichInput.onSubmit(…)` makes
a plain `Enter` call it with the text, and `Shift+Enter` break the line. An
`Enter` that chooses a composition's candidate is the input method's. Without
`onSubmit`, `Enter` breaks the line as before.

**The caret blinks**, at `text-area`'s half-second, solid while it moves.

## Consequences

- `ToolkitLabel` has twelve more words: the eight commands, Format, Address,
  Apply and Remove link.
- A host with no popup windows (a headless session) shows no bar and no link
  editor; `Ctrl+K` there opens nothing a user can reach.
- The bar's icons belong to the field and are closed with it.
- Custom mark kinds are toggled by code, not by the bar: `FormatCommand.Toggle`
  takes a style.
- The showcase's Forms screen has a composer card: `Enter` sends the draft into
  a `rich-text` whose runs carry the marks' classes.
- Tested by `RichInputFormattingTest` (the bar over a selection and waiting
  for a drag to be let go, a button toggling and lighting, no bar for a
  read-only, unfocused or barless field, its icons; the link editor adding,
  editing and removing a link, refusing nothing to link, addresses; `Enter`
  sending and `Shift+Enter` breaking; the Format submenu, and none when
  read-only), `RichInputGoldenTest` and `FormatBarGoldenTest` (both themes),
  `ComposerCardTest`, and `TextMenuTest` unchanged.
