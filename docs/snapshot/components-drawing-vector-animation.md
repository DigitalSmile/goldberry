<!-- Destination: book/src/components/drawing.md, under `## `image``, a new
     `### Vector animations` section after `### Keyboard` and before
     `## `qr-code``. -->

### Vector animations

A Lottie document, the format of a Telegram `tgs` sticker, is a description
rather than a picture: shapes, colours and easing curves. `VectorAnimation`
reads one, gzipped or plain JSON, and draws it at whatever size it is asked
for. `AnimationView` plays one on the frame loop, the way `image` shows a
picture.

```java
import dev.goldberry.image.anim.VectorAnimation;
import dev.goldberry.widgets.core.image.AnimationView;

var sticker = VectorAnimation.of(Files.readAllBytes(path));       // .tgs or .json
new AnimationView(sticker, "🎉");
AnimationView.decorative(sticker.loops(1)).autoplay(false);

Image still = sticker.imageAt(500, 128, 128);                      // half a second in
new Canvas((frame, size) -> sticker.paint(frame, elapsed, 0, 0, size.width(), size.height()));
```

The view starts from the first frame when it is first drawn, and asks for
frames only while the animation moves. With `autoplay(false)`, or when the
platform says its user wants less movement, it shows the first frame and the
loop stays idle. An animation plays for ever unless `loops(n)` says otherwise,
and then holds its last frame. Its CSS type is `image`, it is sized like an
image (the animation's canvas in logical pixels until a stylesheet says
otherwise), and it takes the same `Fit` and the same alt-text rule.

What is drawn is the subset Telegram allows in a sticker, and somewhat more:
shape, null, solid and precomp layers with parents; groups and their
transforms; rectangles, ellipses, stars, polygons and paths; flat and gradient
fills and strokes, dashed or not; trims, mattes and masks; held, eased and
curved keyframes, and paths that change shape. A document with an expression or
an image layer in it is refused with an `ImageDecodeException`, as bytes that
are not a Lottie document are. Anything else unknown, such as a text layer or a
layer effect, is passed over, and the rest is drawn.

There is no markup node for an animation yet: the document is bytes the
application has, not a path a page names.
