# Windows, popups and the host

<p class="gb-lede">An application is one class with a root widget. The launcher owns the window, and everything the application may ask of it is a method on <code>Host</code>.</p>

By the end of this chapter you can run an application and know what runs
when, open a popup that leaves the window and an overlay that does not,
follow the desktop's theme, go fullscreen, set an icon, put the window back
where the user left it, open a second window, and do work off the UI thread
and come back.

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

1. `title()`, `size()`, `minimumSize()`, `maximized()`, `position()`,
   `display()` and `icon()` are read and the window is opened, hidden, put
   where it was asked, and then shown. `fonts()` is read and the font book opens.
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

| Method | Default | What it means |
|---|---|---|
| `size()` | 960 by 640 | what the window restores to when un-maximized |
| `minimumSize()` | no minimum | the window manager stops the drag at the floor, and the application never clamps a frame |
| `maximized()` | false | a state the desktop owns, not a large size: it snaps to the work area and restores to `size()` |
| `position()` | the platform's choice | where the window's top-left opens, clamped onto a display that exists ([Where a window opens](#where-a-window-opens)) |
| `display()` | none | the name of the display to open centred on, and the fallback for a position on no display |
| `icon()` | the platform's generic icon | several sizes of one `Image`; the backend picks which the platform scales from |
| `fonts()` | the bundled faces | faces the application ships ([Text, fonts and icons](text.md#shipping-a-face)) |

`Goldberry.launch(app, args)` reads four flags from the array and ignores
everything else: `--frames=N` paints that many frames and exits,
`--size=WxH` overrides the opening size, `--resize=WxH` walks the window's
size a pixel a frame, and `--late-budget=N` makes a run that misses more
than N refreshes exit non-zero. They exist for an automated run, and
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
| `displays()` | the displays connected now, the primary one first ([Displays](#displays)) |
| `openWindow(spec, root)` | a second top-level window with a tree of its own ([More than one window](#more-than-one-window)) |
| `window()` | the `Window`, for the close hook, the cursor, position, attention, resize and scale notifications |

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

Each overlay is its own node, told apart by its `Overlay` handle and never by
what its widget looks like. Two equal widgets shown at once are two nodes, and
one shown in the same turn another was removed starts with fresh state.
A widget on the layer finds its own handle with
`WindowRoot.overlayOf(context)`.

`remove()` takes an overlay away at once. `dismiss()` takes it away the way its
widget leaves: a `dialog` fades out first and is removed when the fade is over.
An overlay whose widget has no exit is removed at once by either call. Both are
idempotent.

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
> back to an overlay at the cost of being clipped to the window.
> `Menus.open(host, "menu-button", menu)` does the measuring, placing,
> opening and the submenus for a `Menu`.

## The desktop's theme

```java
host.systemTheme();                       // Optional<SystemTheme>: LIGHT, DARK, or empty
Subscription following = host.onSystemThemeChanged(theme -> actions.pickTheme(theme));
```

Empty means the desktop has no such setting, and that is a different answer
from light: the first is a default, the second a theme.
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
difference from the library's own record of how it was built.
The values are `SYSTEM_THEME`, `INPUT_METHOD`, `DEVICE_HOTPLUG`,
`FILE_DIALOG`, `SCREENSAVER_INHIBIT`, `WINDOW_DECORATIONS`, `WAYLAND`,
`WEB_VIEW` and `NOTIFICATIONS`. It may be asked before a window is open, and is
empty where there is no native library at all, apart from `WEB_VIEW` and
`NOTIFICATIONS`, which are a second library being there.

## Notifications

```java
host.notify(Notification.of("Gate waiting", "prod-eu needs an approval")
        .onActivate(() -> actions.showGates()));
```

A notification is drawn by the desktop: GNOME's banner, macOS's Notification
Center, Windows' toast. `notify` answers whether the desktop took it, and
false is an ordinary answer. The action runs on the UI thread when the user
clicks the notification, and the window repaints after it.

| Platform | Shown by | A click | Needs |
|---|---|---|---|
| Linux | The `org.freedesktop.Notifications` service, over D-Bus | Reported | A notification daemon on the session bus |
| macOS | `UNUserNotificationCenter` | Reported | An `.app` bundle, and the user's permission |
| Windows | The notification area's balloon, shown as a toast | Not reported | Nothing |

On macOS a plain `java` process has no bundle identifier, and macOS posts
nothing for it. `notify` answers false and says why once, at info. Package the
application as an `.app`. A real Windows toast needs an AppUserModelID
registered at installation. Both are the application's packaging, not the
toolkit's.
`Capability.NOTIFICATIONS` says whether this process can ask at all.

### The badge

```java
host.badge(3);        // a number on the dock or launcher icon
host.badge("!");      // any short text, on macOS
host.badge(0);        // none
```

macOS shows any short text on the dock icon. A Linux dock shows a number:
Ubuntu's dock, Dash to Dock, Plank and KDE's task manager read the Unity
launcher signal. A dock finds the application by its desktop entry, which only
the application knows. Name it with `-Dgoldberry.desktop.id=ru.example.App`,
without `.desktop`, or start the application from its entry, which sets
`GIO_LAUNCHED_DESKTOP_FILE`. Windows has no badge here, and `badge` answers
false.

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
what the platform last reported.
`canFullscreen()` is false for a host with no window under it, which is what
a `media-player` reads to decide whether to offer the button at all.

## Where a window opens

```java
@Override public Optional<LogicalPoint> position() { return settings.position(); }
@Override public Optional<String> display()        { return settings.display(); }

@Override public void start(Host host) {
    var window = host.window();
    window.onCloseRequest(() -> {
        window.normalBounds().ifPresent(bounds -> settings.position(bounds.origin()));
        settings.size(window.normalSize());
        settings.maximized(window.isMaximized());
        window.display().ifPresent(display -> settings.display(display.name()));
        return true;
    });
}
```

A window is created hidden, so where it opens is decided before anybody sees
it. `position()` is its top-left in the **desktop's coordinates**, the space
`Window.position()` reports and every `Display` rectangle is in: the primary
display's corner is usually the origin, and a display to its left has a
negative `x`.

What was saved may not fit the desktop the application opens on. A monitor
was unplugged, or a laptop left its dock. So the position is checked, in
order:

1. on a display that exists: it opens there, **clamped** into that display's
   usable bounds, clear of the taskbar or the dock;
2. on no display: it opens centred on the display named by `display()`, if
   that one is still connected, and otherwise centred on the primary display.

With no position, a `display()` alone opens centred on that display, and
nothing at all leaves it to the platform. The same clamp applies to
`window.move(point)`, so a window can never be put off every screen.

`normalBounds()` is what to save rather than the current bounds. They are
the bounds the window returns to when it stops being maximized or
fullscreen, so a window closed maximized opens maximized and still restores
to the size the user last gave it. They are still answered after the window
has closed. `onMove(listener)` reports every move.

> [!NOTE]
> **Wayland places every window itself.** A compositor tells no application
> where its windows are, and refuses to put one anywhere. There,
> `position()` is empty, `move` returns false, `normalBounds()` is empty and
> `normalSize()` is the whole of what can be restored. X11, Windows and macOS
> place windows as asked.

### Displays

```java
for (var display : host.displays()) {
    log.info("{} {} at {}", display.name(), display.bounds(), display.scale());
}
```

A `Display` has a name, its full `bounds()`, the `usableBounds()` a window
may occupy (less panels and docks), its `scale()`, and whether it is the
`primary()` one. Its `id()` is good for this run only. Remember a display by
its name, which is a monitor's model as a rule. `window.display()` is the
display most of a window is on. `DisplayLayout` holds the rule above, for an
application that wants to check a position itself.

## Asking for attention

```java
Goldberry.async(this::export)
         .thenRun(() -> host.window().requestAttention(Attention.UNTIL_FOCUSED));
```

`requestAttention(Attention.BRIEFLY)` or `UNTIL_FOCUSED` asks the desktop to
draw the user's eye to a window that is not in front: the dock icon bounces
on macOS, the taskbar button flashes on Windows, and X11 sets the urgency
hint, which the desktop shows its own way. `cancelAttention()` withdraws it,
and `raise()` brings a window to the front, which a desktop may answer with a
flash instead. It is not a notification. It says which window, and nothing
about why.

## More than one window

```java
private WindowHost settings;

void openSettings(Host host) {
    if (settings != null) {
        settings.window().raise();
        return;
    }
    var spec = WindowSpec.of("Settings", LogicalSize.of(480, 360)).withOwnership(Ownership.OWNED);
    settings = host.openWindow(spec, new SettingsPage(model)).orElseThrow();
    settings.onClose(() -> settings = null);
}
```

`host.openWindow(spec, root)` opens a second top-level window with `root` in
it and returns its `WindowHost`, which is a `Host` for that window plus
`close()`, `isOpen()` and `onClose(action)`. The new window has its own tree,
router, overlays, focus, accelerators, popups and tooltips. It shares what
belongs to the application: the stylesheets (a `restyle()` from any window
restyles every window), the fonts, the models (a change repaints every
window) and the clock. A widget in it reaches its own window's host through
`BuildContext.host()`.

`spec.withOwnership(...)` says whether it belongs to the window it was opened
from:

| Ownership | What it does |
|---|---|
| `NONE` | a window of its own, the default |
| `OWNED` | kept above its owner and minimized with it; closes when its owner closes. Opens centred on the owner unless the spec says where |
| `MODAL` | `OWNED`, and the owner takes no input until it closes. A press on the owner brings the modal window to the front |

Closing the application's first window closes every other one and ends the
application, as `Goldberry.stop()` does. Closing any other window takes its
tree down on the next turn of the loop and then runs its `onClose` actions.

## Threads

```java
Goldberry.async(() -> readTheFile())
         .thenAccept(text -> host.title(text));      // already on the UI thread
```

There is one UI thread, and every window, style and box tree is confined to
it. `Goldberry.async(work)` runs the work on a virtual thread and delivers
its result on the UI thread, so every callback chained onto the future may
touch a window with no hand-off to write.
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
> missing.

## Closing

`host.window().close()` closes the window, and the loop ends when the last
window has closed. The first window is the application's: closing it closes
every window `openWindow` opened. `window.onCloseRequest(handler)` sees the user's close
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
`withMaximized`, `withMinimumSize`, `withDecorated`, `withResizable`,
`withPosition` and `withDisplay`.
Painting takes logical coordinates, colours are straight `0xAARRGGBB`, and
the frame is the platform's own buffer wherever it lends one.

`Window.open` may be called more than once, and `Goldberry.run()` returns
when every window has closed. An application launched through the launcher
opens a second window with widgets in it through `host.openWindow`, not here.

## Read more

- [Overlays](../components/overlays.md) and [Menus and the tray](../components/menus.md): the widgets that open over a window
