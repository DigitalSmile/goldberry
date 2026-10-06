# Sizing with CSS

<p class="gb-lede">Every layout property the CSS subset accepts, the units a length may be in, how a percentage resolves, and the tokens that move a control's size.</p>

By the end of this chapter you can write a stylesheet that sizes a tree, know which declarations the engine drops, and switch a whole application to compact density with one argument.

## The subset

The engine applies these layout properties and drops every other one. A dropped declaration is not an error. The engine logs it and carries on. `StyleLint` reports every dead declaration in a sheet, and the toolkit's own sheets have none.

| Property | Values |
|---|---|
| `flex-direction` | `row`, `row-reverse`, `column`, `column-reverse` |
| `justify-content` | `flex-start`, `center`, `flex-end`, `space-between`, `space-around`, `space-evenly` |
| `align-items`, `align-self` | `auto`, `flex-start`, `center`, `flex-end`, `stretch`, `baseline` |
| `align-content` | The same words, and `space-between`, `space-around`, `space-evenly` for wrapped lines |
| `flex-wrap` | `nowrap`, `wrap`, `wrap-reverse` |
| `width`, `height` | A length, a percentage, or `auto` |
| `min-width`, `max-width`, `min-height`, `max-height` | A length or a percentage |
| `padding`, `padding-top`, `padding-right`, `padding-bottom`, `padding-left` | One to four lengths, no `auto` |
| `margin`, `margin-top`, `margin-right`, `margin-bottom`, `margin-left` | One to four lengths, `auto`, negatives allowed |
| `gap` | One length, for both axes |
| `flex-grow`, `flex-shrink` | A number of zero or more |
| `flex-basis` | A length, a percentage, or `auto` |
| `position` | `static`, `relative`, `absolute` |
| `inset`, `top`, `right`, `bottom`, `left` | One to four lengths |
| `overflow` | `visible`, `hidden`, `scroll`, `auto` |

`start` and `end` are accepted for `flex-start` and `flex-end` wherever an alignment keyword is. `left` and `right` are not, because they are not the same under right-to-left text. A shorthand with one bad part is dropped whole, so `padding: 8px nonsense` applies to no edge rather than two.

Not in the subset: `display`, `box-sizing`, `aspect-ratio`, `row-gap` and `column-gap`, `calc()`, grid, and `position: sticky`. A sticky header is an [affix](affix.md). A value the engine cannot express is dropped rather than approximated.

## Units

| Unit | Meaning |
|---|---|
| `px` | A logical pixel. The display scale is applied once, below the stylesheet |
| `%` | A percentage of the parent's matching dimension |
| `em` | The element's own computed font size |
| `rem` | The root element's computed font size |
| `0` | Unitless zero is accepted. Any other unitless number is dropped |
| `auto` | Where the property takes it: `width`, `height`, `margin`, `flex-basis` and `align-self` |

`em` and `rem` are resolved when the declaration is read, because Yoga has no concept of a font size. `em` is the element's own computed size and `rem` is the root element's. `vh`, `vw`, `pt` and `cm` are not units here.

A percentage width is of the parent's width, a percentage height of its height, and a percentage `flex-basis` of the parent's main axis.

## Direction and gap

<div class="gb-tabs">

```kdl
column id="form" {
    row class="field-row" { text "Name"; text-input }
    row class="field-row" { text "Email"; text-input }
}
```

```css
#form { gap: 12px; padding: 16px; }
.field-row { gap: 8px; align-items: center; }
```

</div>

`gap` is one length and applies between rows and between columns alike. A `row` and a `column` ignore `flex-direction`. On any other box, a `panel` or a `card`, the stylesheet chooses it.

## Padding and margin

<div class="gb-tabs">

```kdl
panel id="dialog" {
    text "Delete this file?"
    row class="actions" {
        spacer
        button press="dismiss" "Cancel"
        button class="danger" press="delete" "Delete"
    }
}
```

```css
#dialog { padding: 16px 24px; margin: 0 auto; }
.actions { gap: 8px; margin-top: 16px; }
```

</div>

The shorthand is CSS's: one value is every edge, two are vertical and horizontal, three add a bottom, and four go clockwise from the top. `margin: 0 auto` centres a box on the main axis of a container it does not control. `align-self: center` centres on the cross axis. Negative margins pull a box over its neighbour and are not clamped.

## Alignment

<div class="gb-tabs">

```kdl
row id="status" {
    text "Listening on 8080"
    spacer
    badge class="success" "Live"
}
```

