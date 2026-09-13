# 298. HTML is a document, and not an engine

Date: 2026-09-13

## Status

Accepted. Closes `docs/gaps.md` **G17** — `html-view` — and answers the part of
it that asked for litehtml with **no, not for this**.

Builds on [ADR-0295](0295-a-document-is-a-value-and-a-paragraph-is-a-row-of-words.md),
whose shape this copies exactly, and on
[ADR-0293](0293-a-button-that-reads-as-a-link.md), whose `button.link` is what an
anchor becomes. Supersedes `docs/content-widgets.md` §1.1's architecture for
`html-view` and leaves §1.2's — Markdown *through* litehtml — dead where ADR-0295
left it.

## Context

G17 is the last entry on brd's list and the only one nothing has asked for. It
exists because `content-widgets.md` §1 specifies `html-view` beside
`markdown-view`, and because G8 used to promise both — so the remainder should be
visible rather than quietly dropped.

Its own text says what it waits on, and that has not changed since it was
written:

> litehtml's `document_container` is a C++ virtual class, which FFM cannot
> implement, so it lives in a native library of its own and draws through
> `libgoldberry`'s exported C symbols — and that surface has no rounded geometry.

That is a real project. A second superbuild, a second native artifact with four
classifier jars and four CI legs (ADR-0190), a C++ container implementing
fifteen-odd callbacks against the toolkit's text stack, and first a widening of
the exported paint surface with rounded geometry and a nested state stack in it —
work that is *shared* with `goldberry-vector` and `goldberry-terminal` and that
renders no HTML on its own.

Meanwhile the thing G17 is actually for — **authored content on screen: a help
page, a changelog, the HTML half of an email, a note somebody kept as HTML
instead of as Markdown** — needs none of it. ADR-0295 had already proved that on
the other half of this module: md4c's events became a tree of records and a fold
into `column`, `row` and `text`, and a Markdown document renders under the
ordinary cascade with no engine anywhere.

The question this record answers is therefore not "how do we embed litehtml". It
is **"is the engine what G17 wants, or is a document what G17 wants"**.

## Decision

### A document, parsed in Java, folded like a note

`html-view` ships now, with no litehtml under it and no new native symbol:

```
io.github.digitalsmile.goldberry.html          Html.parse
io.github.digitalsmile.goldberry.html.model    the page, as records
io.github.digitalsmile.goldberry.html.view     html-view, HtmlStyles
```

```java
var document = Html.parse(page.body());

var view  = HtmlView.of(document).onLink(app::navigate);
var links = document.find("a");
var words = document.text();
```

```kdl
scroll { html-view bind="doc.source" link="doc.open" }
```

Every decision below is ADR-0295's, restated because it turned out to be about
*documents* rather than about Markdown:

- **A document is a value.** `HtmlNode` is sealed over `HtmlDocument`, `Element`,
  `HtmlText` and `Comment`; a fold over it is an exhaustive `switch` that stops
  compiling when the model grows. A preview is not the only thing an application
  does with a page — an outline, a link check, a word count, an image prefetch —
  and a widget that hid the parse would make each of them a second parse.
- **A paragraph is a wrapping row of words**, one `text` widget each, because the
  text stack shapes one font per run. The two views share the code that does it:
  `content.inline.Words`, in a package neither of them owns and nothing exports.
- **The appearance is a stylesheet.** `html.css` in the `TOOLKIT_BASE` layer, added
  by the application beside `Controls.stylesheets(theme)`. This *is*
  `content-widgets.md` §1.4's "master stylesheet generated from the active theme",
  written in `var(--gb-*)` so that there is nothing to regenerate on a theme
  switch.
- **A preview is a binding** (ADR-0296). `bind=` re-parses the property on every
  build; the showcase's HTML screen is the Markdown screen with one node name
  changed.

### The tag is an open string, and the class on a widget is the tag

`Element.tag()` is a lower-cased `String` rather than an enum, and this is the one
place the two halves of the module genuinely differ. Markdown has a **closed**
vocabulary — md4c reports one of twenty block types — and HTML has not had one
for a decade. So:

- the **node kinds** are sealed and exhaustively matched, and
- the **tags** are open: an element contributes the class `html-<tag>`, and
  `html.css` decides what that looks like.

There is therefore no table in Java mapping `em` to "emphasis". `html.css` reads
like a browser's default sheet, `<my-callout>` renders as a block and is already
styleable, and adding a rule for `<figure>` is a rule rather than a commit to the
fold. Only the handful of tags whose *structure* differs — a list's gutter, a
quotation's bar, a fence's lines, a table's rows, an anchor — are named in Java.

A document's own `class="callout"` comes through as **`html-callout`**: the same
namespace, so a page cannot be restyled by an application's rule for its own
`.callout` and an application styling its documents has one prefix to learn. An
`id` is not forwarded at all, because an id is unique in a tree and a page's are
the author's.

### An anchor is a `button.link`

The one thing `markdown-view` cannot do, and the reason it cannot is worth
stating: a Markdown link is four words, so following one needs hover and press on
a *run* that the fold has already split. An HTML anchor is an element with a
label, so the whole run is one widget — a `Button` carrying ADR-0293's `link`
variant, with the anchor's text as its label.

That buys a Tab stop, `:hover`, `Space` and `Enter`, and an accessible name, for
free and from the catalogue. What it costs is one rule that overrides a
deliberate decision of the toolkit's, written down in `html.css` beside the
rule: **ADR-0293 keeps the 32px button height because §1.3 makes it a floor for a
control, and an inline link takes `height: auto`**. A link inside a sentence is
not chrome — it is a word, its target is the line it sits on, and a 32px-tall
word would set every paragraph containing a link on triple-spaced lines. Every
browser does the same. A block-level action in a page is still a `button`
somebody wrote and still gets the floor.

