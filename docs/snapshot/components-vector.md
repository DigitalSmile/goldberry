# SVG in the Drawing chapter

At release, replace the last sentence about SVG under `## image` with the
paragraph below. Insert the new section before `## qr-code`. Add `svg-view`
after `image` in the Drawing card in `components/index.md` and add its row
in alphabetical order.

| [`svg-view`](drawing.md#svg-view) | Canvas, images and QR codes |

---

SVG decodes with `goldberry-vector` on the module path and its native
library for the platform. The image has the document’s natural dimensions.
A larger box or a higher display scale requests a raster at the new device
size. This also applies to CSS `url()` images and to images in HTML and
Markdown. In these contexts `currentColor` defaults to black.

## `svg-view`

Use `svg-view` for a diagram or icon whose ink follows the surrounding text.
This leaf takes its fill from the view’s CSS `color`. Changing the theme or
a `:hover` rule updates the drawing. Colours written directly in the SVG
keep their values.

```kdl
svg-view alt="Leaf" "<svg viewBox='0 0 48 48'><path fill='currentColor' d='M6 41Q5 9 41 6Q42 40 11 37Z'/></svg>"
```

A `src` can name a file or a classpath resource. Supply either the inline
document or the source. Give each figure alt text describing its content.
Use `decorative=#true` when the surrounding text already conveys it.

<div class="gb-tabs">

```kdl
svg-view src="classpath:/art/hills.svg" alt="Sun over two hills" fit="contain"
```

```java
import dev.goldberry.vector.view.SvgSource;
import dev.goldberry.vector.view.SvgView;

var hills = new SvgView(SvgSource.resource(App.class, "/art/hills.svg"), "Sun over two hills");
```

</div>

If the resource belongs to a package in a named module, open that package
to `dev.goldberry.vector` for the Java anchor. The `classpath:` form uses
the class loader, which needs that package to be open unconditionally.

Add `dev.goldberry:goldberry-vector` and its matching `natives-<target>`
classifier to the application. For example, on Linux x64 the classifier is
`natives-linux-x64`. A modular application requires `dev.goldberry.vector`
and grants it native access with `--enable-native-access=dev.goldberry.vector`.
Add `VectorStyles.stylesheet()` beside the controls’ stylesheets to style
the loading placeholder and the error icon and alt text.

A Gradle build using the Goldberry BOM can declare the Linux x64 dependencies together.

```groovy
implementation 'dev.goldberry:goldberry-vector'
runtimeOnly 'dev.goldberry:goldberry-vector::natives-linux-x64'
```

CSS `width` or `height` sets one dimension and the other follows the
document’s proportions. With both set, `contain` fits the whole drawing,
`cover` crops to the box, `none` keeps the natural size and `fill` gives
the document that viewport, respecting its own `preserveAspectRatio`.

Paths, gradients, clips, masks, references within the document and embedded
PNG or JPEG data URIs are supported. Convert lettering to paths when it
must appear in the artwork. SVG text, external references, scripts and
animation are unsupported. Documents are limited to 16 MiB and a raster
to 8192 pixels per side and 64 million pixels overall.
