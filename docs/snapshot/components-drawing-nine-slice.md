<!-- Destination: book/src/components/drawing.md, `### The painter`, after the
     paragraph that ends "…The toolkit computes a dash itself and does not ask
     the rasterizer for one." -->

A picture cut in nine is drawn with `drawNineSlice`: its corners whole, its
edges and middle stretched or tiled between them, the way a stylesheet's
`border-image` draws a box's edge. The cut is a `NinePatch`, made once:

```java
private static final NinePatch PLATE = NinePatch.of(22, 64, 22, 64);   // top, right, bottom, left

frame.drawNineSlice(namePlate, PLATE, 0, 0, size.width(), 40);
frame.drawNineSlice(namePlate, PLATE.withWidths(8, 24, 8, 24).withRepeat(Repeat.ROUND, Repeat.STRETCH),
        0, 48, size.width(), 40);
```

The slice lines are in the picture's pixels, and `atDensity(2)` reads them in
a `@2x` picture's. Each side is drawn at its slice's size unless `withWidths`
says otherwise. When two opposite sides do not fit, all four shrink by the
same factor, so a corner keeps its shape. `withFill(false)` leaves the middle
out, and `withRepeat` tiles the edges and the middle, at their own scale
(`REPEAT`) or a whole number of times (`ROUND`). Every seam is on a whole
device pixel, so nothing behind shows between a corner and its edge.
