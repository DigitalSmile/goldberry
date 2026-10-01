# Stack

<p class="gb-lede">Children drawn on top of one another. The first child gives the stack its size, and every child after it floats over it.</p>

By the end of this chapter you can put a badge on a corner of an avatar, an overlay over a picture, and know where an overlay lands when nothing says.

## `stack`

A `stack` keeps its first child in flow and takes every later child out of flow, so adding an overlay cannot move or resize the thing it sits on.

```kdl
stack id="avatar" {
    panel class="portrait" { text "GB" }
    badge class="info" "3"
}
```

```java
var avatar = new Stack(
        new Panel(new Text("GB")),
        new Badge("3"))
        .withAttributes(Attributes.NONE.id("avatar"));
```

`new Stack(Widget...)` and `new Stack(List<Widget>, Attributes)` are the two constructors. A stack of one child is that child in a box.

Later children draw over earlier ones. That is the painter's rule for siblings, and a stack adds nothing to it.

### Attributes

| Attribute | Type | Default | What it does |
|---|---|---|---|
| `id` | string | none | The node's id |
| `class` | string | none | Classes separated by spaces |

### Styling

The CSS type is `stack`. It has no parts, no pseudo-classes of its own, and no rule in `controls.css`.

`render` sets `position: absolute` on every child after the first. Where an absolute child lands is the stylesheet's, and there are two ways to say it.

With no inset, the child is placed by the stack's `align-items` and `justify-content` and by its own `align-self`. A badge goes to a corner with two declarations and no arithmetic.

```css
#avatar { align-items: flex-start; justify-content: flex-start; }
#avatar badge { align-self: flex-end; }
```

With an inset, the child goes where the inset says. Zero pins it to the stack's edge.

```css
#avatar badge { top: 0; right: 0; }
```

The difference between "no inset" and "an inset of zero" only shows on an absolute child, and `stack` is the widget that shows it. An inset is measured from the stack's border box rather than its padding box. That is Yoga's rule, and [ADR-0265](../adr/0265-yoga-measures-an-inset-from-the-border-box.md) explains it.

### Read more

- [ADR-0250 A stack is one child in flow and the rest over it](../adr/0250-a-stack-is-one-child-in-flow.md)
- [ADR-0244 A child may say where it sits](../adr/0244-a-child-may-say-where-it-sits.md)
- [ADR-0099 An indicator travels on a grid](../adr/0099-an-indicator-travels-on-a-grid.md)
- [ADR-0265 Yoga measures an inset from the border box](../adr/0265-yoga-measures-an-inset-from-the-border-box.md)
