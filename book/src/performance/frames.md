# Keeping frames cheap

<p class="gb-lede">The toolkit retains, invalidates, culls and paces so that a settled frame costs microseconds, and ten habits keep an application from undoing it.</p>

By the end of this chapter you know which costs the toolkit has already
removed from a frame, and the handful of choices that are still yours: how
you rebuild, what you transition, when you restyle, how you show a long list,
and what the GPU and the native image change.

## What the toolkit does for you

Each row is one mechanism, the number it moved, and the record that measured
it. Nothing here needs a call from an application.

| Mechanism | What it does | Measured | Record |
|---|---|---|---|
| Retained render tree | Render objects keep their Yoga nodes between frames and are reconciled against each frame's description. Every Yoga setter is guarded by a comparison, because Yoga dirties a node when a style is set, not when it differs | Layout and walk at 960×640: 190 µs rebuilt every frame, 9.1 µs retained, 7.2 µs with a fresh box tree every frame | [ADR-0069](../adr/0069-the-render-tree-is-retained.md) |
| Cascade invalidation | A node's resolved style is cached on its element and checked by identity against the resolver and the inherited style. Invalidation is a subtree | Frame CPU before rasterization: 354 µs to 3.5 µs | [ADR-0070](../adr/0070-the-cascade-resolves-invalidated-nodes.md) |
| A rebuild is not a restyle | An identical widget stops the walk. A re-description that keeps `type`, `id` and `classes` keeps its subtree's styles | A wheel notch on the icon sheet: 1556 elements re-resolved became 4, and 66.6 ms of cascade became 4.2 | [ADR-0315](../adr/0315-a-rebuild-is-not-a-restyle.md) |
| Damage | A changed node damages where it was and where it is. The frame is painted only inside that union where the backend promises to keep last frame's pixels | One small box changed: 367 µs for a full repaint, 117 µs inside the damage | [ADR-0072](../adr/0072-a-partial-repaint-needs-a-promise.md) |
| Layers | A translucent group is rasterized once at full strength, untransformed, and its alpha and matrix go on the blit. A group that only fades or moves keeps its raster | A frame of a fade: 554 µs with the raster rebuilt, 199 µs reused | [ADR-0071](../adr/0071-a-layer-is-a-subtrees-raster.md), [ADR-0072](../adr/0072-a-partial-repaint-needs-a-promise.md) |
| Culling | Each render object carries the ink its subtree draws. A subtree whose ink cannot touch the clip is neither drawn nor walked | The icon sheet, 1544 tiles with forty visible: a settled frame 22.2 ms to 9.0 ms, the raster 18.0 ms to 4.4 ms | [ADR-0313](../adr/0313-a-frame-pays-for-what-is-on-screen.md) |
| Banded raster | Blend2D splits a frame into bands and up to four workers rasterize them on any surface over 400×300 | A live 960×640 frame: 2.856 ms synchronous, 2.146 ms with four workers. A 3840×2160 paint: 6.0 ms to 2.3 ms | [ADR-0042](../adr/0042-blend2ds-workers-and-how-many.md) |
| Pacing to the display | SDL is asked to hold each present until vertical blank. Where that is ignored, the loop paces itself to the rate read off the window's display | Present 5.51 ms to 1.20 ms, paint 2.25 ms to 1.61 ms, and 165 ms of each second in the frame path instead of 862 | [ADR-0047](../adr/0047-a-frame-nobody-sees-costs-full-price.md) |
| Idle loop | The loop parks until an event or a frame request. A static window costs nothing and nothing polls | A repaint request wakes the loop once, through a pushed event | [ADR-0024](../adr/0024-a-repaint-must-wake-the-loop.md) |
| Deep trees | A node copies the custom-property map only when it declares one | First-frame resolve at element depth 101: 2651 µs to 1168 µs | [ADR-0502](../adr/0502-a-node-copies-custom-properties-only-when-it-changes-one.md) |

