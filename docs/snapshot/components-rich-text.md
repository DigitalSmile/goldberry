# Parked for `book/src/components/text.md`: `rich-text` and `run`

Everything below the rule goes into the book at release. `BookTest` reads the
`## `name`` headings and the catalogue rows of this file as if they were in the
book until then.

Where each piece goes:

1. **The section**: in `book/src/components/text.md`, between the end of the
   `## `text`` section (after its `### Keyboard`) and `## `link``.
2. **In `## `text`` → `### Wrapping and cutting`**, the paragraph "Inline runs
   are not built. There is no `span` node, so a sentence with two styles is two
   `text` nodes in a `row`." is replaced by:

   > A sentence with two styles is a [`rich-text`](#rich-text): one paragraph of
   > runs, each styled by the cascade, wrapping as one text.

3. **In `book/src/components/index.md`**, the two catalogue rows, in
   alphabetical order: `rich-text` after `radio-group`, `run` after `row`:

   | [`rich-text`](text.md#rich-text) | Text and links |
   | [`run`](text.md#run) | Text and links |

   and the card's list becomes `text, link, rich-text, run`:

   <a class="gb-card" href="text.html"><strong>Text and links</strong><span>text, link, rich-text, run</span></a>

4. **In `book/src/overview/limitations.md`**, the `Rich text` row's note loses
   "A paragraph has one style." The rest of the row stands: Markdown emphasis is
   still a faux oblique, and `markdown-view` is still a row of words.
5. **The picture**: `rich-text-light.webp` and `rich-text-dark.webp` do not
   exist yet. `BookPicturesTest` takes them from the `kdl` sample once the
   section is in the book (`:example:test` with the golden update flag).

---

## `rich-text`

A `rich-text` is one paragraph made of runs, each in a style of its own. A
keyword can be gold and bold in the middle of a sentence, and the sentence still
wraps as one text.

<div class="gb-shot"><img class="gb-light" src="../images/rich-text-light.webp" width="300" alt="An ability's text: the keyword Bleeding in gold and bold in the middle of a wrapped sentence, and Order as a gold label before the rest"><img class="gb-dark" src="../images/rich-text-dark.webp" width="300" alt="An ability's text: the keyword Bleeding in gold and bold in the middle of a wrapped sentence, and Order as a gold label before the rest"><p>Keywords in the middle of a sentence.</p></div>

<div class="gb-tabs">

```kdl
column {
  rich-text {
    run "Give it "
    run "Bleeding" class="keyword"
    run " equal to the amount of boost it lost."
  }
  rich-text {
    run "Order" class="keyword"
    run ": Reset the power of a unit."
  }
}
```

```java
import dev.goldberry.widgets.text.RichText;
import dev.goldberry.widgets.text.Run;

new Column(
        new RichText(
                new Run("Give it "),
                new Run("Bleeding", Set.of("keyword")),
                new Run(" equal to the amount of boost it lost.")),
        new RichText(
                new Run("Order", Set.of("keyword")),
                new Run(": Reset the power of a unit."))
);
```

</div>

```css
rich-text > run.keyword { color: var(--gb-warning-text); font-weight: bold }
```

A `row` of `text` nodes does not do this. Each `text` wraps inside its own box,
so a sentence split across three of them wraps as three columns. A `rich-text`
shapes each run in its own font, joins them into one paragraph and breaks the
lines over the whole of it. A line can end in the middle of a run and the next
one start there.

A line is as tall as the tallest font on it: the deepest ascent above the
baseline and the deepest share below it. A run at a larger size makes its own
line taller and leaves the others alone. Runs that look like the paragraph add
nothing, so a `rich-text` whose runs are unstyled draws exactly what a `text`
with the same words draws.

### Attributes

| Attribute | Type | Default | What it does |
|---|---|---|---|
| children | `run` | none | the runs, in reading order; any other child is refused |
| `class` | string | none | CSS classes, space-separated |
| `id` | string | none | the node's id, which is also its reconciliation key |
| `tooltip` | string | none | text shown on hover |
| `context-menu` | string | none | the name of a menu a right-click opens |

A `rich-text` takes no argument. Its words are its runs, and an argument is
refused when the document inflates.

### Styling

- CSS type `rich-text`.
- Its children are `run` nodes, styled by the cascade like any child.
- No pseudo-classes of its own. It is not focusable and takes no hover.

The paragraph's `white-space`, `text-overflow`, `text-align`, `overflow-wrap`,
`word-break`, padding and background are the `rich-text`'s. `color`, the font
and `text-decoration` are each run's, inherited from the `rich-text` where a run
does not set them.

### Keyboard

None. A `rich-text` is not a Tab stop. Its accessible name is its runs' text end
to end, and its role is text.

### `run`

A `run` is one stretch of a `rich-text`: its argument is the words, spaces
included, and its classes are what the stylesheet styles it by. Runs are joined
as written, so the space between two words belongs to one of them.

```kdl
rich-text {
  run "Deal "
  run "3" class="number"
  run " damage."
}
```

| Attribute | Type | Default | What it does |
|---|---|---|---|
| argument | string | `""` | the words |
| `class` | string | none | CSS classes, space-separated |
| `id` | string | none | the node's id |

A run decides how its own words look: `font-family`, `font-size`,
`font-weight`, `font-style`, `color` and `text-decoration`. A run's
`white-space`, `text-align`, padding or background are read by nothing, because
a run has no box of its own inside the paragraph. A selector reaches it as a
child of its paragraph, `rich-text > run.keyword`, or by its classes alone.

Outside a `rich-text`, a `run` draws as a `text` with the same words would.

- CSS type `run`.
- No parts and no pseudo-classes.
