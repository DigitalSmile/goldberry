# 296. A preview is a binding, not a callback

Date: 2026-09-13

## Status

Accepted. Extends
[ADR-0295](0295-a-document-is-a-value-and-a-paragraph-is-a-row-of-words.md);
[ADR-0297](0297-an-editor-fills-its-pane-and-a-split-knows-its-own-width.md) is
what building the showcase screen for it found in the catalog.

## Context

ADR-0295 shipped `markdown-view` taking a `Document`. That is the right shape for
a note somebody opened, and it is not a shape an **editor** can use: a live
preview re-parses on every keystroke, and a widget that takes a finished document
leaves the application to notice the keystroke, re-parse, and rebuild the subtree
by hand.

`docs/gaps.md` G8 names that case explicitly — *"the Note editor's live preview"*
— so it is the case to get right rather than a nice-to-have.

The toolkit already has an answer for "this widget's content comes from
somewhere": §9's `bind=`, which ADR-0062 gave to `text`, `badge`, `progress`,
`select` and every field. An element subscribes to whatever its widget returns
from `binding()`, and a change marks it for rebuild.

## Decision

### `markdown-view` is bindable, and that is the whole of "live"

```kdl
split-pane {
    text-area class="mono" bind="md.source" change="md.set-source" fill=#true
    scroll { markdown-view bind="md.source" }
}
```

Two nodes, one property, and **nothing between them**. The editor writes the
property through an action; the preview's element is subscribed to it; a keystroke
marks that element for rebuild; the next frame is the parsed document. No
controller, no listener in the application, no diffing.

In Java it is `MarkdownView.following(property)`, and what is on screen right now
is `resolved()` — read at build rather than captured at construction, which is the
rule `text` already follows.

### The dialect rides the widget

A bound view parses text the widget never saw at construction, so it has to know
*how*: `MarkdownSyntax` is a component of `MarkdownView`, defaulting to
`gitHub()`, and a document may name `syntax="commonmark"`. Two words rather than a
list of extensions — a document choosing bit by bit would be a document with
opinions about md4c's flags, and composing a dialect is Java's job.

### The parse is not cached

A keystroke re-parses the whole document. md4c reads a note in microseconds
(ADR-0294) and the rebuild that follows costs far more than the parse — so a cache
would be a lifetime to explain, an invalidation to get wrong, and nothing
measurable to show for it. The number worth knowing is the *widget* count, which
ADR-0295 already put at roughly one per word.

### The showcase gains a screen, and it is two nodes of markup

The gallery had eight screens and now has nine. **Markdown** is a `split-pane`:
the editor on the left, the same property rendered on the right. It is the screen
that demonstrates an *optional module* — `goldberry-html` is not a dependency of
the toolkit, so the application adds it, adds `MarkdownStyles.stylesheet()`, and
gets a node it never registered (ADR-0131).

The sample it opens with is a resource rather than a Java text block, and covers
every construct the parser reports: headings, marks, links, images, both kinds of
list, tasks, quotations, fences, a table, entities and raw HTML. A text block would
have eaten its backslashes, its backticks and the two trailing spaces that are a
hard break.

## Consequences

- **brd's editor is two nodes**, and its `body.html` is the same property through
  `MarkdownHtml`. The two cannot drift, because there is one source and one parse
  per frame.
- **An unbound `markdown-view` is unchanged.** The document component is still
  there and is what a bound view falls back to before its property answers — which
  is what a lenient inflater produces for a path nothing resolves yet (ADR-0062).
- **A rebuild per keystroke.** For a preview pane that is right; for a document of
  a hundred pages it is the widget count that bites first, and both numbers are in
  `book/src/TODO.md` rather than discovered later.
- **`markdown-view` cannot write.** `binding()` is an `Observable`, so the preview
  reads and the editor reports through an action — ADR-0063's rule, and the reason
  a document cannot quietly edit a model.
- **Nine screens, one digit left.** `Ctrl+1`…`Ctrl+0` still covers the gallery, and
  the tenth screen will be the one that has to argue for itself.

## Alternatives considered

- **A controller, like `toast`'s.** The editor holds one, the preview listens.
  That is the shape a toolkit without `bind=` would need; here it would be a
  second mechanism for what §9 already does, and every application that wanted a
  preview would write the same ten lines.
- **The application parses and passes a `Document`.** It is what ADR-0295 shipped
  and it still works — but for the live case it means the application subscribing,
  parsing and calling `setState`, which is the widget's own rebuild written out by
  hand.
- **Caching the parse by text identity.** A `WeakHashMap` keyed on the string, or
  the last (text, document) pair on the widget. Widgets are values rebuilt every
  frame, so the cache would have to be static or on the state — and it would save
  microseconds while the rebuild beside it costs milliseconds.
- **A `preview=` attribute on `text-area`.** The editor points at the view it
  drives. It reads well in markup and puts document rendering inside a form
  control, which is a dependency `:widgets` must not have.
