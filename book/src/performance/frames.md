# Keeping frames cheap

<p class="gb-lede">The toolkit retains, invalidates, culls and paces so that a settled frame costs microseconds, and ten habits keep an application from undoing it.</p>

By the end of this chapter you know which costs the toolkit removes from a
frame, and the handful of choices that are yours: how
you rebuild, what you transition, when you restyle, how you show a long list,
and what the GPU and the native image change.

## What the toolkit does for you

Each row is one mechanism and the number it moves. Nothing here needs a call
from an application.

| Mechanism | What it does | Scene | Measured |
|---|---|---|---|
| Retained render tree | Render objects keep their Yoga nodes between frames and are reconciled against each frame's description. Every Yoga setter is guarded by a comparison, because Yoga dirties a node when a style is set, not when it differs | Layout and walk at 960×640 | 9.1 µs retained and 7.2 µs with a fresh box tree every frame, against 190 µs rebuilt every frame |
| Cascade invalidation | A node's resolved style is cached on its element and checked by identity against the resolver and the inherited style. Invalidation is a subtree | Frame CPU before rasterization | 3.5 µs, against 354 µs with nothing retained or cached |
| A rebuild is not a restyle | An identical widget stops the walk. A re-description that keeps `type`, `id` and `classes` keeps its subtree's styles | A wheel notch on the icon sheet | 4 elements re-resolved against 1556, and 4.2 ms of cascade against 66.6 |
| Damage | A changed node damages where it was and where it is. The frame is painted only inside that union where the backend promises to keep last frame's pixels | One small box changed | 117 µs inside the damage, against 367 µs for a full repaint |
| Layers | A translucent group is rasterized once at full strength, untransformed, and its alpha and matrix go on the blit. A group that only fades or moves keeps its raster | A frame of a fade | 199 µs with the raster reused, against 554 µs rebuilt |
| Culling | Each render object carries the ink its subtree draws. A subtree whose ink cannot touch the clip is neither drawn nor walked | The icon sheet, 1544 tiles with forty visible | a settled frame 9.0 ms against 22.2, the raster 4.4 ms against 18.0 |
| Banded raster | Blend2D splits a frame into bands and up to four workers rasterize them on any surface over 400×300 | A live 960×640 frame, and a 3840×2160 paint | 2.146 ms with four workers against 2.856 ms synchronous, and 2.3 ms against 6.0 ms |
| Pacing to the display | SDL is asked to hold each present until vertical blank. Where that is ignored, the loop paces itself to the rate read off the window's display | Present and paint in a live window | present 1.20 ms against 5.51, paint 1.61 ms against 2.25, and 165 ms of each second in the frame path instead of 862 |
| Idle loop | The loop parks until an event or a frame request. A static window costs nothing and nothing polls | A repaint request | wakes the loop once, through a pushed event |
| Deep trees | A node copies the custom-property map only when it declares one | First-frame resolve at element depth 101 | 1168 µs, against 2651 µs with the map copied at every node |

Two numbers on that table are worth reading twice. The damage saving is
3.1× for a change covering 0.23% of the window, because the clip saves
rasterization and the walk visits every box regardless. And the retained tree
costs the same with a fresh box tree every frame as with nothing changed,
which is the row a real application lives on.

## What an application must do

### Rebuild cheaply

