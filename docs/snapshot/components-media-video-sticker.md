Destination: `book/src/components/media.md`, a new `### A video sticker` after `### Bringing a codec` and before `` ## `media-player` ``; and one paragraph for the `AnimationView` section that `components-drawing-vector-animation.md` parks for `book/src/components/drawing.md`.

# Guide text held back: a video sticker

Two pieces, for the release commit. Each says where it goes.

## 1. `book/src/components/media.md`, after `### Bringing a codec`

### A video sticker

A WebM sticker is a short VP9 video with an alpha channel: each picture's alpha
travels beside it as a second VP9 stream, and the track says so with
`AlphaMode`. The built-in decoder decodes both, so the pictures are transparent
where the sticker is, and half transparent where it fades.

`VideoAnimation` plays one as a picture rather than as a player. It reads the
bytes from memory, decodes no sound, holds no thread, and is drawn by the same
`AnimationView` that plays a Lottie sticker:

```java
var sticker = VideoAnimation.of(bytes);
return new AnimationView(sticker, "🎉");
```

The view plays it on the frame loop, loops it until `loops(n)` says otherwise,
stands on its first picture with `autoplay(false)` or when the user asks for
less movement, and draws it over whatever is beneath it. Each picture is
decoded when the frame asks for it, on the UI thread, which costs about a
millisecond and a half for a 512×512 sticker. `close()` frees the decoders, and
an animation nothing refers to frees them by itself.

A `MediaPlayer` plays the same file with its alpha too. `video-view` draws a
picture with alpha on the CPU, over its own background, which `media.css` makes
black: a stylesheet that wants the page to show through sets
`background-color: transparent` on it. The GPU's video layer draws opaque
pictures only, and leaves these to the CPU.

## 2. The `AnimationView` section (parked in `components-drawing-vector-animation.md`)

After the paragraph that introduces `AnimationView`:

`AnimationView` plays any `MovingPicture`, the interface `VectorAnimation`
implements. `goldberry-media`'s `VideoAnimation` is the other one: a WebM
sticker, drawn with its transparency (see the media chapter).
