<!-- Destination: book/src/guide/input.md, a new `## Global shortcuts` section
     after `## Accelerators` and before `## Focus`. Doc comments in
     `dev.goldberry.input.global` link `#accelerators` until then; point them
     at `#global-shortcuts` when this moves. -->

## Global shortcuts

An accelerator fires while a window has the keyboard. A push-to-talk key, a
mute key or a "show the window" key has to work while another application has
it, so it is asked of the desktop instead, and the desktop may say no:

```java
var shortcuts = GlobalShortcuts.open();
shortcuts.bind(
                List.of(
                        GlobalShortcut.of("talk", "Push to talk", "Ctrl+Alt+T"),
                        GlobalShortcut.of("mute", "Mute the microphone", "Ctrl+Alt+M")),
                event -> {
                    switch (event.id()) {
                        case "talk" -> microphone.open(event.isPress());
                        case "mute" -> { if (event.isPress()) microphone.toggleMute(); }
                        default -> {}
                    }
                })
        .thenAccept(grants -> grants.forEach(settings::show));
```

The answer is a `Grant` per shortcut, in the order asked:

| Grant | Means |
|-------|-------|
| `Granted(id, trigger)` | its presses and releases will arrive; `trigger` is the combination in the desktop's words |
| `Refused(id, DENIED, …)` | the user said no |
| `Refused(id, TAKEN, …)` | another application holds the combination |
| `Refused(id, UNMAPPABLE, …)` | the key has no code here: `Menu` on a Mac |
| `Unsupported(id, …)` | this desktop has no way to ask |

Show the answer where the shortcut is configured. A shortcut the user turned
down, or that another application holds, is not one to tell them to press.

How each desktop is asked:

| Desktop | Asked through | Notes |
|---------|---------------|-------|
| Wayland | the desktop's GlobalShortcuts portal | the desktop shows the user a dialog, and the user may choose another trigger; the `trigger` granted is the one they chose. The application's desktop entry id (`-Dgoldberry.desktop.id`, as for notifications) is the name the desktop lists the shortcuts under |
| X11 | a key grab on the root window | whichever client grabbed the combination first keeps it; Caps Lock and Num Lock do not matter |
| Windows | `RegisterHotKey` | whichever application registered it first keeps it |
| macOS | Carbon's `RegisterEventHotKey` | no Accessibility permission is asked for; open the shortcuts on the UI thread |

A Wayland desktop gives no application the keyboard while another has focus,
so there the portal is the only way, and a desktop whose portal has no global
shortcuts answers `Unsupported` for each.

### Press and release

The listener hears each granted shortcut go down once and come up once. A
held key's repeats are not passed on, so a push-to-talk opens on the press and
closes on the release. On Windows the release is noticed within 20 ms, because
Windows reports only the press. Binding again, or closing, releases whatever
is held first, so the microphone never stays open because its release can no
longer come.

`bind` replaces the shortcuts bound before; an empty list unbinds them all.
Close the shortcuts when the application ends:

```java
@Override public void stop() { shortcuts.close(); }
```

Opened on the UI thread, the listener and the answer run on the UI thread.
`GlobalShortcuts.open(executor)` delivers both to an executor of the
application's choosing.

### In tests

`GlobalShortcuts.simulated()` grants what it is asked, except what the test
refuses, and presses what the test says:

```java
var shortcuts = GlobalShortcuts.simulated().refuse("mute", Grant.Reason.TAKEN);
var call = new Call(shortcuts);           // binds "talk" and "mute"
shortcuts.press("talk");
assertTrue(call.microphoneOpen());
shortcuts.release("talk");
assertFalse(call.microphoneOpen());
```
