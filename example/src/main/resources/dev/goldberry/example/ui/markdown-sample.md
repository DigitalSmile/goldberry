# A Markdown sample

Everything below is **live**. Type in the pane on the left and this side is the
next frame: the editor writes one property and the preview follows it, which is
all `bind=` means.

## Text, and what marks it

Prose with *emphasis*, **strength**, ***both at once***, ~~a change of mind~~ and
`inline code`. Emphasis leans because the toolkit ships two upright faces, so it
is a faux oblique rather than an italic — `markdown.css` says so in a comment you
can override.

A soft break in the source
is a space on the screen, because that is what it means.
Two spaces at the end of a line  
are a hard break, which the HTML writer emits as `<br>`.

Escapes work: \*this is not emphasis\*, and neither is a lone \_underscore\_.

Entities are resolved on the way in — AT&amp;T, &copy; 2026, an em&#8212;dash and
a non&nbsp;breaking space — so the model holds characters a reader can read.

### Links and pictures

An [inline link](https://example.com), one [with a title](https://example.com "the title"),
and a bare URL that the GitHub dialect finds by itself: https://goldberry.dev —
as well as an address like frodo@bagend.shire.

![A picture of the Sea](sea.png "Away west")

The picture above is **drawn**, because this application told the view where to
find it: `images=` names an `ImageSource`, and what a `src` means — a path, a key,
a URL nothing here may fetch — stays the application's. A source that has nothing
for a name gets the alt text instead, which is what ![this one](missing.png) is.

The links are **pressable**, and the line above this pane says what was handed
over. Following one is the application's decision too: nothing here opens a
browser.

#### The fourth level

##### The fifth

###### And the sixth, which is the last one CommonMark has

## Lists

A tight list, which is the one with no blank lines in it:

- Bread
- Cheese
- Something to carry it in

A loose list, which is the same list with air in it:

- The first item, which has room to breathe.

- The second, and a renderer draws these further apart because the author asked.

Numbered, and starting where it says rather than at one:

7. Seven
8. Eight
9. Nine

Nested, because a list holds blocks and a block can be a list:

- The Shire
    - Hobbiton
    - Bywater
- Bree
    1. The Prancing Pony
    2. The road east

And a task list, which is GitHub's — **tick one**. The box reports which task it
is, the application rewrites that one character of the source on the left, and the
preview re-parses because it is bound to it:

- [x] Parse it natively
- [x] Render it as ordinary widgets
- [x] Do the same for HTML, in the tab next door
- [x] Make a link followable, and a box tickable
- [x] Select text across two of them, and copy it
- [ ] Draw either with litehtml, for a line of mixed faces as one shaped run

## Quotations

> A quotation holds **blocks**, not words, which is why this one can hold a list:
>
> - and does
> - twice
>
> > A quotation inside a quotation is one more level of the same thing.

## Code

A fence with a language, which is what a highlighter reads:

```java
var document = Markdown.parse(source);           // one downcall
var html = MarkdownHtml.of(document);            // a fold over the tree
var view = MarkdownView.following(model.source());
```

A fence with none:

```
$ ./gradlew :example:run
```

And an indented block, which is the other way to write one:

    goldberry_md_parse(text, size, flags);
    goldberry_md_free(handle);

Long lines in a fence are clipped rather than wrapped, because a wrapped program has lines nobody wrote.

## A table

| What            | `markdown-view` | `html-view` |
|:----------------|:---------------:|------------:|
| Followable links |       yes      |         yes |
| Images          |       yes       |         yes |
| Tickable tasks  |       yes       |          — |
| Text selection  |       yes       |         yes |

---

## Raw HTML

Markdown lets an author write markup, so <kbd>Ctrl</kbd>+<kbd>T</kbd> is an
inline one. A widget renderer has no engine under it, so it shows the markup as
the text it is — honestly, rather than by pretending to have drawn it:

<div class="not-rendered">A block of HTML.</div>

That is every construct the parser reports. The thematic break above is one too.
