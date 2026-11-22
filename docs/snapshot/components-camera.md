# Parked for `book/src/components/camera.md`: a new chapter

Everything below the rule goes into the book at release. `BookTest` reads the
`## `name`` headings and the catalogue rows of this file as if they were in the
book until then.

Where each piece goes:

1. **The chapter**: everything below the rule becomes
   `book/src/components/camera.md`, listed in `book/src/SUMMARY.md` after
   `components/microphone.md`. Doc comments in `dev.goldberry.camera` link
   `components/media.html` until it moves; point them at
   `components/camera.html` then.
2. **In `book/src/components/index.md`**, a card after the microphone's:

   <a class="gb-card" href="camera.html"><strong>Camera</strong><span>camera-view</span></a>

   and the catalogue row, in alphabetical order, before `canvas`:

   | [`camera-view`](camera.md#camera-view) | Camera |

---

# Camera

`goldberry-camera` lists the cameras there are and takes pictures from them.
A camera is a picture source, so it is drawn as any other is, and
`camera-view` adds what a camera needs on top: what to say while there is no
picture, and a mirror. It is an optional module with no native library of its
own, and it brings `goldberry-media` with it:

```kotlin
dependencies {
    implementation("dev.goldberry:goldberry-camera")
}
```

## Opening a camera

```java
try (var cameras = Cameras.open();
        var camera = cameras.open(CameraSpec.CALL)) {
    window.show(new CameraView(camera));
    ...
}
```

`Cameras.list()` asks the system for the cameras plugged in now, each with
its name, where it faces, and the modes it offers. `preferred()` is the one to
open when the application has not chosen: the first facing the user, else the
first. `onChange` hears cameras plugged in and taken away while the
application's window loop runs.

A camera opens in the **uncompressed** mode nearest to what was asked. Most
USB cameras send their largest sizes and fastest rates only compressed, as
Motion JPEG; those modes are listed and not opened. A rate slower than the one
asked counts twice as much as a size away from it, so a camera that sends
1280×720 uncompressed only at 10 frames a second opens 640×480 at 30 for
`CameraSpec.CALL`.

Pictures come as NV12 planes, newest first, taken on a thread of the camera's
own. A picture stays as it is until two more have been shown after it; a view
that draws slowly never holds the camera up.

| Status | Means |
|---|---|
| `Asking` | The system is asking the user; no pictures yet |
| `Active` | Taking pictures |
| `Denied` | The user refused; final |
| `Lost` | The camera was unplugged or stopped; final |
| `Closed` | Closed; final |

On macOS the application's bundle needs an `NSCameraUsageDescription`
string, and the first camera opened asks the user.

## `camera-view`

A camera's pictures, or a line saying why there are none.

```kdl
camera-view camera="front" fit="cover"
camera-view camera="front" mirror="off"
```

```java
new CameraView(camera, Fit.CONTAIN, Mirror.OFF, Attributes.NONE)
```

| Attribute | Means |
|---|---|
| `camera` | The camera to show, a named object; a name nothing is bound to shows "No camera" |
| `fit` | `cover` (the default), `contain`, `fill` or `none`, as `video-view` takes it |
| `mirror` | `auto` (the default) mirrors unless the camera faces away; `on`; `off` |

While the camera is active its pictures are drawn as `video-view` draws a
player's: on the GPU where the window has one, and converted on the CPU where
it has not. Otherwise the view says, in the middle, that it is waiting for
permission, that the camera is not allowed, that it stopped, that it is off,
or that there is no camera.

**The mirror is the view's.** A person expects to see themselves as in a
mirror, and a desktop camera, which does not say where it faces, almost always
faces the user, so `auto` mirrors it. The camera's own pictures, which a call
sends, stay as they were taken.

The view carries its camera's state as a class, `asking`, `active`,
`denied`, `lost`, `off` or `none`, and `mirrored` when it mirrors, so a
stylesheet can dress each. `camera.css` sizes it at least 160 by 120, rounds
its corners and puts it on black:

| Token | Sets |
|---|---|
| `--gb-camera-backdrop` | Behind the picture, and around a letterboxed one |
| `--gb-camera-message-color` | The line shown instead of a picture |
| `--gb-camera-min-width`, `--gb-camera-min-height` | The smallest the view is |
| `--gb-camera-radius` | Its corners |

## Testing without a camera

A `SyntheticCamera` paints a test card (colour bars over a grey ramp, with a
square crossing them) and takes a picture only when it is advanced, so a
picture of a `camera-view` is the same every run:

```java
var camera = SyntheticCamera.of(640, 480);
camera.advance(Duration.ofMillis(500));   // fifteen pictures at 30 fps
```

`SyntheticCamera.asking(…)` starts it waiting, and `approve()`, `deny()` and
`lose(reason)` move it as a person or a cable would.
