# A page's own styles

Held back until the release (ADR-0710). Three pieces, all in
`book/src/components/content.md`, under `## html-view`. The third edits the
paragraph `components-content-emphasis.md` also edits: apply that one first.

## 1. `### Attributes`, a row after `images`

| `author-styles` | `ignored`, `theme-colours`, `adapted` or `as-written` | `adapted` | What is done with the page's own `<style>` and `style=`; see [A page's own styles](#a-pages-own-styles) |

## 2. A new `### A page's own styles`, after `### Styling` and before `### Keyboard`

### A page's own styles

A page's `<style>` blocks and `style=` attributes are applied to that page and
to nothing else in the window, and so are the `bgcolor` and `<font color>` of
mail written before CSS. A rule a mail writes cannot reach the application
round it or the mail beside it.

```kdl
html-view bind="mail.body" link="mail.open" author-styles="adapted"
```

```java
HtmlView.following(mail.body()).authorStyles(AuthorStyles.THEME_COLOURS);
```

What is read is the part that is safe and that the toolkit's CSS has: colour,
the text's size, weight, style, spacing, alignment, decoration and case, a
box's padding, margin, border, corners and `max-width`, `opacity`,
`visibility`, and `display: none`, which is how a mail hides its preview line.
Nothing that fetches is read (`url()` included), nothing that moves a box out
of the page (`position`, `transform`, a negative margin), nothing that reads
or sets the theme (custom properties and `var()`), and no `font-family`, so a
mail reads in the application's own face. `@media` and every other at-rule are
skipped.

Selectors are matched against the page as written, so `ul > li`, `td a` and
`p:first-child` mean what they say. `:hover`, `:active` and `:focus` apply as
the pointer and the keyboard move. A page's rules win over the application's
own sheets for the page's elements; an application that has to insist writes
`!important`.

`author-styles=` decides what happens to the colours:

| Value | What the page looks like |
|---|---|
| `adapted` | The default. A background the page paints is kept, and text on it reads: the page's own colour when it reaches 4.5:1 against that background, dark or light ink when it does not. A text colour with no background of the page's under it is the theme's, because it would sit on the theme's surface |
| `theme-colours` | Sizes, spacing, boxes and borders as written, every colour the theme's |
| `as-written` | Exactly as written, for a surface the application knows to be white |
| `ignored` | The application's stylesheets alone, as a help page should be |

Inline styles follow the text run. Its background covers the glyph range on
each line. Paragraph boxes support padding.

## 3. `### What it does not do`

Replace the sentence "A `<style>` block is kept in the model and applied by
nothing, because the cascade a page is under is the application's stylesheets,
which is what makes it follow the theme." with:

A page's own styles are a subset, applied to that page alone: see
[A page's own styles](#a-pages-own-styles).
