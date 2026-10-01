# Spacer

<p class="gb-lede">Empty space that takes whatever its row or column has left over.</p>

By the end of this chapter you can push a button to the far end of a toolbar, centre a label between two things, and know the one thing a spacer cannot do.

## `spacer`

A `spacer` is a box that grows into the free space of its container.

<div class="gb-shot"><img class="gb-light" src="../images/spacer-light.webp" width="639" alt="A title bar: Goldberry on the left and a Theme button on the right"><img class="gb-dark" src="../images/spacer-dark.webp" width="639" alt="A title bar: Goldberry on the left and a Theme button on the right"><p>The spacer takes the room between.</p></div>

<div class="gb-tabs">

```kdl
row id="title-bar" {
    text "Goldberry"
    spacer
    button press="theme" "Theme"
}
```

```java
var bar = new Row(
        new Text("Goldberry"),
        new Spacer(),
        new Button("Theme", this::theme)
).withAttributes(Attributes.NONE.id("title-bar"));
```

</div>

`new Spacer()` is the whole constructor. `new Spacer(Attributes)` gives it an id and classes.

Two spacers share the free space equally, so a label between two of them sits in the middle.

```kdl
row {
    spacer
    text "Nine set out from Rivendell"
    spacer
}
```

### Attributes

| Attribute | Type | Default | What it does |
|---|---|---|---|
| `id` | string | none | The node's id |
| `class` | string | none | Classes separated by spaces |

### Styling

The CSS type is `spacer`. It has no parts and no pseudo-classes, and `controls.css` ships no rule for it.

A spacer grows by 1 unless the stylesheet gives it a `flex-grow` of its own. A spacer with `flex-grow: 2` takes twice the share of its siblings.

```css
#title-bar spacer.wide { flex-grow: 2; }
```

A spacer cannot be told not to grow. `flex-grow: 0` is the computed value when nothing was declared, so `render` reads it as unset and grows by 1 anyway. A fixed gap between two neighbours is `gap` on the container or `margin` on one of them, not a spacer with a width.

### Read more

- [ADR-0311 `margin` is room outside, and `auto` is the half that mattered](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0311-margin-is-room-outside-and-auto-is-the-half-that-mattered.md)
- [ADR-0373 A column starts from nothing and grows](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0373-a-column-starts-from-nothing-and-grows.md)
