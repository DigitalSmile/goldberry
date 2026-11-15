# ADR-0707: Marked text in Markdown and HTML

- **Status:** Accepted, as phase 6 of `docs/rich-text-input.md`, under D6
  (taken as recommended on 2026-11-14) and D9 ("later").
- **Date:** 2026-11-15
- **Relates to:** ADR-0703 (text carries marks), ADR-0704 (a field takes
  formatting), ADR-0706 (mentions)

## Context

D6 settled that a copy carries marks in a type of the toolkit's own, and that
HTML in both directions goes through a converter the application installs
from `:html`, because `:widgets` cannot see `:html`. D9 left typed Markdown to
"later", but said that a Markdown conversion lets an application turn
`**bold**` into marks when a message is sent. What was missing was the hook
the converter installs into, the converters themselves, and a decision on how
to write marks that Markdown has no syntax for.

## Decision

**A clipboard format is an interface in `core`.** `text.mark.MarkedFormat` is
a MIME type, `write(MarkedText)` and `read(byte[])`. `MarkedClipboard.copy` and
`paste` take a list of them. A copy with marks offers the toolkit's own type,
each format, and the plain text. A paste tries the toolkit's own type, then
each format in order, then the plain text. A format that throws or reads
nothing is passed over. `Editor.clipboardFormats(…)` and
`RichInput.clipboardFormats(…)` install them. D6's
`RichInput.clipboard(MarkedClipboard)` became this, since `MarkedClipboard` is
a set of functions rather than a thing to install.

**Markdown: `dev.goldberry.markdown.marked.MarkedMarkdown`.** Its dialect is
`SYNTAX`: CommonMark with `~~strike~~` and `_underline_`, every newline a line
break, and no indented code and no HTML, which is how a message is written.
It writes bold as `**`, italic as `*`, underline as `_`, strike as `~~`, and
code in as many backticks as the code needs. Text is escaped so that it reads
back as itself, block markers at a line start included. Marks that overlap
are nested with the longer one outside, and code always innermost. Whitespace
moves outside emphasis delimiters, because `** x**` is not bold, and stays
inside a link's brackets or a spoiler's pipes. Writing goes a line at a time.

**Kinds Markdown lacks.** A spoiler is `||x||`, the messengers' own syntax.
The reader swaps each unescaped `||` for a rare punctuation character that the
document does not contain, so the parser treats it as `|`, and pairs those up
afterwards. An escaped `\|` stays a pipe, and an odd last `||` reads as pipes.
A mention is a link to `mention:` and its percent-encoded id. A custom kind is
a link to `mark:name/value`, with `?grows&atomic&exclusive` flags as needed.
The link scheme is shared with HTML through `content.marked.MarkLinks`, which
is not exported.

**Reading Markdown** folds the parsed document into marks, with an inner mark
winning a conflict (code inside bold is code). Blocks that marks cannot hold
are read as their words: a heading is its text, a list item its text after
`- ` or `N. `, a code block is code, and paragraphs are a blank line apart.

**HTML: `dev.goldberry.html.marked.MarkedHtml`.**
- **Writing** uses `<strong>`, `<em>`, `<u>`, `<s>`, `<code>`,
  `<span class="spoiler">` and `<a href>`, nested properly, with `<br>` for a
  newline.
- **Reading** takes those elements and their usual synonyms, `span.spoiler`
  and `span.tg-spoiler`. It also reads an inline `style` for weight, slant,
  underline or strike-through and a monospace family. A `font-weight: normal`
  removes bold, which is what a document editor's paste needs: such editors
  wrap the whole selection in `<b style="font-weight:normal">`.
- **Whitespace** collapses as a browser collapses it, except in `pre`, and a
  mark never starts or ends on it.
- **Blocks** start new lines, `br` breaks a line, a list item starts with
  `- `, and metadata, scripts and styles are dropped.
- **`MarkedHtml.format()`** is the `text/html` `MarkedFormat`. It writes UTF-8
  behind a `<meta charset>`. It reads UTF-16 when a byte-order mark says so
  (one browser writes that on Linux) and skips the `Version:` header Windows
  puts on HTML.

**The showcase uses both.** The composer installs `MarkedHtml.format()`. A
draft with no formatting is read as Markdown when it is sent, as Telegram's
desktop client does, so `**today**` typed by hand arrives bold. That is D9's
"later" taken at send time, not as input rules while typing.

## Consequences

- Markdown is smaller than marks, so some round trips change the value. A
  mark across a line break comes back as one mark per line. Whitespace at the
  edge of emphasis falls outside it. Underline inside a word (`a_b_c`) is not
  underline to CommonMark. Leading spaces and extra blank lines are not kept.
  The class comment lists these. What is promised, and tested over random
  marks, is that the text comes back the same and every non-whitespace
  character keeps its kinds.
- A real link to `mention:` or `mark:` reads back as that kind.
- `RichInput` has one more component, `clipboardFormats`. It is unreleased,
  so there is no breaking-changes note.
- `:html` exports two more packages, `markdown.marked` and `html.marked`.
- The test host's clipboard in `:widgets` now holds bytes as well as text, as
  a desktop's does.
- Tested by `MarkedClipboardTest` (formats offered, paste order, a throwing
  format), `MarkedMarkdownTest` (each kind written and read, nesting,
  whitespace, fences, escaping, spoilers and pipes, blocks, mentions, round
  trips for every kind and for markup-looking text, and 200 random word-aligned
  values), `MarkedHtmlTest` (each kind written and read, nesting, a captured
  browser selection, a document editor's paste, unknown elements, structure,
  trimming, a round trip of every kind, the format's encodings),
  `MarkLinksTest`, `RichInputClipboardFormatTest`, and `ComposerCardTest`
  (Markdown on send, HTML installed).
