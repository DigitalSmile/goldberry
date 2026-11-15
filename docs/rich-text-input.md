# Rich-text input — implementation plan

Written 2026-11-14, before any code. This is W3.1 of
[`gaps/goldberry-gaps-plan.md`](gaps/goldberry-gaps-plan.md), Tessera's T-02:
"inline spans (bold, italic, code, mention, link) in an editable, growing
field, plus a formatting popover on selection". The owner decided on
2026-11-08 that the editor is Goldberry's (D12 there), and that this document
comes before any code. It follows `gpu-gaming-plan.md`'s shape: what was
found, the decisions with a status, phases with exit criteria, risks, and a
log.

Each decision becomes an ADR when it is taken, parked in `docs/snapshot/adr/`.
**The owner took every recommendation on 2026-11-14** ("go for it"). D1, D2,
D4, D5 and D6 are recorded in ADR-0703 and D3 in ADR-0704; D7, D8 and D9 are
recorded with the phases that build them. **Phases 1 to 6 are done** (§5);
D8 is ADR-0705, D7 is ADR-0706, and phase 6's conversions are ADR-0707. Open:
phase 7. The next free ADR number is 0708.

## 1. Where things stand

Read from the tree on 2026-11-14 (master at `6741de31`).

| Area | State |
|------|-------|
| The edit value | `TextEdit(text, anchor, caret)` in `core` `text.edit`: an immutable record, every operation returns a new one, steps are graphemes (`BreakIterator`), offsets from outside are snapped. A copy of the string per keystroke |
| Undo | `EditHistory` keeps whole `TextEdit` states, not inverse operations. A typing run folds into one step (`Kind.TYPING`, `DELETING`, `OTHER`); `endRun()` on focus loss; depth 200. Typed on `TextEdit`, so it cannot hold anything else |
| Shaping | `TextDocument` holds one `Paragraph` per hard line and re-shapes only the lines an edit touched (a common prefix and suffix, widened to whole lines). `TextDocument.Shaper` is `Paragraph shape(String line)`: **it sees a line's text and nothing else**, so two lines with the same words and different styles would share a shaping, and a style change on an unchanged line re-shapes nothing |
| Mixed faces | `Paragraph.join(List<Paragraph>)` puts paragraphs shaped in different fonts end to end without shaping them again; line breaking, bidi, caret and hit testing run over the joined text. `ParagraphCache` keys on `(font, text)`, so a run that did not change keeps its shaping. `Paints.Context.join` keeps a join while its spans are the same instances |
| Paint-only styling | `SpanPaint(start, end, argb, decorations, background)`: colour, underline and strike-through, and a fill behind a range, with no re-shaping. `TextDecoration` has no wavy style yet (`text-decoration-style` is W3.7's first step) |
| Inline objects | **None.** A `Paragraph` holds glyphs only; there is no placeholder for a box inside a line. A mention has to be text the caret treats as one unit, not an embedded widget |
| The canvas editor | `Editor` (`core` `text.edit`): a `TextEdit`, an `EditHistory`, a `TextDocument`, the IME composition (`preedit`, its caret and clause, spliced into `displayText()` and never into the value), scrolling inside a viewport, the primary selection, `caretLine()`/`selectionRects()`/`composingRects()`, and `paint`. Used by no widget, only by the showcase's `StickyCard` |
| The controls | `text-input` (`TextInputState`, `TextEditor`) and `text-area` (`TextAreaState`, `TextAreaBox`, `AreaEditor`) hold a `TextEdit` themselves; they do not use `Editor`. `text-area` paints one paragraph of the rows in view, sliced from the text between two line starts, and keeps its geometry in a `TextDocument`. It grows from `rows` to `maxRows` and then scrolls |
| Keys | `EditKeys.of(event, EditSurface)` turns a key into a sealed `EditCommand` (`Move`, `MoveLine`, `Delete`, `Type`, `Simple`). The three editors switch over it exhaustively, so a new command fails to compile in each until it is answered |
| IME | `PreeditEvent` (text, caret, selected clause), `BackendWindow.textInputArea` for the candidate window. A composition is drawn beside the value, underlined, and becomes an edit only when a `TextEvent` commits it |
| Clipboard | `Clipboard.text(...)` copies text; `write(Map<String, byte[]>)` offers bytes under MIME types and `read(type)` reads them. Nothing writes a styled-text type today |
| Menus and popovers | `TextMenu` (cut, copy, paste, select all, labels from `Labels`), `Menus.show(host, anchor, menu)`, `Popover` in `widgets` `overlay.popover`, `Host.popup` |
| Rich display | `rich-text` with `run` children (W2.6, ADR-0697): runs styled by the cascade (`rich-text > run.keyword`), joined into one paragraph, with `Run.onPress`, `Run.spoiler`, selection and copy. Read-only |
| Documents | `:html` has a Markdown model (`markdown.model`: `Strong`, `Emphasis`, `Code`, `Struck`, `Underlined`, `Link`, …), an HTML parser and `content.select`. `:html` requires `:widgets`, so `:widgets` cannot use any of it |

What Tessera stores is **entities**: a plain string and a list of
`(offset, length, type)` in UTF-16 code units, Telegram's shape. Java's char
offsets are UTF-16 code units, so a model of ranges over a `String` maps onto
them one to one.

## 2. What this plan delivers

1. **A styled-text value** in `core`: a string and its marks, immutable, with
   the rules for how marks move when the text is edited, tested without a
   window or a font.
2. **The canvas `Editor` with marks.** An editor with no marks is the plain
   one it is today. A sticky note on a board gets bold text through the same
   class.
3. **A `rich-input` control** in `widgets`: a growing, scrolling, focusable
   field over that editor, with the marks styled by the stylesheet, toggle
   keys, undo that keeps formatting, IME composition, and a clipboard that
   carries the formatting between rich inputs.
4. **A formatting bar** that opens over a settled selection, and a link
   editor.
5. **Mentions**: the field reports a query typed after a trigger (`@`, `#`,
   `/`), the application shows its own suggestions, and the chosen one goes in
   as an atom the caret cannot enter and Backspace removes whole.
6. **Conversions** in `:html`: marks to and from Markdown, and HTML on paste.
7. **A showcase composer** and a guide section, parked.

**Not in this plan**, so it is not rediscovered:

- block structure: lists, quotes, headings, tables, nested blocks. A message
  composer needs none, and a tree model would be the price (D1);
- inline images and widgets inside a line. `Paragraph` has no placeholders,
  and an atom is text;
- collaborative editing (operational transforms, CRDT positions). Tessera
  keeps its CRDTs (Automerge) on its own side, for the Board;
- markdown typed as markdown and turned into marks as it is typed (input
  rules). D9 asks whether to add it later;
- spellcheck. T-25 (W3.7) adds a decoration layer beside the marks (ranges
  with a paint, outside the value and the history) with its wavy underline,
  and is planned there.

## 3. Decisions

All **taken as recommended** on 2026-11-14. The text below is kept as it was
proposed; where the build departs from it, §5 and the ADRs say so.

### D1. Flat marks over a string, not a tree (taken: ADR-0703)

(a) **Marks**: the value is a `String` and a sorted list of
`Mark(start, end, kind)`. (b) A document tree (ProseMirror's or Lexical's
shape): blocks of inline nodes, each node a run of text carrying its marks.

(a) is what everything below it already is. `TextEdit`, `TextDocument`,
`EditKeys`, `BreakIterator`, the clipboard and `Paragraph` all speak offsets
into one string, and so does Tessera's wire format. Marks change nothing
about where the caret may sit, how a word is found, how bidi runs, or how
undo stores a state. (b) buys nesting (a list in a quote) that a composer does
not need, and it costs a second position model that would have to map onto
every one of those.

Two marks of different kinds may overlap (bold over half of a link). Two of
the same kind and value that touch or overlap merge into one. A kind may
exclude others: code excludes everything but spoiler, as Telegram's `code`
does, and a mention excludes a link. The list is kept in that normal form
after every edit, so equal values are equal records.

### D2. The kinds: a fixed set, plus the application's own (taken: ADR-0703)

(a) A sealed `MarkKind` with `BOLD`, `ITALIC`, `UNDERLINE`, `STRIKE`, `CODE`,
`SPOILER`, `Link(URI)`, `Mention(String id)` and
`Custom(String name, String value, Behaviour)` for the rest (a hashtag, a
custom emoji, a command). (b) The fixed set only. (c) Names only: every mark
is a string the stylesheet styles, and the editor knows no kind.

Each kind carries its **behaviour**, which is what an editor needs to know and
a stylesheet cannot say:

| | Grows when typed at its end | Atomic | Excludes |
|---|---|---|---|
| bold, italic, underline, strike | yes | no | — |
| code | yes | no | all but spoiler |
| spoiler | yes | no | — |
| link | no | no | mention |
| mention | no | **yes** | everything |
| custom | as given | as given | as given |

"Grows at its end" is ProseMirror's `inclusive`: type after a bold word and
the new letters are bold; type after a link and they are not.

### D3. Marks get their looks from the stylesheet (taken: ADR-0704)

(a) **The cascade.** The `rich-input` box has one carrier child per distinct
set of marks present in its text: `rich-input > mark.bold.italic`, with the
classes named after the kinds (`bold`, `italic`, `code`, `link`, `mention`,
`spoiler`, a custom kind's name). The cascade resolves each, so a stylesheet
writes `rich-input mark.code { font-family: monospace; background: … }`
and a combination is styled the way CSS styles any element with two classes.
A field holds a handful of sets, not one per mark. `text-area`'s `text-value`
carrier is the model: a node that is never drawn, there to be styled. (b) A
Java table on the widget (`MarkStyle` per kind), merged in code for
combinations.

(a) is how `rich-text`'s runs are styled, so a message and the composer it was
typed in read one stylesheet. What a mark's style may change is `rich-text`'s
list: the font (`font-*`, `font-feature-settings`), `color`,
`text-decoration`, `background`. A font change re-shapes the runs it covers; a
colour, a rule or a background is a `SpanPaint` and re-shapes nothing.

### D4. `Editor` gains marks; `rich-input` is a control over it (taken: ADR-0703, ADR-0704)

(a) `Editor` holds a styled value instead of a `TextEdit`. With no marks it is
the plain editor it is today, and its public API keeps every method. The
`rich-input` control is to `Editor` what `text-area` is to its state: a box,
a focus ring, a placeholder, keys from the element tree. (b) A separate
`RichEditor` beside `Editor`. (c) More arguments on `text-area`, which the
gaps plan already ruled out (W3.1).

(a) gives the canvas editor rich text for free, and keeps the IME, scrolling,
primary selection and geometry in one place rather than two. `text-input` and
`text-area` stay as they are: they do not use `Editor` today, and moving them
onto it is not this plan's work.

### D5. Undo keeps formatting by keeping states (taken: ADR-0703)

(a) `EditHistory` becomes `EditHistory<S>`, over whatever state its editor
holds: `TextEdit` for the controls, the styled edit for `Editor`. The folding
rule is unchanged and gains one kind, `FORMATTING`, which never folds: a
toggle is its own step. (b) The styled edit everywhere, so every history holds
marks, most of them empty. (c) A second history class for marks.

(a) is a source-compatible change for anyone who wrote `EditHistory` as a
field type (a raw type, with a warning), and a breaking one for nobody who
called it. (b) touches every control for no gain. (c) is two copies of the
folding rule.

### D6. The clipboard carries marks in a type of the toolkit's own (taken: ADR-0703)

(a) Copy offers `text/plain` and `application/x-goldberry-marked`: the text
and the marks in a small line-based encoding `core` writes and reads without
a JSON library. Paste prefers the toolkit's type, then `text/plain`. HTML in
both directions goes through a converter the application installs from
`:html` (`RichInput.clipboard(MarkedClipboard)`), because `:widgets` cannot
see `:html`. (b) The same, with `text/html` written and read in `:widgets` by a
small writer of its own. (c) Plain text only.

A paste from a browser or a word processor arrives as `text/html`. Under (a)
the application that wants it formatted installs the converter. Under (b) the
toolkit has a second, smaller HTML reader beside `:html`'s.

### D7. Mentions: the field reports, the application suggests (taken: ADR-0706)

(a) The field reports a **query**: a trigger character the application chose
(`@`, `#`, `/`) at a word start, followed by the word the caret is in. It
reports it with its range and the rectangle under it
(`onQuery(MentionQuery)`), and again as it changes or ends. The application
shows its own list, in a `Popover` or anything else. While a query is open,
`Up`, `Down`, `Enter`, `Tab` and `Escape` go to the application's handler
first. `replaceQuery(text, kind)` puts the chosen mention in as an atom.
(b) A stock suggestion list fed by a `Function<String, List<Suggestion>>`.

A mention's look, its avatar and whether a contact list is searched on the UI
thread are the application's; (b) would answer all three for it. (a) is less
to build and less to outgrow.

An **atom** cannot be entered: the caret steps over it, a click inside puts
the caret at its nearer end, a selection boundary inside it moves outward, and
Backspace or Delete next to it removes it whole. A composition can never start
inside one, so preedit cannot split it. Its text is fixed when it is inserted
(`@Ann Lee`). A copy keeps it as a mark, and a plain-text paste of it is
ordinary text.

### D8. A stock formatting bar, replaceable (taken: ADR-0705)

(a) `rich-input` opens a small bar above a selection once it settles (the
pointer released, or `Shift` let go), with Bold, Italic, Underline, Strike,
Code, Spoiler and Link, each showing whether the whole selection has it. The
rows come from `Labels` (W2.13), so they are translated. The application can
give its own list of actions, or none. (b) No stock bar: the field publishes
the selection's rectangle and the application builds one.

The context menu gains the same toggles under a Format submenu either way,
because a keyboard user and a touchpad user both need a route that is not a
hover.

### D9. Markdown typed as markdown (taken: later)

(a) Not in this plan. A toggle key or the bar formats text, and the
Markdown conversion in phase 6 lets an application turn `**bold**` into marks
on send, as Telegram's desktop client does. (b) Input rules in phase 5:
`**x**`, `_x_` and `` `x` `` become marks as the closing delimiter is typed,
undone by one `Ctrl+Z`.

### The toggle keys (not a decision, unless the owner wants one)

`Ctrl+B` bold, `Ctrl+I` italic, `Ctrl+U` underline, `Ctrl+Shift+X` strike,
`Ctrl+Shift+M` code, `Ctrl+Shift+P` spoiler, `Ctrl+K` link, `Ctrl+Shift+N`
clear formatting: Telegram Desktop's set, with `Cmd` for `Ctrl` on macOS
through `Shortcut.primary`. **As built**, they are not a new `EditSurface` or
`EditCommand`: `EditKeys.format(event)` answers a `FormatCommand` of their own,
so `text-input` and `text-area` change nothing at all and keep `Ctrl+B` free
for the application (ADR-0703).

## 4. The model in detail

Package `dev.goldberry.text.mark` in `core`, exported. All values.

```java
var text = MarkedText.of("Ship it on Friday")
        .mark(0, 4, MarkKind.BOLD)
        .mark(11, 17, new MarkKind.Link(URI.create("https://example.org")));

text.text();                    // "Ship it on Friday"
text.marks();                   // [Mark[0, 4, BOLD], Mark[11, 17, Link[…]]]
text.marksAt(2);                // {BOLD}
```

| Type | What it is |
|------|------------|
| `MarkKind` | Sealed: the enum `Style` (`BOLD`, `ITALIC`, `UNDERLINE`, `STRIKE`, `CODE`, `SPOILER`), and the records `Link(URI)`, `Mention(String id)` and `Custom(name, value, Behaviour)`. `behaviour()` answers D2's table, and `className()` the class D3 styles it by |
| `Mark` | `(start, end, kind)` with `start < end`. No empty marks |
| `MarkedText` | `(text, marks)` in normal form: sorted by start, then end, then kind; same-kind marks merged; exclusions applied; nothing outside the text; no atom split. `of`, `mark`, `unmark`, `marksAt`, `marksOver(start, end)`, `slice(start, end)`, `replace(start, end, MarkedText)` |
| `MarkedEdit` | A `TextEdit` with marks: `(MarkedText value, int anchor, int caret, Set<MarkKind> typing)`. `typing` is what the next typed character gets when the caret was toggled with nothing selected (ProseMirror's stored marks); it is cleared by any movement. Every `TextEdit` operation is mirrored, and steps also skip atoms. `toggle(kind)` over the selection, or over `typing` when there is none |

**How marks move on an edit.** `replace(start, end, inserted)` is the one
primitive; typing, deleting and pasting are all of it.

1. Text removed from a mark shrinks it, and a mark left empty goes.
2. A mark that spans the replaced range is split around the new text.
3. The new text gets the kinds it is told to inherit, then its own marks,
   which win a conflict. A typed letter inherits `typing` when it is set, and
   otherwise the kinds of every mark the caret is strictly inside and of every
   mark ending at it that grows at its end; so typing inside a bold word
   splits the mark and the inherited bold joins it again into one. Text
   inserted where a mark starts never gets it. A paste inherits nothing.
4. A replacement that touches an atom removes the whole atom first. Text
   inserted strictly inside an atom takes the atom's mark away and leaves its
   text; an editor's caret never stands there.
5. The result is normalised.

**As built**, the old rules 2 and 3 ("a spanning mark stays one mark around
the new text", "text at a mark's end gets it if it grows") became one rule
about inheritance, which is what lets a paste split a bold word and a
keystroke not.

These rules are the test plan for phase 1: a property test runs random edits
over random marked texts and checks the normal form, that no atom is ever
split, and that undo after any sequence gives back the first value.

**Display.** `Editor.displayText()` still splices the composition in at the
caret. The composition is shown with the marks `typing` (or rule 3) would give
it, so a word composed in bold looks bold while it is composed, and becomes
an edit only on commit, through rule 4.

## 5. Phases

### Phase 0 — this document and the decisions (done)

**Exit:** the owner has answered D1–D9, and each answer is an ADR. Answered on
2026-11-14; ADR-0703 and ADR-0704 hold D1–D6.

### Phase 1 — the model (`core` `text.mark`) (done, ADR-0703)

`MarkKind`, `Mark`, `MarkedText`, `MarkedEdit`, the edit rules of §4, and
`EditHistory<S>` with `FORMATTING` (D5).

**Tests:** `MarkedTextTest` (normal form, merge, exclusions, slice and
replace), `MarkedEditTest` (each `TextEdit` operation with marks, toggles,
`typing`, atoms under every movement and deletion), a property test over
random edits as §4 describes, and `EditHistoryTest` over both state types.

**Exit:** every rule in §4 has a test that names it. Met: `MarkedTextTest`
names rules 1 to 4, and `MarkedEditPropertyTest` runs 150 random edits under
ten seeds.

### Phase 2 — shaping, geometry and paint in `Editor` (done, ADR-0703)

`TextDocument` learns that a line is its text **and** its marks: a second
shaper interface, `MarkedShaper.shape(String line, List<Mark> lineMarks)`,
and a line re-shaped when either changed. A line is shaped run by run (one
`Paragraph` per stretch with the same font) and joined with `Paragraph.join`,
so `ParagraphCache` keeps every run that did not change. Colours, rules and
backgrounds become `SpanPaint`s. `Editor` holds a `MarkedEdit`; its caret,
hit test, selection rectangles and scrolling work across font seams, and its
caret steps over atoms. A **decoration layer** that is not marks (ranges with a
paint, held outside the value and the history) is added for the spellchecker
W3.7 brings, and for a mention query's highlight.

**Tests:** `EditorMarksTest` (a caret walk across a bold word in a larger
face, a click on either side of a seam, `Up`/`Down` keeping the column over
mixed sizes), `TextDocumentTest` (a mark change re-shapes one line; a colour
change re-shapes none), `EditorPreeditTest` (a composition inside bold;
never inside an atom), and an RTL case with a bold Arabic word.

**Exit:** the showcase's sticky note takes `Ctrl+B` with no widget involved.
Met (`StickyCardTest.formats`).

**As built**, beyond the text above: `Paragraph.joinOnGrid` joins the pieces of
a line with every line measured in the editor's own font, because the caret,
the hit test and the scroll count rows of one height and a joined paragraph
otherwise makes a line as tall as its tallest face. A marks-only change goes
through `TextDocument`'s own path, which shapes only the lines whose marks
moved. The decoration layer for the spellchecker is **not** built: W3.7 adds
it with the wavy underline it needs. `MarkedClipboard` (D6) landed here
rather than in phase 3, since `Editor.copy` is where it is used.

### Phase 3 — the `rich-input` control (done, ADR-0704)

`widgets` `form.richinput`: `RichInput` (value, `Observable<MarkedText>`,
`onChange`, placeholder, `rows`/`maxRows` growing as `text-area` does,
`maxLength`, read-only, disabled, attributes), its state and its box with the
D3 carriers. Toggle keys (`EditSurface.RICH`, `EditCommand.Format`), the
clipboard of D6, the context menu with a Format submenu, the IME candidate
area following the caret, focus and blink as `text-area` has them. KDL
inflation (`rich-input placeholder="Message"`), `bind=` to a
`Property<MarkedText>`.

**Tests:** `RichInputTest` (typing, toggles, undo restoring marks, grow and
scroll), `RichInputClipboardTest` (copy and paste between two inputs keeps
marks; plain text in, plain text out), `RichInputPreeditTest`, a markup
inflation case, and goldens of each kind styled by the toolkit's stylesheet,
light and dark.

**Exit:** a `rich-input` in the showcase formats with keys and the menu, and
its value round-trips through `bind=`. Partly met: the keys, `bind=` and the
stylesheet's looks are built and tested (`RichInputTest`); the context menu's
Format submenu and the showcase card are not, and move to phase 4 with the
bar. Light and dark goldens wait for the showcase card. The caret does not
blink yet, and `Enter` inserts a newline.

### Phase 4 — the formatting bar and links (done, ADR-0705)

The stock bar of D8 over a settled selection, closing on a collapsed
selection, `Escape`, focus loss or scroll. A link editor: `Ctrl+K` or the bar's
Link opens a small field for the address over the selection, and editing an
existing link selects it first.

**Tests:** `FormatBarTest` (opens on settle, not during a drag; each button
reflects and toggles; translated labels), a link edit and removal, a golden.

**As built**, with what phase 3 left over: the bar is `format-bar` in an
attached popup (`FormatPopups`), its buttons `FormatButton`s (icon, name,
`FormatCommand`), replaceable by `RichInput.formatting(…)` or none; the
`link-editor`; the text menu's Format submenu through `TextMenu` rows of a
field's own; `RichInput.onSubmit` (`Enter` sends, `Shift+Enter` breaks the
line); a blinking caret; and the showcase's composer card on the Forms
screen, whose sent messages are `rich-text` with the marks as run classes.
Tested by `RichInputFormattingTest`, `RichInputGoldenTest` and
`FormatBarGoldenTest` (both themes) and `ComposerCardTest`. The bar opens on a
settled selection from the keyboard at once, not when `Shift` is let go: a
key-made selection has no drag to wait for.

### Phase 5 — mentions (done, ADR-0706)

D7: trigger characters, `MentionQuery` reported as it changes, keys routed to
the application while a query is open, `replaceQuery` inserting an atom. If D9
is (b), input rules land here too.

**Tests:** `MentionTest` (a query opens at a word start and not inside a
word, follows typing, closes on a space or `Escape`; an inserted atom is
stepped over, deleted whole, never split by a composition, survives undo and
redo), and a showcase golden with a suggestion list open.

**Exit:** the showcase composer mentions a contact with the keyboard alone.
Met (`ComposerCardTest.mentionsWithKeys`: `@a`, `Down`, `Enter`, then `Enter`
sends a message whose `mention` run reads `@Ana Silva`).

**As built.** The query is found in `core`, as `MentionQuery.at(MarkedEdit,
triggers)` in `text.mark`. Its `completion(text, kind, in)` is what replaces
the word: the text under the kind, then a space unless whitespace follows.
The field takes `RichInput.mentions(Mentions)`, which holds the triggers, an
`onQuery(Optional<MentionPrompt>)` listener and a key handler.
`replaceQuery` became `MentionPrompt.complete(text, kind)` on the reported
prompt, so a list never needs a handle on the field. A prompt the field has
moved on from changes nothing. The word a query covers runs past the caret to
the word's end, and the whole word is replaced. Code, an atom and punctuation
all close a query, and a query is at most 64 chars. Nothing is reported
during a composition. `Editor.rangeRects` is new and gives the anchor. The
query is **not highlighted**: that waits for W3.7's decoration layer, as
phase 2 said. D9 stayed "later", so there are no input rules. The tests are
`MentionQueryTest` (core), `EditorPreeditTest`'s atom and range cases,
`RichInputMentionTest`, and in the showcase `ContactTest`,
`ComposerCardTest` and `MentionListGoldenTest`, both themes. The golden draws
the list alone, because a headless session opens no popups.

### Phase 6 — conversions in `:html` (done, ADR-0707)

`markdown.marked` (or the package D6's converter lands in): `MarkedText` to
Markdown and back for the subset marks express, and HTML on paste (`<b>`,
`<strong>`, `<i>`, `<em>`, `<u>`, `<s>`, `<code>`, `<a href>`), everything else
read as its text. The converter installs into `rich-input`'s clipboard (D6).

**Tests:** round trips through Markdown for every kind, an HTML paste from a
captured browser selection, and unknown elements reduced to their text.

**As built.** The hook is `core`'s `MarkedFormat` (a MIME type, `write`,
`read`), installed with `RichInput.clipboardFormats(…)` or
`Editor.clipboardFormats(…)`, rather than D6's `RichInput.clipboard(…)`. A
paste tries the toolkit's own type, then each format, then the text.
`MarkedMarkdown` (in `markdown.marked`) writes and reads its own dialect,
`SYNTAX`: CommonMark with strike-through, `_` underline, hard breaks, and no
HTML or indented code. A spoiler is `||x||`, swapped for a rare punctuation
character before the parse. A mention is a `mention:` link and a custom kind a
`mark:` link (`content.marked.MarkLinks`). `MarkedHtml` (in `html.marked`)
also reads inline styles, so a document editor's paste keeps its bold and its
`font-weight: normal` wrapper does not make everything bold.
`MarkedHtml.format()` is the `text/html` format, and it reads UTF-16 with a
byte-order mark and the Windows `Version:` header. What Markdown cannot hold is
listed in the class comment. The random round trip promises the same text and
the same kinds on every non-whitespace character. The showcase composer
installs the HTML format and reads a draft with no formatting as Markdown on
send, which is D9 taken at send time. The tests are `MarkedClipboardTest`,
`MarkedMarkdownTest`, `MarkedHtmlTest`, `MarkLinksTest`,
`RichInputClipboardFormatTest` and `ComposerCardTest`.

### Phase 7 — the showcase, the guide and the answer

A composer in the showcase (a message list from W2.5's sections, a
`rich-input` with the bar and mentions, sent messages shown in `rich-text`
from the same marks). A guide section parked in `docs/snapshot/`, with
`BookTest`'s heading for `rich-input`. Tessera's answer written in
`gaps/answers-tessera.md`.

## 6. Risks

- **`Editor` is public API**, and phase 2 changes what it holds. Every
  existing method keeps its meaning with no marks; `EditorDocumentTest`,
  `EditorPreeditTest`, `EditorFallbackTest` and the showcase's sticky note are
  the net.
- **`EditHistory<S>`** (D5) is a signature change on a public class. It goes
  into `docs/snapshot/changelog-breaking-changes.md` even if no caller breaks.
- **A new `EditCommand`** breaks the exhaustive switches in the three editors
  on purpose. That is the sealed interface doing its job, and each editor
  answers `Format` (the plain ones by ignoring it).
- **Seams between fonts.** A caret at a seam between two sizes, a selection
  rectangle across it and a hit test on either side are where a joined
  paragraph has been least tested. Phase 2 tests them before anything is
  built on them.
- **Bidi with marks.** A mark's range is logical; its rectangles may be
  several on one line. `Paragraph.stretchesOf` already answers that, and an
  RTL test is in phase 2.
- **Size.** Thousands of marks in a pasted document are a list scan per
  query. A composer holds tens. If it matters, `MarkedText` gets an interval
  index behind the same API.

## 7. Documents kept in step

- `gaps/goldberry-gaps-plan.md` §8 (T-02's row) and §10, and
  `gaps/answers-tessera.md` (G-02), as each phase lands.
- `docs/ARCHITECTURE.md`'s "Text editing depth" line, which still says rich
  text is deferred.
- `docs/snapshot/`: the ADRs, the guide section, and the breaking-changes note
  for D5.

## 8. Log

- **2026-11-14.** Written against `6741de31`. Found: `TextEdit` and
  `EditHistory` are values over a string, so marks can ride along as part of
  the state; `TextDocument.Shaper` sees only a line's text, so a mark change
  would re-shape nothing until it sees marks too; `Paragraph.join` and
  `ParagraphCache` already shape mixed faces without re-shaping unchanged
  runs; `Paragraph` has no inline objects, so a mention is an atom of text;
  `:widgets` cannot see `:html`'s Markdown model. D1–D9 proposed, nothing
  decided. No code.
- **2026-11-14, later.** The owner took every recommendation. Phase 1:
  `text.mark` with `MarkKind`, `Mark`, `MarkedText`, `MarkedEdit`, and
  `EditHistory<S>` with `FORMATTING`. Phase 2: `Editor` over a `MarkedEdit`,
  `markStyles`, `Paragraph.joinOnGrid`, `TextDocument.MarkedShaper`,
  `EditKeys.format` and `FormatCommand`, `MarkRun`, `MarkStyle(s)` and
  `MarkedClipboard`; the sticky note formats. Phase 3: `rich-input` in
  `form.richinput`, with `mark` carriers and rules in `controls.css`. Three
  departures from the text above, each marked "as built". ADR-0703 and
  ADR-0704; the guide section parked in
  `docs/snapshot/components-forms-rich-input.md`. Next ADR 0705.
- **2026-11-14, night.** Phase 4 (ADR-0705): the formatting bar, the link
  editor, the Format submenu, `onSubmit`, the caret blink and the composer
  card, with goldens of the field and the bar. Twelve `ToolkitLabel`s. Next
  ADR 0706. Open: phase 5 (mentions, D7), 6 (Markdown and HTML in `:html`),
  7 (the guide moves at release; Tessera's answer kept current).
  The composer card raised the Forms screen's count in the showcase's
  sidebar from 13 to 14, which every screen draws: 37 gallery and shell
  goldens and 18 screen pictures in `book/src/images` (`ScreenPicturesTest`)
  were taken again, 63 pixels each. Verified: `:core` 5930, `:widgets` 3262 and
  `:example` 938 tests green after the retake, javadoc clean, the showcase on
  the module path.
- **2026-11-15.** Phases 1 to 4 were committed to master as `93a1ee62`.
  Phase 5 (ADR-0706): `MentionQuery` in `core`, `Mentions` and
  `MentionPrompt` on `rich-input`, the list keys routed to the application,
  `Editor.rangeRects`, and in the showcase `ui.forms.mention` (`Contact`,
  `Contacts`, `MentionList`, `MentionPicker`), with the composer card
  mentioning from the keyboard. The card's description changed, so the
  showcase's search index (`index.tsv`) was written again. Verified: `:core`
  5960, `:widgets` 3280 and `:example` 946 tests green, javadoc clean, the
  showcase on the module path (dummy driver). `spotlessCheck` is red only in
  `:media`'s `PictureView`, which was already wrong at `93a1ee62`. Next ADR
  0707. Open: phase 6 (Markdown and HTML in `:html`) and phase 7.
- **2026-11-15, later.** Phase 5 was committed as `84f1f44d`. Phase 6
  (ADR-0707): `MarkedFormat` in `core`, `MarkedMarkdown` and `MarkedHtml` in
  `:html` (two more exported packages, and `content.marked`, which is not
  exported), the `text/html` format, and the composer reading Markdown on send.
  Writing Markdown had a bug, found by the random round trip: a kind closed and
  reopened at the same boundary was not reopened. The test host's clipboard in
  `:widgets` holds bytes now. The card's description changed again, and so did
  `index.tsv`. Verified: `:core` 5964, `:widgets` 3283, `:html` 354 and
  `:example` 948 tests green; spotless and javadoc clean; the showcase on the
  module path. Three `GalleryTextScaleTest` screens first failed on a truncated
  jar while another session rebuilt `:gpu` in the same tree, and passed on a
  rerun. Next ADR 0708. Open: phase 7.