Two numbers on that table are worth reading twice. The damage saving is
3.1× for a change covering 0.23% of the window, because the clip saves
rasterization and the walk still visits every box. And the retained tree
costs the same with a fresh box tree every frame as with nothing changed,
which is the row a real application lives on.

## What an application must do

### Rebuild cheaply

A widget is a value. Return a new tree from `build()` as often as you like,
because reconciling a fresh description against the retained tree costs
7.2 µs on a showcase-shaped screen
([ADR-0069](../adr/0069-the-render-tree-is-retained.md)). What is not free is
anything a build creates that has to be parsed, shaped or decoded. An `Icon`
is parsed from the bundled set and scaled to its size when it is built
([ADR-0043](../adr/0043-icons-are-stroked-paths.md)), a `Font` is one face
opened and kept
([ADR-0044](../adr/0044-one-face-many-sizes.md)), and an `Image` is decoded
once into pixels it owns. Build each in `Application.start` and keep it in a
field. The [start-up chapter](startup.md#what-an-application-keeps-out-of-start)
shows the shape.

A model write is cheap too. A woven field costs 2.5 ns to write with no
listeners and 12.9 ns with one
([ADR-0125](../adr/0125-a-raw-field-is-woven-into-a-binding.md)), and each
write asks for one frame. Set a field, and the toolkit rebuilds.

### Transition the properties that can be transitioned

Transitions are over a closed list: `opacity`, `background-color`,
`border-color`, `box-shadow`, `color` and `transform`
([ADR-0067](../adr/0067-motion-is-an-overlay-on-a-frame-clock.md),
[ADR-0068](../adr/0068-the-transform-stack-is-java-side.md)). A layout
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
`@Bind(restyle = true)` drops every resolved style first
([ADR-0133](../adr/0133-a-restyle-is-declared.md)). Declare it on the two
values a rule depends on, a theme and a density, and on nothing else:

```java
@Model
public final class Settings {
    @Bind(value = "app.theme", restyle = true) String theme = "nord-dark";
    @Bind("app.note") String note = "";
}
```

Why: a restyle re-resolves every element in the window, which is the 66.6 ms
cascade that
[ADR-0315](../adr/0315-a-rebuild-is-not-a-restyle.md) took a wheel notch out
of. A rebuild without a restyle re-resolves only the nodes whose selectors can
have changed.

### Virtualize a long list

A `list` or a `table` with thousands of rows builds only the rows its viewport
can see once it knows how tall a row is
([ADR-0213](../adr/0213-a-virtual-list-is-two-spacers-and-a-window.md)). The
height can come from the stylesheet's `--gb-list-row-height`, read when the
list is built
([ADR-0254](../adr/0254-a-build-may-ask-the-cascade-for-a-number.md)):

```java
ListView.of(names).virtualized()      // the stylesheet's row height
ListView.of(names).virtualized(32)    // a stated one
```

`Table` takes its `rowHeight` as a component, and zero builds every row. A
list whose rows vary in height must not virtualize, because the arithmetic is
index times height. A `masonry` cannot virtualize, which is why the icon sheet
relies on culling instead
([ADR-0313](../adr/0313-a-frame-pays-for-what-is-on-screen.md)). The
[collections chapter](../components/collections.md) has both widgets.

### Edit a large note in a `text-area`

A `text-area` shapes a line at a time and draws the rows on screen, so a
keystroke costs the same in a 500 kB note as in a 2 kB one
([ADR-0388](../adr/0388-a-note-is-shaped-a-line-at-a-time.md)). Medians per
keystroke, before and after that record:

| Note | Frame before | Frame after |
|---|---|---|
| 2 kB | 4.63 ms | 4.92 ms |
| 50 kB | 20.47 ms | 4.10 ms |
| 500 kB | 207.39 ms | 4.36 ms |

A keystroke into the 500 kB note shapes 2 021 characters against 1 941 for the
2 kB one. A `text` widget shapes its whole paragraph, which is right for a
label and wrong for a document. A `markdown-view` bound to the same note
keeps the blocks nobody typed in, and a 50 kB preview costs 16.83 ms a
keystroke where it cost 24.14 ms, while 500 kB is still over the 100 ms
preview budget
([ADR-0389](../adr/0389-a-block-nobody-typed-in-keeps-its-widget.md)).

### Ask for a frame only while something moves

The frame loop is idle when no animation is active. `WidgetRenderer.isAnimating()`
answers whether anything in the last rendered tree is still moving, and an
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
changes, and sixty of them a second is a fan running for nothing
([ADR-0024](../adr/0024-a-repaint-must-wake-the-loop.md)). An application on
the launcher gets this for free: a model write asks for a frame and a settled
tree asks for none
([ADR-0128](../adr/0128-a-change-is-its-own-frame-request.md)).

### Know that `opacity` on a group costs a layer

A node with `opacity` below one and children is rasterized into a layer of
its own and composited once
([ADR-0071](../adr/0071-a-layer-is-a-subtrees-raster.md)). That is what makes
`opacity` mean what CSS means where two children overlap, and it costs an
allocation and a blit. A translucent leaf keeps the cheap path on purpose. So
fade a panel, and do not fade every label in it separately.

### Move things with `transform`

A transform is applied to the blit, so a group that only moves keeps its
raster at 199 µs a frame instead of 554
([ADR-0072](../adr/0072-a-partial-repaint-needs-a-promise.md)). Scrolling is
a transform on one box, and the thousand boxes under it are skipped at the
first rectangle that held, so a scroll re-measures nothing
([ADR-0313](../adr/0313-a-frame-pays-for-what-is-on-screen.md)). Prefer a
transform to a changed position or size whenever the thing moving is the
same thing.

### Use the GPU path where it helps

With `goldberry-gpu` on the module path a window presents its CPU-painted
frame through the GPU, and falls back to the CPU wherever it cannot
([ADR-0480](../adr/0480-windows-present-through-the-gpu-by-default-and-on-the-cpu-where-it-cannot.md)).
Painting itself does not change. What changes is present, and video:

| | Through the GPU | Through the window surface | Record |
|---|---|---|---|
| Paint mean, showcase on an M1 Pro, 240 frames | 3.67 ms | 5.34 ms | [ADR-0480](../adr/0480-windows-present-through-the-gpu-by-default-and-on-the-cpu-where-it-cannot.md) |
| First frame on screen | 1016.9 ms | 1013.4 ms | [ADR-0480](../adr/0480-windows-present-through-the-gpu-by-default-and-on-the-cpu-where-it-cannot.md) |
| One minute of 4K60 VP9, pictures shown | 3600 of 3600 | 2018 of 3598 | [ADR-0485](../adr/0485-the-audio-clock-never-jumps-and-4k60-plays-every-picture.md) |
| UI thread per 4K picture shown | 2.8 ms | 17.6 ms | [ADR-0485](../adr/0485-the-audio-clock-never-jumps-and-4k60-plays-every-picture.md) |

It helps a window that shows video, a `canvas3d`, or a large surface. It costs
the device at the first frame, 20 ms on Metal and 190 ms on one NVIDIA
driver, and `-Dgoldberry.gpu=off` keeps every window on the CPU. The
[GPU chapter](../components/gpu.md) covers the widgets built on it.

### Keep a downcall handle a constant in a native image

This one is for anyone binding a native function of their own. In a native
image a `MethodHandle` to a foreign function is a direct call only when it is
a `static final` read by the method that invokes it. Twenty million calls
([ADR-0161](../adr/0161-a-downcall-handle-is-a-constant-or-it-is-not-a-call.md),
[ADR-0173](../adr/0173-a-bound-function-is-a-holder-and-its-handle-is-a-constant.md)):

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

Every number on this page was measured on one machine. The
[Measuring](measuring.md) chapter says how to take your own.