```css
#status { align-items: center; justify-content: space-between; }
#status badge { align-self: flex-start; }
```

</div>

`justify-content` places children along the main axis and `align-items` across it. `align-self` is one child's answer to `align-items`, and `align-self: auto` is a real declaration that undoes a more general rule.

## Wrapping

<div class="gb-tabs">

```kdl
row id="tags" {
    chip "Unread"
    chip "Starred"
    chip "Archived"
    chip "Shire"
    chip "Rivendell"
}
```

```css
#tags { flex-wrap: wrap; gap: 8px; align-content: flex-start; }
```

</div>

`flex-wrap: wrap` lets a row break into lines. `align-content` places the lines across the container. Wrapped lines share a cross axis.

## Growing, shrinking and the basis

<div class="gb-tabs">

```kdl
row id="editor" {
    column id="files" { text "main.java"; text "Window.java" }
    column id="source" { text "// the file" }
}
```

```css
#files { flex-basis: 220px; flex-shrink: 0; }
#source { flex-grow: 1; flex-basis: 0; }
```

</div>

`flex-grow` shares out the space left after content. `flex-shrink` is 1 by default, which is CSS's rule and the reason every control in `controls.css` declares `flex-shrink: 0`. `flex-basis: 0` with `flex-grow: 1` is a fixed share of the row, counted after the gaps, which is how a masonry's columns are equal without anybody counting them. `flex-basis: auto` asks `width` or the content.

## Width, height and limits

<div class="gb-tabs">

```kdl
card id="note" {
    text "A card no wider than a readable line and no narrower than its title."
}
```

```css
#note { width: 50%; min-width: 320px; max-width: 640px; }
```

</div>

`width` and `height` are preferred sizes. A cramped row shrinks a box below its `width` unless `flex-shrink` is 0 or `min-width` holds it. "No limit" is undefined rather than zero, because a maximum of zero is a box that may not exist.

## Position, inset and overflow

<div class="gb-tabs">

```kdl
stack id="portrait" {
    panel class="picture" { text "GB" }
    badge "3"
}
```

```css
#portrait badge { position: absolute; top: 4px; right: 4px; }
.picture { overflow: hidden; }
```

</div>

An absolute child is placed against the nearest ancestor that is not `static`, from its border box. `inset` takes the same one-to-four shorthand as `padding`. `overflow: hidden` clips. `scroll` and `auto` size the same as `hidden` and differ only in whether a `scroll` viewport offers bars. A `stack` sets `position: absolute` on its later children itself.

## Density and the size tokens

A control's height is the one metric that is a token rather than a literal, because the design system says a user preference moves it. Padding, gap and radius stay literal. The compact density is three custom properties and no rules, in the same cascade layer as a theme, and regular ships no stylesheet at all because it is what `controls.css` already is.

```java
var sheets = Controls.stylesheets(Theme.NORD_DARK, Density.COMPACT);
```

| Token | Regular | Compact | Read by |
|---|---|---|---|
| `--gb-control-height` | `32px` | `28px` | Every control's height, through `--gb-button-height`, `--gb-input-height` and the rest |
| `--gb-list-row-height` | `32px` | `26px` | `list`, `tree`, a `select`'s rows |
| `--gb-table-header-height` | `36px` | `30px` | `table` |
| `--gb-code-box-width`, `--gb-code-box-height` | `40px`, `48px` | `36px`, `44px` | `code-input` |
| `--gb-calendar-day` | `32px` | `28px` | The date picker's day cell |

The other size tokens a layout reads do not move with density.

| Token | Value | Read by |
|---|---|---|
| `--gb-field-label-width` | `120px` | A `field`'s label column |
| `--gb-scroll-line` | `20px` | A `scroll`, as one wheel line and one arrow key |
| `--gb-scrollbar-gutter` | `0px`, `12px` with `Scrollbars.ALWAYS` | The room a `scroll` reserves beside its content |
| `--gb-scrollbar-size` | `10px`, `12px` with `Scrollbars.ALWAYS` | The bar's thickness |

A widget of your own that reads `var(--gb-control-height)` is compact-aware with no code. One that writes `32px` cannot be made so by anything in the density sheet.

An application's own sheet goes after the toolkit's, in the application layer, and is written the same way.

```java
var sheets = new ArrayList<>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
sheets.add(Stylesheet.resource(CascadeLayer.APPLICATION, App.class, "app.css"));
```

How the layers cascade and what else a sheet may say is in [Styling](../guide/styling.md).
