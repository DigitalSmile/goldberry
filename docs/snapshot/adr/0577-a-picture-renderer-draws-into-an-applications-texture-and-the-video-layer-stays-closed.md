# ADR-0577: A picture renderer draws into an application's texture, and the video layer stays closed

- **Status:** Accepted
- **Date:** 2026-10-08
- **Relates to:** the Gwent clone's issue list (GB-019, GB-020),
  [ADR-0484](0484-video-view-shows-its-pictures-through-a-gpu-layer-when-gpu-is-present.md),
  [ADR-0563](0563-a-texture-has-layers-levels-and-samples-and-a-pass-may-have-no-colour-target.md)

## Context

The downstream game plays up to twelve board videos at once and samples
each one in its card shader. It wants the twelve as layers of one
`Texture2DArray`, so the shader binds one texture and indexes it. With one
2D texture per slot, it binds twelve beside its other two, close to SDL's
sixteen per stage, and picks the slot with a `switch`.

Two things stood in the way. `VideoLayer.render(GpuFrame, GpuTexture)` drew
over level 0 of layer 0 only, although ADR-0563 had made a
`TextureView`, one level of one layer, a `RenderTarget` (GB-019). And the
mapping from `:media`'s `Picture` to `:gpu`'s `VideoImage` was
package-private in `GpuVideoPresenter`, so the game repeated it in a class
of its own, two exhaustive switches over the toolkit's enums with a test
to keep them in step (GB-020). The game reaches `VideoLayer` at all only
through `--add-exports`, because `dev.goldberry.gpu.video` is exported to
`dev.goldberry.media` alone (ADR-0484).

GB-020 asked for a public `VideoImage image(Picture)`. Any such method names
`VideoImage`, so it is only usable if `dev.goldberry.gpu.video` becomes API.

## Decision

**`dev.goldberry.gpu.video` stays a qualified export to `dev.goldberry.media`.**
`VideoLayer`, `VideoImage`, `PlaneLayout` and `ColorMatrix` do not become
API.

**`:media` gains `dev.goldberry.media.gpu.PictureRenderer`**, an exported,
`AutoCloseable` class whose signatures name only types an application
already reads:

```java
public void render(GpuFrame frame, Picture picture, RenderTarget target);
public void render(GpuFrame frame, Picture picture, PhysicalRect source, RenderTarget target);
public void close();
```

It owns a `VideoLayer`. It maps a picture once per new picture, telling
pictures apart by identity as the presenter does. It shows the picture,
or the `source` part of it, and renders into the target: a whole
`GpuTexture`, or a `TextureView`. Once closed it refuses to render.
The borrowing rule is `Picture`'s. A picture new to the renderer is uploaded
inside `render`, and the bytes are copied out before the call returns, so a
picture rendered when it is taken from the player is safe.

**`VideoLayer.render(GpuFrame, RenderTarget)`** draws over the target's
level and layer, at the level's size. The `GpuLayer` override delegates to
it. It refuses a target that is not `B8G8R8A8_UNORM` up front, rather than
leaving the refusal to the pipeline bind, which an unshown layer's clear
never reached.

**The mapping moves** into `dev.goldberry.media.view.gpu.Pictures`, public so
the new package can call it, in a package that is still not exported. The
presenter and the renderer both use it.

**`:media`'s module declaration** exports `dev.goldberry.media.gpu`, and
`requires static dev.goldberry.gpu` becomes `requires transitive static`,
as `org.jspecify` already is. Without `transitive`, `-Xlint:exports` under
`-Werror` rejects an exported signature that names `GpuFrame`. `static`
stays, so an application without `:gpu` still ships no GPU code. Only an
application that calls the new package loads it, and that application has a
`GpuFrame` and so has `:gpu`.

## Alternatives considered

- **Export `dev.goldberry.gpu.video` to everyone**, with a public
  `image(Picture)` in `:media` as GB-020 proposed. That puts four types and
  the layer's whole lifecycle into the public surface, for one caller that
  wants pictures in a texture. The plane layouts and matrices are the
  shader's vocabulary. Each would then be a compatibility promise, and an
  application would still need both modules' enums to keep in step.
- **Move the video types into `dev.goldberry.gpu`.** The same surface under
  another name, and it mixes the video layer into the general GPU API that
  `canvas3d` and every other application read.
- **Leave it to `--add-exports` downstream.** That is what the game does now.
  It is a command-line flag every launcher, test task and native-image
  build has to carry, and it reaches past encapsulation into types the
  toolkit can change in any release without notice.

## Consequences

- GB-019's `render(frame, texture.layer(3))` works through
  `PictureRenderer` with a `Picture`. Inside `:media` it also works on
  `VideoLayer`. The game deletes its own mapping class and its test, and
  holds one `PictureRenderer` per slot instead of a `VideoLayer`.
- The entries' proposed APIs are not what landed: there is no public
  `image(Picture)`, and no `VideoLayer` overload an application can call.
- `PicturesTest` checks every pixel format and colour matrix has its
  counterpart. `PictureRendererTest` checks one mapping per picture and a
  closed renderer. On a real device, `PictureRendererOnGpuTest` draws an
  NV12 picture into layer 2 of a four-layer array, the other layers keep
  their clear colour, and a source rectangle crops. `VideoLayerTest`
  covers a layer and a mip level directly. `PictureRendererModuleTest`
  compiles an application module that requires `:media` alone, with every
  lint on. It reads the renderer and `:gpu`'s frame and target, and is
  refused `dev.goldberry.gpu.video`.
- `:media:testWithoutGpu` now leaves out every `*OnGpuTest`, not only
  `VideoOnGpuTest`.
