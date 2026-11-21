# ADR-0708: A showcase screen for `rich-input`

- **Status:** Accepted, as phase 7 of `docs/rich-text-input.md`, widened by
  the owner on 2026-11-15 ("create in the showcase all possible options and
  examples").
- **Date:** 2026-11-15
- **Relates to:** ADR-0624 (the showcase's sidebar of sections), ADR-0704 (a
  field takes formatting), ADR-0705 (the formatting bar), ADR-0706 (mentions),
  ADR-0707 (Markdown and HTML)

## Context

The showcase has a screen per chapter of the guide and a card per section.
`rich-input` is one section of the Fields and forms chapter, and phases 4 to 6
put one card for it on the Forms screen: the composer. The owner asked for
every option and an example of each. That is eleven cards: the composer, every
kind of mark, rows, read-only and disabled, three bars, four triggers, the
stylesheet, the clipboard, the conversions, the markup, and the canvas editor.
On the Forms screen they would outnumber the fields the chapter is about.

Phase 7 also asked for the composer's sent messages to sit in a message list
built on `list` sections (W2.5), which needs a scrolling well of its own.

## Decision

**`rich-input` gets a screen of its own, Rich input**, under Controls and right
after Forms. The code is `example` `ui.richinput`. Its header links the Fields
and forms chapter. Each card links a section the book has today, because the
guide's own `rich-input` section is parked until release. One card,
`rich-input.kdl`, is markup bound to `app.rich-note` on `FormsModel`. The rest
are Java, because a suggestion list, a send handler and a clipboard format are
code.

**The composer moves there** from the Forms screen. Its conversation is a
`list` in day sections inside an end-anchored `scroll`, and each message is a
`rich-text` drawn from its marks by `MarkedRuns`. Links, mentions and the
custom kinds can be pressed, and spoilers are covered.

**The mention picker is generalised.** `ui.forms.mention` became
`ui.richinput.suggest`. A `Suggestion` is a row that knows the text and kind
it completes with. A `Suggester` answers one trigger. `SuggestionPicker` takes
a map of them. One field then shows all three ways a completion goes in: as an
atom (`@` a person, `/` a command), as a reference of the application's own
(`#` a topic), and as plain text (`:` an emoji).

**The showcase writes its own clipboard format.** `MarkdownFormat` offers
marked text as `text/markdown` through `MarkedMarkdown`. It shows that an
application can install a `MarkedFormat` of its own, beside the HTML one the
toolkit ships.

## Consequences

- One chapter now has two screens. The rule "a screen per chapter" gets one
  exception, for a section with more options than a card can show.
  `GalleryDocsTest` still holds every card to a link that resolves.
- `Ctrl+0` opens Rich input, the tenth screen, and Panels no longer has a
  digit. It is still reached from the sidebar, search, and Edit ▸ Go to.
  `ShowcaseShellTest` pins the new order.
- The Forms screen's count drops from 14 to 13 and the sidebar gains a row.
  So every gallery and shell golden, both tour goldens, 18 of the book's
  screen pictures and the search index were taken again.
- The composer's and the suggestion list's rules moved from
  `chapter-forms.css` to a new `chapter-richinput.css`. The list's classes are
  `suggestion-*` now, with the same looks, so `mention-list-{dark,light}` did
  not change.
- The bundled faces have no Arabic or CJK and no fallback cascade, so the
  "every kind" sample uses Cyrillic and Greek beside an emoji.
- Tested by a test per card (`KindsCardTest`, `RowsCardTest`,
  `StatesCardTest`, `BarCardTest`, `TriggersCardTest`, `StylesCardTest`,
  `ClipboardCardTest`, `ConvertCardTest`), `ComposerCardTest` (day sections,
  pressable mentions, spoilers, today's section, emoji from the keyboard),
  `RichInputChapterTest` (placed after Forms, every card, the markup card
  following the model), `SuggestionTest`, `SuggestionPickerTest`,
  `MarkedWriterTest`, `MarkedRunsTest`, `EntitiesTest`, `MarkdownFormatTest`,
  and `SuggestionListGoldenTest`.
