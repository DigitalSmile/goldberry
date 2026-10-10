<!-- Three pieces, each says where it goes. -->

<!-- 1. Destination: book/src/guide/input.md, `## The cursor`. Replace the
     paragraph that begins "The cursor is a property of the painted
     rectangle" with the two below. -->

The cursor is a property of the painted rectangle, set from CSS or from
code, and it inherits down the stack of rectangles under the pointer rather
than the element tree. `cursor: pointer` on a button therefore covers the
label inside it. During a drag the shape stays the one the drag began with,
whatever is under the pointer. The one exception is the box holding the
pointer: when its own `cursor` changes in a frame painted during the drag,
the pointer takes the new shape. That is how a card that says `grab` shows
`grabbing` once it is lifted.

An application can draw its own shapes. `Application.cursors()` gives a shape
a picture at each size it was drawn, each with its hot spot:

```java
@Override public List<CursorImage> cursors() {
    return Stream.of(32, 48, 64, 96)
            .map(size -> new CursorImage(Cursor.GRAB,
                    Image.decode(read("cursors/grab-" + size + ".png")), size * 3 / 8, size / 8))
            .toList();
}
```

The shapes stay CSS's, so `cursor: grab` in a stylesheet and a link's own
`pointer` show the application's picture without naming it, and a shape with
no picture is the platform's. The smallest size is the shape at 100%, and the
toolkit shows the one the display's scale wants. Without a picture, `grab`
and `grabbing` fall back to `move`.

<!-- 2. Destination: book/src/overview/limitations.md, the row
     "Custom image cursors". Replace it with: -->

| Cursors from a stylesheet `url()` | <span class="gb-pill partial">deferred</span> | An application gives a shape its picture through `Application.cursors()`. `cursor: url(…)` is not read, and an animated cursor is not built |

<!-- 3. Destination: book/src/status.md, the line "Still to come: `select`
     and custom image cursors". Replace "and custom image cursors" with
     "and a cursor from a stylesheet `url()`". -->