A widget is a value. Return a new tree from `build()` as often as you like,
because reconciling a fresh description against the retained tree costs
7.2 µs on a showcase-shaped screen. What is not free is anything a build
creates that has to be parsed, shaped or decoded. An `Icon` is parsed from
the bundled set and scaled to its size when it is built, a `Font` is one face
opened and kept, and an `Image` is decoded once into pixels it owns. Build
each in `Application.start` and keep it in a field. The
[start-up chapter](startup.md#what-an-application-keeps-out-of-start)
shows the shape.

A model write is cheap too. A woven field costs 2.5 ns to write with no
listeners and 12.9 ns with one, and each write asks for one frame. Set a
field, and the toolkit rebuilds.

### Transition the properties that can be transitioned

Transitions are over a closed list: `opacity`, `background-color`,
`border-color`, `box-shadow`, `color` and `transform`. A layout
property never transitions, and a declaration naming one is refused with a
warning rather than ignored:

```css
card { transition: box-shadow 160ms ease-out }   /* interpolates every component */
card { transition: width 160ms }                 /* refused, with a warning in the log */
```

Why: an animated value lives in an overlay applied at paint and is never
written back into computed style, so the cascade and the animation cannot
fight. Width would mean a layout pass per frame, which is the one cost the
retained tree exists to avoid. Animate a size with `transform` instead.

### Restyle for a theme, rebuild for everything else

A change to a model field asks for a frame. A change to a field declared
`@Bind(restyle = true)` drops every resolved style first. Declare it on the two
values a rule depends on, a theme and a density, and on nothing else:

```java
@Model
public final class Settings {
    @Bind(value = "app.theme", restyle = true) String theme = "nord-dark";
    @Bind("app.note") String note = "";
}
```

Why: a restyle re-resolves every element in the window, which is the 66.6 ms
cascade in the table above. A rebuild without a restyle re-resolves only the
nodes whose selectors can have changed.

### Virtualize a long list

A `list` or a `table` with thousands of rows builds only the rows its viewport
can see once it knows how tall a row is. The height can come from the
stylesheet's `--gb-list-row-height`, read when the list is built:

```java
ListView.of(names).virtualized()      // the stylesheet's row height
ListView.of(names).virtualized(32)    // a stated one
```

`Table` takes its `rowHeight` as a component, and zero builds every row. A
list whose rows vary in height must not virtualize, because the arithmetic is
index times height. A `masonry` cannot virtualize, which is why the icon sheet
relies on culling instead. The
[collections chapter](../components/collections.md) has both widgets.

### Edit a large note in a `text-area`

A `text-area` shapes a line at a time and draws the rows on screen, so a
keystroke costs the same in a 500 kB note as in a 2 kB one. Medians per
keystroke, with the whole note shaped and with one line:

| Note | Whole note shaped | A line at a time |
|---|---|---|
| 2 kB | 4.63 ms | 4.92 ms |
| 50 kB | 20.47 ms | 4.10 ms |
| 500 kB | 207.39 ms | 4.36 ms |

A keystroke into the 500 kB note shapes 2 021 characters against 1 941 for the
2 kB one. A `text` widget shapes its whole paragraph, which is right for a
label and wrong for a document. A `markdown-view` bound to the same note
keeps the blocks nobody typed in. A 50 kB preview costs 16.83 ms a keystroke,
against 24.14 ms with every block rebuilt, and a 500 kB preview costs more
than 100 ms.

### Ask for a frame only while something moves

The frame loop is idle when no animation is active. `WidgetRenderer.isAnimating()`
answers whether anything in the last rendered tree is moving, and an
application that drives its own loop asks for the next frame only then:

```java
window.onPaint(frame -> {
    BoxPainter.paint(frame, renderer.render(tree));
    if (renderer.isAnimating()) {
        window.repaint();
    }
});
```

Why: a request wakes the loop and paints a frame whether or not a pixel
changes, and sixty of them a second is a fan running for nothing. An
application on the launcher gets this for free: a model write asks for a
frame and a settled tree asks for none.

### Know that `opacity` on a group costs a layer

A node with `opacity` below one and children is rasterized into a layer of
its own and composited once. That is what makes
`opacity` mean what CSS means where two children overlap, and it costs an
allocation and a blit. A translucent leaf keeps the cheap path on purpose. So
fade a panel, and do not fade every label in it separately.

### Move things with `transform`

A transform is applied to the blit, so a group that only moves keeps its
raster at 199 µs a frame instead of 554. Scrolling is
a transform on one box, and the thousand boxes under it are skipped at the
first rectangle that held, so a scroll re-measures nothing. Prefer a
transform to a changed position or size whenever the thing moving is the
same thing.

### Use the GPU path where it helps

With `goldberry-gpu` on the module path a window presents its CPU-painted
frame through the GPU, and falls back to the CPU wherever it cannot.
Painting itself does not change. What changes is present, and video:

| | Through the GPU | Through the window surface | Scene |
|---|---|---|---|
| Paint mean | 3.67 ms | 5.34 ms | the showcase on Metal, 240 frames |
| First frame on screen | 1016.9 ms | 1013.4 ms | the showcase on Metal |
| Pictures shown | 3600 of 3600 | 2018 of 3598 | one minute of 4K60 VP9 |
| UI thread per picture shown | 2.8 ms | 17.6 ms | the same minute of 4K60 VP9 |

It helps a window that shows video, a `canvas3d`, or a large surface. It costs
the device at the first frame, 20 ms on Metal and 190 ms on NVIDIA's Vulkan
driver, and `-Dgoldberry.gpu=off` keeps every window on the CPU. The
[GPU chapter](../components/gpu.md) covers the widgets built on it.

### Keep a downcall handle a constant in a native image

This one is for anyone binding a native function of their own. In a native
image a `MethodHandle` to a foreign function is a direct call only when it is
a `static final` read by the method that invokes it. Twenty million calls:

| Handle | JVM | Native image |
|---|---|---|
| Bound to its address, built at run time | 10 ns | 4560 ns |
| Unbound, built at run time | 10 ns | 4500 ns |
| Unbound, built at image build time | 10 ns | 10 ns |

Passing the constant into a helper as an argument costs 810 ns a call against
8.9 ns when the helper names the field itself, and an instance field measures
4540 ns. For the toolkit's own bindings the difference is a showcase frame at
42.5 ms or at 1.0 ms, and
[Native image](../native.md#the-one-flag-the-frame-rate-depends-on) says which
flag makes the difference.

> [!WARNING]
> An image with the wrong handle shape builds, runs and paints correctly. It
> is only forty times slower. Measure the frame, because nothing else will
> tell you.

The [Measuring](measuring.md) chapter says how to take your own numbers.
