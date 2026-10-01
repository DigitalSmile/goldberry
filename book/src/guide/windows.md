# Windows, popups and the host

<p class="gb-lede">An application is one class with a root widget. The launcher owns the window, and everything the application may ask of it is a method on <code>Host</code>.</p>

By the end of this chapter you can run an application and know what runs
when, open a popup that leaves the window and an overlay that does not,
follow the desktop's theme, go fullscreen, set an icon, and do work off the
UI thread and come back.

```java
public final class Hello implements Application {

    private Host host;

    @Override public String title()            { return "Hello"; }
    @Override public LogicalSize size()        { return new LogicalSize(960, 640); }
    @Override public LogicalSize minimumSize() { return LogicalSize.of(480, 320); }

    @Override public void start(Host host) {
        this.host = host;
        host.shortcut(Shortcut.primary(Key.Q), () -> host.window().close());
    }

    @Override public Widget root() {
        return new Column(new Text("Hello").id("greeting"), new Button("Close", Goldberry::stop));
    }

    public static void main(String[] args) {
        Goldberry.launch(new Hello(), args);
    }
}
```

## The lifecycle

`Goldberry.launch(application, args)` takes over the calling thread as the
UI thread and returns when the window has closed. In order:

1. `title()`, `size()`, `minimumSize()`, `maximized()` and `icon()` are read
   and the window is opened. `fonts()` is read and the font book opens.
2. `stylesheets()` is read and the renderer is built.
3. `start(host)` runs. Open what has a `close()` here, bind accelerators,
   keep the `Host`.
4. `root()` is called **once**, and the element tree mounts it. Every later
   rebuild comes from a `setState` inside it, or from a `@Bind` field that
   moved.
5. The frame loop runs, idle whenever nothing is happening.
6. After the loop ends: the tree is unmounted, the render tree closed,
   `stop()` runs, then the font book closes and the runtime shuts down.

`stop()` closes what `start` opened, in reverse. The launcher closes what the
launcher opened and nothing else: it cannot know that an `Icon` in a field is
still referenced by a widget that has not been collected.

Everything on `Application` but `root()` has a default, so the smallest
application is one method. `models()` is the wiring for a document's names
and for repaints, and is the subject of
[Building an application](../applications.md).

