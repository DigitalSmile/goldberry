# Guide pieces for the downstream gaps batch

Each piece names the page and place it goes. Neutral prose: no ADR numbers,
no history.

## `book/src/guide/logging.md`, the properties table, after `goldberry.gpu.debug`

| `goldberry.gpu.power` | `low`, `high` | which GPU the device prefers on a machine with an integrated and a discrete one; `low` by default |

## `book/src/components/gpu.md`, `### Rendering offscreen`, after the first sample

`OffscreenGpu.open(GpuPower.HIGH)` prefers the discrete GPU on a machine with
two, and `gpu.adapter().name()` says which one the driver gave. A benchmark
prints it so its numbers say what they measured.

## `book/src/guide/styling.md`, `## Text scale`, a new last paragraph

An application sets the scale its windows open at with `textScale()`, and
changes it while running with `host.textScale(1.25)`: every window draws its
text at the new size from the next frame. The value is clamped to 0.9–1.5.
`Offscreen.of(w, h).textScale(1.5)` renders a tree at a scale for a test.

## `book/src/guide/testing.md`, after the paragraph on `Offscreen.render`

A tree that builds to nothing renders as its background, so an overlay's empty
state can be rendered on its own.

## `book/src/guide/styling.md`, `### Math functions`, after the list of what does not fold

A length divided by a length is a number, so a value can follow the window:

```css
.hud { scale: min(100vw / 2560px, 100vh / 1440px); }
```

A percentage cannot be divided, because what it is a percentage of is not
known until layout.

## `book/src/guide/input.md`, `## Pointer events`, after the paragraph on `CLICKED`

The secondary and middle buttons click too, as `AUX_CLICKED`, by the same
rule: a press and a release on the same node, not dragged off. `button()` says
which. A control that acts on `CLICKED` never hears a right click.

## `book/src/guide/input.md`, after `## Hover hooks`, a new section `## Click handlers`

Any widget takes a click handler, with no wrapper around it:

```java
new Panel(row).onClick(event -> select(row, event.button(), event.local().fractionX()))
```

It hears clicks of every button, with the position as a fraction of the
node's own box. It consumes the click, so an ancestor's handler does not run
too, and a control inside the node that takes the click keeps it. A node with
only a click handler is not a keyboard control: for one a keyboard must
reach, use `pressable` or `button`.

## `book/src/performance/measuring.md`, the launch-flag table, after `--capture=`

| `--capture-at=N` | writes frame N to the `--capture=` file and keeps running |

## `book/src/guide/windows.md`, after the paragraph on `host.window()`

`host.capture(path)` writes what the window shows now, GPU layers included, to
a PNG: what a "report a problem" button attaches.

## `book/src/guide/windows.md`, a new section `## Opening links and one running copy`, after `## The desktop's theme`

The system can ask an application to open something: a link of a scheme the
application registered, or a file opened from the desktop. It arrives in
`onOpenUrl`:

```java
@Override
public void onOpenUrl(URI target) {
    router.open(target);
}
```

On Windows and Linux the system starts a new process for a link. An
application that names an id is one running copy, and a second launch hands
its command line to the first and exits:

```java
@Override
public Optional<String> singleInstance() {
    return Optional.of("dev.example.chat");
}
```

Registering the scheme is the installer's: a `.desktop` file with
`MimeType=x-scheme-handler/chat;`, `CFBundleURLTypes` in `Info.plist`, or a
registry key under `HKCU\Software\Classes`.

## `book/src/guide/windows.md`, a new section `## Secrets`, after `## Opening links and one running copy`

A token that keeps the user signed in belongs in the system's own store, not
in a file. `Keychain.system()` keeps one secret per service and account in
the Secret Service on Linux (gnome-keyring, KWallet, KeePassXC), the login
Keychain on macOS and the Credential Manager on Windows:

```java
var keychain = Keychain.system();
Thread.startVirtualThread(() -> {
    keychain.store("chat", account, refreshToken);
    var token = keychain.loadText("chat", account);
});
```

Every call waits for the store, and a locked store asks the user to unlock it
first, so never call one on the UI thread. A refusal is a `KeychainException`
whose `reason()` says which: `UNAVAILABLE` where there is no store (a
container, a desktop without a keyring daemon), `DENIED` when the user
dismissed the prompt, `FAILED` otherwise. The Credential Manager holds at most
2560 bytes per secret. In a test, `Keychain.inMemory()` stands in for the
system's store.

## `book/src/guide/windows.md`, `## Notifications`, the table's Windows row

| Windows | The notification area's balloon, shown as a toast | Reported, for the latest notification | Nothing |

## `book/src/guide/windows.md`, `### The badge`, replacing the last sentence ("Windows has no badge here, and `badge` answers false.")

On Windows the badge sits over the window's taskbar button: up to three
characters, white on a red disc, and a number past 99 as `99+`.

