# Split pane

<p class="gb-lede">Two children and a divider you can drag, with a position that survives a resize and minimums that do not.</p>

By the end of this chapter you can put a list beside a detail view, keep either side from being squeezed to nothing, let the user collapse one, and move the divider from the keyboard.

## `split-pane`

A `split-pane` lays out exactly two children along one axis with a draggable divider between them.

```kdl
split-pane id="demo-split" first-min=120 second-min=140 collapsible=#true {
    panel class="split-demo" {
        text class="caption" "The map"
        text "Tab to the divider and use the arrows, or Enter to collapse."
    }
    panel class="split-demo" {
        text class="caption" "The road"
        text "This one takes whatever is left."
    }
}
```

```java
var split = new SplitPane(map, road);
```

`new SplitPane(first, second)` is horizontal, starts in the middle, and keeps its own position. The controlled form takes the position from the application and reports a drag back to it.

```java
new SplitPane(SplitAxis.HORIZONTAL, position, this::setSplit, map, road)
```

The full constructor is `SplitPane(axis, position, onResize, firstMin, secondMin, collapsible, children, attributes)`. A list of any size other than two is an `IllegalArgumentException`. A three-way split is two split panes, one inside the other.

The position is a fraction and the minimums are pixels, deliberately. A divider a third of the way across stays a third of the way across when the window widens. A list that needs 160 points or its labels wrap needs them whatever the window does. The fraction is clamped against the pixels on every layout, which needs the pane's measured length, and that arrives once a frame through `Measured`.

The first pane is given its size in logical pixels and the second grows into the rest. Two `flex-grow`s would not do it, because `flex-grow` shares out what is left after content and would ignore the fraction.

### Attributes

| Attribute | Type | Default | What it does |
|---|---|---|---|
| `axis` | string | `horizontal` | `horizontal` is side by side with a vertical bar. `vertical` is stacked with a horizontal bar |
| `position` | number | `0.5` | Where the divider starts, as a fraction of the pane's length from 0 to 1 |
| `resize` | action name | none | An action that takes a number. Naming one makes the pane controlled, and `position` is then where the divider is rather than where it starts |
| `first-min` | number | `48` | The least the first child may be, in logical pixels |
| `second-min` | number | `48` | The least the second child may be |
| `collapsible` | boolean | `#false` | Whether dragging past a minimum collapses that child to nothing rather than stopping at it |
| `id` | string | none | The node's id, which lands on the `split-pane` node |
| `class` | string | none | Classes separated by spaces |

The default minimum is 48 points. The design system's hit-target floor is 32, and a pane squeezed to exactly that has no room for anything in it.

### Styling

| Node | What it is | In `controls.css` |
|---|---|---|
| `split-pane` | The whole pane | `align-items: stretch; flex-grow: 1; flex-shrink: 1` |
| `split-pane-side` | Each child's box | `flex-direction: column; overflow: hidden` |
| `split-divider` | The bar | 6 px thick, `flex-shrink: 0`, `--gb-border` |

The divider carries the axis as a class, `horizontal` or `vertical`, and `collapsed` when a side has gone to nothing. `split-divider:hover` and `split-divider:focus-visible` are both `--gb-accent`, on purpose: a divider has no state worth two appearances, and both mean "this is the thing you are about to move". The focus ring is turned off because a ring at the usual offset would be drawn across both panes. `split-divider.collapsed` takes `--gb-surface-2`.

Each side clips what is in it, so a pane dragged narrower than its content does not spill across the divider.

A split pane grows to fill what it is given. In a column that sizes to its content it needs a height from the stylesheet, which is why the showcase gives its card a definite one. The pane measures its own length and asks for a rebuild when it changes, and that value cannot feed itself because what the rebuild changes is the children.

```css
#demo-split { height: 220px; }
```

### Keyboard

The divider is the Tab stop. The pane itself is not focusable and neither side is a composite.

| Key | Moves |
|---|---|
| The arrows along the axis | 16 px |
| Page Up, Page Down | 64 px |
| Home, End | As far as the minimums allow |
| Enter, Space | Collapses and restores, when `collapsible` |

The arrows across the axis are left alone, so a horizontal split has nothing to say about Up and the key reaches whatever does.

### Read more

- [ADR-0165 A divider translates, and a rotation has three brakes](../adr/0165-a-divider-translates-and-a-rotation-has-three-brakes.md)
- [ADR-0297 An editor fills its pane, and a split knows its own width](../adr/0297-an-editor-fills-its-pane-and-a-split-knows-its-own-width.md)
- [ADR-0117 A widget may be told what it measured](../adr/0117-a-widget-may-be-told-what-it-measured.md)