| Method | Default | Record |
|---|---|---|
| `size()` | 960 by 640 | what the window restores to when un-maximized |
| `minimumSize()` | no minimum | the window manager stops the drag at the floor, and the application never clamps a frame ([ADR-0304](../adr/0304-a-window-has-a-floor-and-the-desktop-enforces-it.md)) |
| `maximized()` | false | a state the desktop owns, not a large size: it snaps to the work area and restores to `size()` ([ADR-0221](../adr/0221-a-window-may-open-maximized.md)) |
| `icon()` | the platform's generic icon | several sizes of one `Image`; the backend picks which the platform scales from ([ADR-0351](../adr/0351-a-window-icon-is-several-sizes-and-the-backend-picks-the-base.md)) |
| `fonts()` | the bundled faces | faces the application ships ([Text, fonts and icons](text.md#shipping-a-face)) |

`Goldberry.launch(app, args)` reads four flags from the array and ignores
everything else: `--frames=N` paints that many frames and exits,
`--size=WxH` overrides the opening size, `--resize=WxH` walks the window's
size a pixel a frame, and `--late-budget=N` makes a run that misses more
than N refreshes exit non-zero. They exist for CI, and
`Goldberry.launch(app)` passes none.

## The host

`Host` is handed to `start` and is the only handle an application needs.
It is confined to the UI thread, except `repaint()`.

| Method | What it does |
|---|---|
| `title(text)` | sets the window's title |
| `repaint()` | asks for a frame. Coalesced, and safe from any thread |
| `restyle()` | re-reads `stylesheets()` and rebuilds the renderer. What a theme switch is ([Styling](styling.md#restyle-versus-repaint)) |
| `shortcut(...)`, `removeShortcut(...)` | window accelerators ([Input and focus](input.md#accelerators)) |
| `overlay(widget, corner)`, `fill(widget)` | floats a widget over the content, in a corner or over everything |
| `popup(...)`, `tooltip(...)`, `attachedPopup(...)` | opens a widget tree in a platform window of its own |
| `anchor(id)` | the painted rectangle of the node with that id, from the last frame |
| `focus(id, fromKeyboard)` | moves keyboard focus |
| `after(delay, action)` | runs on the UI thread after a delay, on the frame loop's own timer |
| `clipboard()`, `primarySelection()` | the session's clipboard, and X11's middle-click buffer where there is one |
| `fileDialog(spec, onChoice)`, `fileDialogs()` | the platform's open, save and folder dialogs |
| `tray(spec)` | an icon in the notification area, or empty where the desktop has none |
| `systemTheme()`, `onSystemThemeChanged(listener)` | the desktop's light-or-dark setting |
| `reducedMotion()` | whether the desktop asks for less movement |
| `openExternal(url)` | hands a URL to the desktop's browser or mail client |
| `webView(spec)`, `embeddedWebView(spec, bounds)` | a web page in a window of the engine's own, or inside this one where the window system allows it |
| `canFullscreen()`, `isFullscreen()`, `setFullscreen(on)`, `onFullscreenChanged(listener)` | fullscreen |
| `frames()` | what the frame loop has been managing lately |
| `fonts()` | the renderer's font book, for a canvas measuring its own text |
| `clock()` | the clock frames are timed against. Virtual in a test |
| `onContextMenu(handler)` | what a `context-menu="…"` right-click opens |
| `window()` | the `Window`, for the close hook, the cursor, resize and scale notifications |

`window()` is an escape hatch and is named as one. Reaching for it is a
signal that something belongs on `Host` instead.

## Overlays and popups

Something can open over a window in two places, and they are different
things.

### In the window

```java
Overlay hud = host.overlay(Hud.stages(), Corner.BOTTOM_END);
// ...
hud.remove();
```

An overlay is a sibling of the application's root, painted after everything
and taking no space from anything. Every window has an overlay layer from its
first frame, so adding one never re-parents the application and a toast
cannot cost the tree its state. `Corner` is `TOP_START`, `TOP_END`,
`BOTTOM_START` or `BOTTOM_END`, and a third argument sets the margin from the
edges instead of `Overlay.WINDOW_MARGIN`. `host.fill(widget)` covers the whole
window, which is what a `tour`'s veil and a `dialog`'s scrim are. A
`Dialogs.show(host, dialog)` and a `Toasts.at(host, controller, corner)` are
this call with a widget the catalogue wrote.

### In a window of its own

```java
Optional<Popup> menu = host.popup(new Menu(items), "menu-button", Placement.BELOW);
```

A popup is a real platform window, parented to this one and free of its
bounds. A dropdown near the bottom of a window is routinely taller than the
space under its button, and an in-window overlay would be clipped. The
content is measured with no surface, placed by `Placement` against the
anchor with a flip if it would not fit and a shift to stay inside the
display's work area, and then opened. `Placement.BELOW`, `ABOVE` and
`AFTER` ship, with `.gap(px)` and `.align(...)` on each. By id, the popup
follows its anchor when the window moves; by rectangle, it opens where the
rectangle was.

A popup is light-dismissed by default: a press anywhere in the owning window,
or `Escape`, closes it. `popup.close()`, `isOpen()`, `lightDismiss(false)`,
`takesFocus(false)` and `content(widget)` are on the handle.

> [!IMPORTANT]
> `Optional.empty()` is a normal answer. Popup support belongs to the video
> driver, and SDL's `dummy` driver has none. A caller that gets empty falls
> back to an overlay at the cost of being clipped to the window
> ([ADR-0102](../adr/0102-a-popup-is-a-window-the-platform-may-refuse.md)).
> `Menus.open(host, "menu-button", menu)` does the measuring, placing,
> opening and the submenus for a `Menu`.

## The desktop's theme

```java
host.systemTheme();                       // Optional<SystemTheme>: LIGHT, DARK, or empty
Subscription following = host.onSystemThemeChanged(theme -> actions.pickTheme(theme));
```

Empty means the desktop has no such setting, and that is a different answer
from light: the first is a default, the second a theme
([ADR-0322](../adr/0322-the-desktop-says-light-or-dark-or-says-nothing.md)).
The toolkit chooses nothing with the answer, and asking once at start-up is
the bug `onSystemThemeChanged` exists to prevent.

### Capabilities

```java
if (!Goldberry.capabilities().contains(Capability.SYSTEM_THEME)) {
    // follow the user's own choice, and say why
}
```

A platform integration is compiled into the native library only where the
machine that built it had the headers. Where it was not, the call answers
"the desktop does not say", and `Goldberry.capabilities()` reports the
difference from the library's own record of how it was built
([ADR-0325](../adr/0325-a-build-says-what-it-can-ask-the-desktop.md)).
The values are `SYSTEM_THEME`, `INPUT_METHOD`, `DEVICE_HOTPLUG`,
`FILE_DIALOG`, `SCREENSAVER_INHIBIT`, `WINDOW_DECORATIONS`, `WAYLAND` and
`WEB_VIEW`. It may be asked before a window is open, and is empty where there
is no native library at all.

## Fullscreen

```java
if (host.canFullscreen()) {
    host.setFullscreen(!host.isFullscreen());
}
host.onFullscreenChanged(on -> model.setFullscreen(on));
```

Fullscreen is a state the platform owns. `setFullscreen` is a request whose
answer arrives through `onFullscreenChanged`, several frames later on macOS,
and the user can change it without the application, so `isFullscreen()` is
what the platform last reported
([ADR-0473](../adr/0473-a-window-is-fullscreen-when-the-platform-says-so.md)).
`canFullscreen()` is false for a host with no window under it, which is what
a `media-player` reads to decide whether to offer the button at all.

## Threads

```java
Goldberry.async(() -> readTheFile())
         .thenAccept(text -> host.title(text));      // already on the UI thread
```

There is one UI thread, and every window, style and box tree is confined to
it. `Goldberry.async(work)` runs the work on a virtual thread and delivers
its result on the UI thread, so every callback chained onto the future may
touch a window with no hand-off to write
([ADR-0020](../adr/0020-one-ui-thread-and-virtual-threads-behind-it.md)).
`Goldberry.ui()` is the UI thread as an `Executor`, `Goldberry.isUiThread()`
says whether you are on it, and `host.repaint()` is safe from anywhere.

A write to a model from a background continuation is not a write the toolkit
was watching for. `Models.refresh(model)` after it sweeps the fields, and is
a no-op once the model is woven
([Model weaving](../weaving.md#the-sweep-and-the-one-line-it-sometimes-costs)).

> [!CAUTION]
> On macOS the JVM must start on the process's first thread, because AppKit
> requires it. Run with `-XstartOnFirstThread`. Without it SDL reports
> `No available video device`, and the toolkit's message says which flag is
> missing ([ADR-0039](../adr/0039-macos-needs-the-first-thread.md)).

## Closing

`host.window().close()` closes the window, and the loop ends when the last
window has closed. `window.onCloseRequest(handler)` sees the user's close
button first and returns whether to allow it. `Goldberry.stop()` asks the
loop to finish from any thread.

## The low-level path

```java
var window = Window.open("Hello", 960, 640);
window.onPaint(frame -> frame.fill(0xFF2E3440));
Goldberry.run();
```

That is the whole API for a window with no widgets: no backend to name, no
event loop to build. `Window.open(WindowSpec)` takes a spec with
`withMaximized`, `withMinimumSize`, `withDecorated` and `withResizable`.
Painting takes logical coordinates, colours are straight `0xAARRGGBB`, and
the frame is the platform's own buffer wherever it lends one.

`Window.open` may be called more than once, and `Goldberry.run()` returns
when every window has closed. The launcher opens one window per
`Application`; an application that wants a second top-level window opens it
here and drives it itself.

## Read more

- [ADR-0093](../adr/0093-an-application-is-a-root-widget.md): what the launcher owns
- [ADR-0100](../adr/0100-a-window-has-a-layer-above-its-application.md): the overlay layer
- [ADR-0102](../adr/0102-a-popup-is-a-window-the-platform-may-refuse.md): popups
- [ADR-0104](../adr/0104-a-popup-is-measured-then-placed.md): measure, then place
- [ADR-0140](../adr/0140-a-widget-may-reach-its-window.md): `BuildContext.host()`
- [Overlays](../components/overlays.md) and [Menus and the tray](../components/menus.md): the widgets that open over a window