## `book/src/components/content.md`, `## \`web-view\``, after the paragraph on callbacks

A page can be given scripts that run before its own, on every navigation,
which is where an embedding application defines the API its pages call. And
the application can talk to the open page through the controller:

```java
var page = WebPage.of(app.url()).initScript("window.Host = { version: 2 };").on("hostSend", this::received);
controller.postMessage("{\"type\":\"theme\",\"dark\":true}");
```

A page hears a posted message as a `message` event on its `window`, with the
JSON as `event.data`.

## `book/src/ahead-of-time-binding.md`, `## Adding it to a build`, replacing `### Gradle` and the sentence above it

The weaver is one jar, `dev.goldberry:goldberry-weaver`. It takes directories
of compiled classes and rewrites them in place, resolving the module's own
types against its compile classpath, `goldberry-core` included. `--models` and
`--catalog` select one half; with neither, it runs both.

### Gradle

The `dev.goldberry.weave` plugin runs both halves at the right moments:

```groovy
// settings.gradle
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

// build.gradle
plugins {
    id 'java'
    id 'dev.goldberry.weave' version '2026.2'
}
```

The catalog is declared before `classes` on every build. The models are woven
when `goldberry { nativeImage = true }` is set, or for one build with
`-Pgoldberry.nativeImage=true`; `./gradlew weaveModels` weaves them on demand.
The weaver runs on the project's Java toolchain.

## `book/src/guide/text.md`, a new section `## Words in the user's language`, before `## Read more`

`dev.goldberry.i18n` has CLDR's plural rules for every locale and ICU's
message syntax, so a count reads right in any language:

```java
var files = IcuMessage.parse(
        "{count, plural, one {# файл} few {# файла} many {# файлов} other {# файла}}");
files.format(Locale.forLanguageTag("ru"), Map.of("count", 21));   // "21 файл"

PluralRules.of(Locale.ENGLISH, PluralType.ORDINAL).select(22);   // TWO: "22nd"
```

A message takes `{name}`, `{name, number}`, `plural` and `selectordinal`
with `=0` cases, `offset:` and `#`, and `select`. Every plural and select
needs an `other` case, and a message without one is refused when it is
parsed.

The toolkit's own words, a text field's Cut, Copy and Paste and the names a
screen reader hears for close and dismiss buttons, are English until an
application gives it a translation:

```java
Labels.use(ResourceBundle.getBundle("app.messages", locale));
// app/messages_ru.properties:  goldberry.copy=Копировать
```

Each `ToolkitLabel` names its key. A word the bundle lacks stays English.
Call it again when the user switches language, and restyle so open menus
are rebuilt.

## `book/src/components/drawing.md`, `### Input`, after the paragraph under the sample

`path.contains(x, y)` says whether a point is inside a path's fill, by the
`nonzero` rule a fill uses, or `path.contains(x, y, true)` for `evenodd`. A
board hit-tests what it drew with it: a click lands in a note's rounded
outline, not its bounding box, and not in the hole of a ring. A box with a
`clip-path: path(…)` is hit by the pointer the same way.

## `book/src/performance/measuring.md`, after the section on `Host.frames()`, a new section `## Every frame, and the hitch`

`host.window().onFrame(frame -> …)` hears every frame the window presents,
with its interval, its paint and stages, the refreshes it was late by, and on
a composited window its upload, acquire, submit and GPU layers. The listener
is handed the same object every frame, so listening costs nothing; keep one
with `frame.copy()`.

```java
window.onFrame(frame -> {
    if (frame.intervalNanos() > 25_000_000) {
        hitches.add(frame.copy());
    }
});
```

`frames.percentile(FrameStage.INTERVAL, 99)` is the hitch a mean smooths
away, over the last sixty frames; `percentile(FrameStage.PAINT, 50)` is the
median paint. `frames.layers()` is what the GPU layers took to render
again, a `canvas3d`'s renderer recording its passes, apart from the
compositor's own work.

## `book/src/components/overlays.md`, `## `hud``, the list of readings

The readings are `fps`, `refresh`, `late`, `paint`, `build`, `style`, `layout`,
`raster`, `upload`, `acquire`, `submit` and `layers`. `layers` is the GPU
layers' own rendering time, judged against a quarter of a frame.

## `book/src/guide/input.md`, `## Pointer events`, after the paragraph on `WHEEL`

A two-finger pinch on a touchpad arrives as `PINCH`, where the pointer is.
`event.scale()` is how much the fingers spread since the last change, so a
zoom multiplies by it, and `event.pinchPhase()` says whether the gesture
`BEGAN`, `CHANGED` or `ENDED`. Where the platform reports no pinch, a
touchpad's zoom arrives as `Ctrl` with the wheel.

```java
case PINCH -> board.zoomBy(event.scale(), event.content().x(), event.content().y());
```


## `book/src/guide/testing.md`, `## The virtual clock`, a new last paragraph