**Following is the application's.** `onLink` is handed the `href` and nothing
else: no browser opens, no relative path resolves, nothing is fetched. That is
ADR-0291's division for URL schemes and ADR-0190's for images, applied to the
place a reader is most likely to expect otherwise.

### There is no failure mode

Every string is a page. A stray `</div>`, an unclosed `<p>`, `a < b` in a
sentence, a file truncated mid-comment — each has a recovery written beside the
code that performs it, because a content renderer that threw on a page a browser
draws is useless for the corpus it exists to read. The two that matter most:

- **A `<p>` is ended by any block tag and by no inline one**, which is how
  paragraphs are actually written.
- **`<li>First<li>Second` is two items**, not one inside the other. Without that
  table the second bullet is drawn indented under the first, which reads as a
  styling bug and is a parsing one.

### And the list of what it is not, in the API's own words

`Html`'s javadoc carries it, so that nobody has to discover it:

- **Not a browser.** No scripting, no network, no navigation. The README's
  promise was always "renders your HTML content, beautifully and offline".
- **Not the HTML5 parsing algorithm.** No implied `html`/`head`/`body`, no foster
  parenting, no adoption agency, no namespaces. A fragment stays a fragment.
- **Not a CSS engine.** `<style>` and `style=` are kept in the model and applied
  by nothing; the cascade is the application's stylesheets, which is what makes a
  page follow the theme instead of fighting it.
- **Not a resolver.** An `href` and a `src` are strings.

### litehtml stays open, and stays exactly where it was

This record does not delete the engine from the plan; it removes `html-view` from
the list of things waiting on it. What an engine still buys, and what nothing
here does:

- **Real inline layout** — a line of mixed faces as one shaped run, with
  justification, hyphenation, and a selection a reader can drag across two faces.
- **Text selection**, which follows from it.
- **The rest of CSS**: floats, positioned elements, `vertical-align`, per-side
  borders, `border-radius` on a page's own boxes.

When something asks for those, the work is what `book/src/TODO.md` already
describes — the wider paint surface first, shared with two other modules — and
the model above is what it would render, with `HtmlWidgets` becoming the second
renderer rather than the only one.

## Consequences

**The `:html` module has two widget trees, and that broke the weaver.** The
catalog is written into "the longest package prefix every widget shares", which
for `markdown.view.MarkdownView` and `html.view.HtmlView` is
`io.github.digitalsmile.goldberry` — a package **`:core`** owns. Two named
modules containing one package is a `LayerInstantiationException` on the module
path, so the first application to put both content widgets on its path would not
have started, and no class-path test could have seen it. `CatalogWeaver` now
descends from that prefix to a package the module actually has a class in. It is
a latent bug this change exposed rather than one it introduced: any module with
two widget trees had it.

**Two stylesheets, not one.** An application that renders notes and never pages
adds `MarkdownStyles.stylesheet()` alone. The two sheets agree about sizes and
gaps on purpose — `controls.css` says the controls "must not look like they were
designed by different people", and two kinds of document are no different — but
neither names the other's classes, and a test says so.

**`Words` moved**, from `markdown.view` to `content.inline`, and grew a `Piece`
that can be a widget. That last part is what puts a link's full stop against it:
a button flushed as a widget of its own left `the help .` on the screen, which is
the exact mistake ADR-0295 recorded about `*emphasis*, and`. `Entities` moved the
same way and grew `resolveAll`, because md4c *marks* an entity for the Markdown
side and an HTML tokenizer has to find its own.

**The showcase has ten screens**, which is exactly the ten digits a keyboard has.
The eleventh is now a decision about which screen loses its accelerator rather
than an addition.

**No new native symbol, and no new native artifact.** The one thing that crosses
is md4c's entity table, which was already there — 2125 names through one exported
symbol rather than a copy that drifts (ADR-0010). A machine with no
`libgoldberry` can parse HTML; it just cannot resolve `&nbsp;`.

**A page is roughly one widget per word**, as a note is (ADR-0295). The same
bound applies and the same thing would fix it, which is the virtualization
argument applied to a document and which nothing needs yet.

## Alternatives considered

**Build litehtml, as G17 and `content-widgets.md` §1.1 specify.** Rejected *for
now*, not on the merits: it is the only way to get real inline layout, and the
first commit of it is a paint-surface widening that two other planned modules
want. What settled it is the ordering — that work renders nothing until all of it
is done, and this renders a help page today. The record above keeps it open.

**Render HTML by translating it into the Markdown model.** Rejected. It is lossy
in the direction that matters — `div`, `span`, attributes, and every tag the
author invented — and it points the module's dependency the wrong way: ADR-0295's
split exists because Markdown *produces* HTML and never reads it.

**A `Tag` enum.** Rejected: it would make a fold exhaustive and `<my-widget>`
unrepresentable, and authored HTML has been full of invented elements for a
decade. The compromise above — sealed kinds, open tags — keeps the exhaustiveness
where it catches something.

**Forward a document's `class` and `id` unprefixed**, so a page's own stylesheet
vocabulary reaches the cascade directly. Rejected: a page's `class="card"` would
then be styled by the application's rule for its own cards, which is a wrong
picture with no visible cause. The `html-` prefix is one rule to explain and
impossible to collide with.

**Draw an `<img>`.** Deferred, and it is not an engine's problem: `Image.decode`
and `Frame.drawImage` have existed since ADR-0283, and what is missing is an
`img` *widget* in the catalogue and an answer about who fetches. Both are
somebody else's record. Until then an image is its alt text, exactly as in a
Markdown note.

**Make links inert, like `markdown-view`'s.** Rejected: it is the one capability
G17 promised over G8 that costs nothing here, and a help page whose links do not
work is a help page nobody trusts.
