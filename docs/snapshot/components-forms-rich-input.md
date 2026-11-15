# Parked for `book/src/components/forms.md`: `rich-input`

Everything below the rule goes into the book at release. `BookTest` reads the
`## `name`` headings and the catalogue rows of this file as if they were in the
book until then.

Where each piece goes:

1. **The section**: in `book/src/components/forms.md`, after the `## `text-area``
   section and before `## `field``.
2. **In `book/src/components/index.md`**, the catalogue row, in alphabetical
   order after `rich-text`:

   | [`rich-input`](forms.md#rich-input) | Fields and forms |

   and the card's list gains `rich-input` after `text-area`:

   <a class="gb-card" href="forms.html"><strong>Fields and forms</strong><span>text-input, text-area, rich-input, field, form, code-input, date-picker, time-picker, color-picker</span></a>

3. **In `book/src/guide/text.md`, `## Selection and editing`**, after the
   paragraph on the canvas `Editor`, the paragraph under "Marks" below.
4. **The picture**: `rich-input-light.webp` and `rich-input-dark.webp` do not
   exist yet. `BookPicturesTest` takes them from the `kdl` sample once the
   section is in the book (`:example:test` with the golden update flag).

---

## `rich-input`

A `rich-input` is a field that takes formatting: bold, italic, underline,
strike-through, code, spoilers, links and mentions, in a message being written.
It grows from `rows` to `max-rows` with its text and scrolls after that, as a
`text-area` does.

```kdl
column {
  rich-input placeholder="Message" rows=1 max-rows=8
}
```

```java
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widgets.form.richinput.RichInput;

new RichInput(draft, next -> draft = next)
        .placeholder("Message")
        .maxRows(8);
```

Its value is a `MarkedText`: the words, and the marks over them as ranges of
`char` offsets. Those are the units a `String` is indexed by, and the ones a
messenger's entities are counted in, so a message converts one mark to one
entity with no arithmetic. `bind=` follows a value that holds a `MarkedText`
or a plain `String`.

### Keyboard

Every key a `text-area` takes, and the formatting keys, on the desktop's own
modifier (`Cmd` on macOS):

| Keys | Does |
|------|------|
| `Ctrl+B`, `Ctrl+I`, `Ctrl+U` | Bold, italic, underline |
| `Ctrl+Shift+X` | Strike-through |
| `Ctrl+Shift+M` | Code |
| `Ctrl+Shift+P` | Spoiler |
| `Ctrl+Shift+N` | Clear formatting |
| `Ctrl+K` | A link: the address editor |

With a selection, a key turns its style on over all of it, or off when all of
it has the style already. With nothing selected, it turns the style on or off
for what is typed next, until the caret moves. Undo brings formatting back with
the words: a style turned on is one step. A copy keeps its marks when it is
pasted into another rich field, and is plain text everywhere else.

### The formatting bar

Select words and let go, and a bar opens above them: bold, italic, underline,
strike-through, code, spoiler, link and clear formatting. A button whose style
covers the whole selection is lit. The bar never takes the keyboard, so the
selection stays where it was while you press it, and it goes when the
selection does.

```java
new RichInput(draft, onChange).formatting(List.of(
        new FormatButton("bold", "Bold", new FormatCommand.Toggle(MarkKind.Style.BOLD)),
        new FormatButton("link", "Link", FormatCommand.Simple.LINK)));
```

`formatting(List.of())` takes the bar away, and `formatting=#false` does in
markup. The same commands are in the text menu, under Format.

`Ctrl+K`, or the bar's Link, opens a small editor for the address under the
selection; with the caret in a link and nothing selected, it edits that link.
An address typed without a scheme is `https`.

### Sending

```java
new RichInput(draft, onChange).onSubmit(message -> send(message));
```

With `onSubmit`, `Enter` sends the text and `Shift+Enter` breaks the line, as a
message composer does. Without it, `Enter` breaks the line.

### Mentions

Type `@` at the start of a word and the field opens a **query**: it tells the
application what is typed after the trigger, letter by letter, and where it
is. The application looks people up and shows its own list. The field draws
none, because who can be mentioned, how a suggestion looks and where the list
goes are the application's.

```java
import dev.goldberry.widgets.form.richinput.MentionPrompt;
import dev.goldberry.widgets.form.richinput.Mentions;

new RichInput(draft, onChange)
        .mentions(Mentions.on(this::suggest, "@", "#").keys(this::listKey));

void suggest(Optional<MentionPrompt> prompt) {
    prompt.ifPresentOrElse(
            open -> showPeople(lookUp(open.query().query()), open.anchor()),
            this::hidePeople);
}

void choose(Person person) {
    prompt.complete("@" + person.name(), new MarkKind.Mention(person.id()));
}
```

A query is a trigger at the start of the text, after a space or after an
opening bracket or quote, and the letters, digits and `_` after it. So
`mail@example` opens none, and a space, a comma or a caret moved away closes
one. `onQuery` hears it open, hears every change, and hears
`Optional.empty()` once when it ends. `anchor()` is the query's line in window
coordinates, which is what a popup is opened against.

While a query is open, `Up`, `Down`, `Enter`, `Tab` and `Escape` are offered
to the key handler first, and it returns true for a key it takes. An `Escape`
it leaves closes the query until the caret leaves the word.

`complete` replaces the whole word, trigger included, with the text you give
under the kind you give, and adds a space after it. It is one undo step. A
`MarkKind.Mention` makes the text an atom; null puts in plain text, such as an
emoji for a `:` shortcode. A prompt answers only for the query it was made
for: once another letter is typed, completing the old one changes nothing.

A mention is an atom. The caret steps over it, a click inside lands at its
nearer end, a selection grows to cover it, and `Backspace` next to it removes
all of it. An input method cannot start a composition inside one.

### Markdown and HTML

`goldberry-html` converts marked text both ways. `MarkedMarkdown` writes a
message's Markdown and reads it back, and `MarkedHtml` does the same for HTML:

```java
import dev.goldberry.html.marked.MarkedHtml;
import dev.goldberry.markdown.marked.MarkedMarkdown;

String markdown = MarkedMarkdown.write(draft);   // "Ship it **today**"
MarkedText typed = MarkedMarkdown.read("Ship it **today**");
String html = MarkedHtml.write(draft);           // "Ship it <strong>today</strong>"
```

The Markdown is the kind a messenger accepts: `**bold**`, `*italic*`,
`_underline_`, `~~strike~~`, `` `code` ``, `||spoiler||`, `[words](address)`,
and a mention as a link to `mention:` and its id. Text that looks like markup
is escaped, so it reads back as itself. Markdown cannot say everything marks
can: a mark across a line break comes back as one per line, and whitespace at
the edge of bold or italic falls outside it.

To keep formatting on the clipboard beyond other rich fields, install the HTML
format. A copy then offers HTML to a word processor, and a paste from a
browser or a document editor keeps its bold, italic, links and code:

```java
new RichInput(draft, onChange).clipboardFormats(List.of(MarkedHtml.format()));
```

A paste prefers the toolkit's own type, then each installed format in turn,
then the plain text.

### Styling the marks

Each set of marks in the text is a `mark` node under the field, with a class
per kind: `bold`, `italic`, `underline`, `strike`, `code`, `spoiler`, `link`,
`mention`, or the name of a kind an application adds. The field draws its
marked words with what the cascade resolved for that node:

```css
rich-input > mark.code { font-family: "JetBrains Mono"; background: var(--gb-surface); }
rich-input > mark.bold.italic { color: var(--gb-accent); }
```

A mark's font, `color`, `text-decoration` and `background` are read. The lines
stay the field's own height, so a mark in a larger face draws larger glyphs on
the same rows.

### Marks

*(For `book/src/guide/text.md`, `## Selection and editing`.)*

Text can carry marks. A `MarkedText` is a string and its marks, each a kind
over a range; `MarkedEdit` is the same value with a caret, the marked twin of
`TextEdit`. Hand the canvas `Editor` a `MarkStyles` and it shapes marked words
in their own fonts and takes the formatting keys; without one it keeps the
marks and draws them as plain text.