`Offscreen.of(w, h).measure(root)` is a strip for a frame's cost rather than
its picture. It paints into one buffer and, as a window does, only where
something changed, so a frame with nothing changed paints nothing. Each
`frame()` says what it cost and how much it painted, and `stats()` reads as a
window's statistics do.

```java
try (var meter = Offscreen.of(2560, 1440).stylesheets(sheets).measure(new Hud(model))) {
    for (var i = 0; i < 600; i++) {
        model.tick();
        var frame = meter.frame();
        log(frame.timings().paintNanos(), frame.damagedPixels());
        meter.advance(16);
    }
    var p99 = meter.stats().percentile(FrameStage.PAINT, 99);
}
```

`meter.picture()` is a copy of what the buffer holds, for a check beside the
numbers.

## `book/src/guide/text.md`, `## Images`, after the paragraph on an image having nothing to close

`Image.decodeInto` decodes into memory the caller owns instead, such as a GPU
transfer buffer or an atlas page being built, and returns the size. The
function is asked for the memory once the size is known. `ChannelOrder.RGBA`
swaps red and blue in the same pass; `BGRA` is an image's own order. Both are
premultiplied.

```java
var size = Image.decodeInto(bytes, ChannelOrder.RGBA,
        decoded -> PixelTarget.packed(transfer.map(decoded), decoded.width()));
```

## `book/src/guide/styling.md`, `### Typography`, after the paragraph on weights

```css
.timestamp { font-variant-numeric: tabular-nums }
.code      { font-feature-settings: "liga" 0, "calt" 0 }
```

`font-feature-settings` turns OpenType features on and off: `"tag"` alone or
with `on`, `off` or a number that picks an alternate. `font-variant-numeric`
names the figure features: `lining-nums` or `oldstyle-nums`,
`proportional-nums` or `tabular-nums`, `diagonal-fractions` or
`stacked-fractions`, `ordinal` and `slashed-zero`. Both inherit. Where both
name a feature, `font-feature-settings` wins, and `font` resets both. A
feature the face does not have does nothing. Inter's figures are proportional,
so a clock or a counter that should not jitter as it ticks asks for
`tabular-nums`.

## `book/src/components/collections.md`, `## `list``, after the paragraph on virtualized rows, and a row in the Java table

**Sections.** `sections(sectionOf, header)` groups consecutive items with one
key, such as a day in a timeline, under a header widget. The header is drawn
above the section's first row, and while that section is at the top of the
viewport a copy stays pinned there until the next section's header pushes it
out. A header is not a row: it is never focused or selected, the typeahead
does not match it, and `Home` and `End` go to rows. It is styled as
`list-section-header`, and the pinned copy as `list-section-header.pinned`.

```java
new ListView<>(messages, Message::id, this::bubble)
        .virtualized(RowHeights.estimating(64))
        .sections(Message::day, day -> new Text(day))
```

| `sections(Function<T, String>, Function<String, Widget>)` | Headed runs of items, with the top one pinned. |

## `book/src/components/media.md`, `## `video-view``, a new last paragraph and sample

A picture that does not come from a player, such as a call's video decoded
by the application, is shown by `PictureView` over a `PictureSource`. A
`PushedPictures` takes pictures from any thread, and the view draws the newest
once a frame, as a `video-view` with the same styling.

```java
var tile = new PushedPictures();
row.add(new PictureView(tile, Fit.COVER));
tile.push(new VideoPlanes(...));   // from the thread that decoded it
```

A pushed picture's bytes must stay as they are until two more have been
pushed after it. A `VideoPicture` is drawn as it is. `VideoPlanes` are shown
by the GPU where the window has one, and converted to BGRA on the CPU where it
has not, which takes the module's natives; a picture is converted once however
many frames draw it.

## `book/src/components/content.md`, `## `web-view``, after the paragraph on reading cookies

A sign-out deletes what the page signed in with: `deleteCookies(url, name)`
deletes the cookies the engine would send to `url` (all of them when `name`
is null), and `clearCookies()` empties the jar. Both complete on the UI thread.
On Linux and macOS every page shares one jar. `WebViewSpec.userAgent(agent)`
sets the user agent the page sends from its first request, and the
controller's `userAgent(agent)` changes it later.

## `book/src/guide/styling.md`, `### Transition and animation`, a new last part

A box with a `view-transition-name` that layout moves or resizes is drawn
where it was, and travels to where it is now:

```css
call-tile { view-transition-name: tile-7; transition: transform 300ms ease-out }
```

The name is the box's identity, so a tile moved into another column still
travels from where it was; give each box its own. The move takes the box's
`transition` for `transform` when it has one, and 250 ms `ease` otherwise.
Under reduced motion the box moves at once. A box that changes size is
stretched while it travels, and whatever is inside it moves with it.

Unlike a browser, which animates only inside `startViewTransition()`, every
layout change moves a named box this way. `auto` and `match-element` are not
read.
