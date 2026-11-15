# ADR-0706: Mentions: the field reports, the application suggests

- **Status:** Accepted, as D7 of `docs/rich-text-input.md` (taken as
  recommended on 2026-11-14), and phase 5 of that plan.
- **Date:** 2026-11-15
- **Relates to:** ADR-0703 (text carries marks), ADR-0704 (a field takes
  formatting), ADR-0705 (formatting around a selection)

## Context

ADR-0703 made a mention an atom in the model: the caret steps over it, a
deletion that touches it removes all of it, and nothing can be typed inside
it. What a composer still lacked was the way to put one in. A person types
`@`, a letter or two, and picks somebody from a list. D7 asked whether the
field only **reports** what is typed after the trigger and the application
shows its own list (a), or the toolkit draws a stock suggestion list fed by a
lookup function (b). D9, typed Markdown turned into marks, was taken as
"later", so no input rules land here.

## Decision

**The field reports and the application suggests (D7 (a)).**

**A query is found in `core`.** `MentionQuery.at(MarkedEdit, triggers)` in
`text.mark` returns the open query at the caret, if there is one. A query is
open when nothing is selected and the caret is in a word that begins with a
trigger. The trigger must be at the start of the text, after whitespace, or
after an opening bracket or quote. The word after it is letters, digits,
combining marks or `_`. So `mail@example` opens nothing, a space or a comma
closes the query, and so does a caret that leaves the word. A word under code
or inside an atom opens none: that rule keeps a mention just put in (`@Ann`)
from reading as a query for another. A query is at most 64 chars. Its
`query()` is what was typed between the trigger and the caret, and
`start()`..`end()` is the whole word, trigger included, running on past the
caret. `completion(text, kind, in)` is what replaces that word: `text` under
`kind`, and a space after it unless whitespace follows already. A trigger is
one code point that cannot be part of a word.

**The field reports it.** `RichInput.mentions(Mentions)` takes the triggers,
an `onQuery(Optional<MentionPrompt>)` listener, and a key handler.
`Mentions.on(listener, "@", "#").keys(handler)` builds one. After every
change, and when the field moves or loses the focus, the field looks for a
query and reports it when it opens, changes or moves. When it ends, the field
reports `Optional.empty()` once. A `MentionPrompt` holds the query and its
anchor (the query's first line, in window coordinates) and is the way back
into the field. `complete(text, kind)` puts the chosen text in as one undo
step. `dismiss()` closes the query. A prompt the field has moved on from
changes nothing, so a list a frame behind cannot put a name over the wrong
word. Nothing is reported while a composition is open, since the input
method's text is not the field's yet, and nothing in a read-only or disabled
field.

**Keys go to the application first.** While a query is open, a press of `Up`,
`Down`, `Enter`, `Tab` or `Escape` with no modifier but `Shift` is offered to
the handler before the field acts on it. A key the handler leaves is the
field's. An `Escape` the handler leaves closes the query, and the query stays
closed until the caret leaves its word.

**The atom is the model's.** `MarkKind.Mention` stays the atom of ADR-0703.
Stepping over it, deleting it whole, undo and redo, and a composition never
starting inside it needed no new code here; they get tests here.

**The showcase does the application's half.** In `example`
`ui.forms.mention`, `MentionPicker` hears the composer's queries, matches
`Contacts` by the start of a handle or of any word of a name, and shows a
`MentionList` in an attached popup above the query. `Up`/`Down` move the
marked row, `Enter`/`Tab` take it, and `Escape` is left to the field. The
popup never takes the keyboard.

## Consequences

- `RichInput` gains a `mentions` component, between `formatting` and
  `readOnly`. It is unreleased, so no breaking-changes note is needed.
- `Editor.rangeRects(start, end)` gives the rectangles of any range of the
  committed text. It allows for an open composition, which pushes the text
  after the caret.
- The query is not highlighted in the field. The decoration layer phase 2
  deferred, ranges with a paint kept outside the value, comes with the
  spellchecker (W3.7), and the query highlight can use it then.
- A headless host opens no popups. The showcase's keys still choose, because
  the matches live in the picker and the list only draws them.
- The field closes the query when its `mentions` are taken away. It does not
  close it when the field is disposed: whatever the application showed belongs
  to a tree that is going too, and the showcase closes its list in its own
  `dispose`.
- Tested by `MentionQueryTest` (opening at a word start, not inside a word or
  under code or after an atom, following typing, punctuation, any script,
  several triggers, a trigger outside the BMP, the length cap, the word's
  range, completion with and without a kind, no double space, the atom stepped
  over and deleted whole, undo and redo, trigger checks),
  `EditorPreeditTest` (a composition beside an atom, a range after a
  composition), `RichInputMentionTest` (reports as typed, no query inside a
  word, the anchor, focus loss, read-only, a composition, key routing, an
  `Enter` the application takes or leaves, `Escape` and dismissal, completing
  from the prompt and from a key, the atom with undo, stale prompts, removed
  mentions), `ContactTest`, `ComposerCardTest` (a contact mentioned with the
  keyboard alone and sent; `Escape` then `Enter` sends the query as typed),
  and `MentionListGoldenTest` in both themes.
