# Input and focus

<p class="gb-lede">Pointer, wheel and keyboard events route through one router that remembers who is hovered, pressed and focused, against the frame the user is looking at.</p>

By the end of this chapter you can bind an accelerator, make a composite one
Tab stop, read a drag without remembering where it started, take a dropped
file, open a context menu from the keyboard, and know what a custom widget
implements to take part.

```java
@Override public void start(Host host) {
    host.shortcut(Shortcut.primary(Key.S), this::save);        // Cmd+S or Ctrl+S
    host.shortcut("Ctrl+Shift+Z", this::redo);
    host.window().onFileDrop(drop -> open(drop.first(), drop.at()));
    Menus.contextMenus(host, Map.of("rows", rowMenu()));
}
```

## How an event travels

Dispatch is capture, then target, then bubble. A `Handles` widget sees the
event in `onPointerCapture` root-first, then the deepest node under the
pointer sees it in `onPointer`, then each ancestor does. `consume()` stops it
at any step. The target stays the same through all three phases, so an
ancestor can tell what happened to it from what happened below it.

The router holds what input has to remember between frames against
**elements**, because a widget is rebuilt constantly and could not remember
any of it. `:hover`, `:active`, `:focus` and `:focus-visible` are set on
elements by the router, which is why they survive a rebuild.

Hit testing runs against the snapshot taken while painting, not a fresh
layout pass. A pointer event is about what the user can see, and that is the
last frame drawn ([ADR-0054](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0054-hit-testing-runs-against-the-painted-frame.md)).
A transform the painter applied is inverted once, from the very matrix the
rasterizer used, so a scaled control responds where it is drawn.

### Kinds

| `PointerEvent.Kind` | When |
|---|---|
| `MOVED` | the pointer moved with no button change |
| `PRESSED`, `RELEASED` | a button went down or came up, with `button()` and `clickCount()` |
| `ENTERED`, `EXITED` | synthetic, derived from pointer flow |
| `CLICKED` | synthetic: a press and its release both landed on this node |
| `WHEEL` | the wheel turned or a touchpad scrolled |

A press that is dragged off and let go elsewhere still releases, so the
captor can stop looking pressed, and is not a click. Cancelling a click by
dragging off is a gesture people rely on.

## A press captures the pointer

A press takes an implicit capture until the release, so a drag that leaves a
widget still reaches it and `:active` cannot get stuck. That is what makes a
slider work ([ADR-0058](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0058-a-press-captures-the-pointer.md)).
`:hover` keeps following the pointer regardless: capture decides who is
told, not what is highlighted. `router.capturePointer(element)` takes a
capture that outlives the release, for the rare widget that needs one.

## Gestures: where the drag started

```java
@Override public void onPointer(PointerEvent event) {
    if (event.kind() == PointerEvent.Kind.MOVED && !Float.isNaN(event.dragX())) {
        var fraction = event.local().fractionX();          // 0..1 along my own box
        onChange.accept(min + fraction * (max - min));
    }
}
```

A widget is a value rebuilt every frame and cannot remember where a drag
began. The router can, and reports it on every event of the gesture:
`pressX()` and `pressY()` are where the button went down, and `dragX()` and
`dragY()` are how far the pointer has travelled since, `NaN` when no button
is held ([ADR-0075](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0075-a-gestures-origin-is-the-routers.md)).

`local()` is the pointer in the widget's own box, with `fractionX()` and
`fractionY()` clamped to `0..1`. A control whose hit target is bigger than
the thing being pointed along, a slider with a readout beside its track, names
the part to measure against with `Handles.localPart()`
([ADR-0079](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0079-a-continuous-value-is-placed-by-ratio.md)). A
control whose drag is a rate rather than a position, a knob, answers
`gestureAnchor()` once on the press and gets it back as `anchor()` on every
event after. A `canvas` reads `content()` instead of `local()`: it is
measured from inside the padding, where the painter draws.

A drag held at the edge of a viewport carries the viewport on, and the
selection with it ([ADR-0500](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0500-a-drag-held-at-the-edge-carries-the-viewport-on.md)).

## The wheel is lines

```java
@Override public void onPointer(PointerEvent event) {
    if (event.kind() == PointerEvent.Kind.WHEEL) {
        scrollBy(event.deltaY() * lineHeight);
        event.consume();          // and the page behind does not lurch
    }
}
```

Wheel deltas are in lines, fractional, positive down and right. A touchpad
sends a fraction of a detent per frame and the fraction is preserved in
`deltaY()`; a mouse's whole detents are in `ticksY()`
([ADR-0056](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0056-the-wheel-is-lines-and-the-sign-is-ours.md),
[ADR-0115](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0115-a-wheel-reports-a-fraction-and-a-detent.md)).
Natural scrolling and SDL's away-from-the-user sign are both undone at the
boundary, so a widget never sees either. `--gb-scroll-line` in the theme
says how far one line moves a viewport.

## Keys and text are different events

A `KeyEvent` is a key going down or up, with its `Key`, its `Modifiers` and
whether it is a repeat. A `TextEvent` is committed text: what the platform's
compose and input method produced, which for anything but a Latin keyboard
is not a key at all. One character can take several keystrokes
([ADR-0055](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0055-sdl-owns-keyboard-translation.md)).

A widget that wants what the user typed implements `onText`, and says so
with `wantsTextInput()`. That is what turns the platform's text input on,
and the input method with it; a board that only wants arrow keys leaves it
off. A field that handles a key consumes it, and a key it does not handle
goes on, so `Tab` still moves focus.

## Accelerators

```java
host.shortcut(Mod.CTRL.and(Key.T), actions::toggleTheme);   // enums: cannot be misspelled
host.shortcut("Primary+S", this::save);                     // the desktop's own modifier
host.removeShortcut("Primary+S");
```

An accelerator is per window and fires **after** the focused widget has
declined the key, so a text field keeps its own `Ctrl+A`. The modifiers must
match exactly: `Ctrl+S` does not fire on `Ctrl+Shift+S`. A string that names
no key throws at the call.

`Primary` is the desktop's own modifier: `Cmd` on macOS and `Ctrl`
everywhere else. `Shortcut.primary(Key.S)` builds it in Java, and `Mod`,
`CmdOrCtrl` and `Primary` all spell it in a string. Nothing else is
translated: a shortcut that says `Ctrl` means the control key on every
desktop, which is what a terminal or an Emacs-bound editor needs
([ADR-0378](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0378-the-desktops-own-modifier-has-a-name.md)).
`-Dgoldberry.input.primary=ctrl` or `=meta` overrides the answer, and the
toolkit's own tests run with `ctrl` on every runner.

A widget that binds keys while mounted, a `menubar`, passes itself as the
owner and gives them back with `removeShortcut(shortcut, owner)`, so an
unmount cannot take a binding somebody else made.

## Focus

One focus owner per window. Focus moves by a press and by `Tab` and
`Shift+Tab` in document order, and `host.focus(id, fromKeyboard)` moves it
from Java: a dialog putting the caret in its first field, a form jumping to
its first error. It is refused when the node cannot take focus, is disabled,
or is outside a modal that is open.

`:focus` is true however focus arrived. `:focus-visible` is true only for
keyboard focus, and the ring is drawn on that one, so a button clicked with a
mouse gets no ring. `Handles.onFocusChanged(focused, fromKeyboard)` tells a
widget the same thing, and `onFocusWithin` tells a container when focus
enters or leaves its subtree as a whole.

### A composite is one Tab stop

```java
@Override public FocusScope focusScope() {
    return FocusScope.VERTICAL;     // Up and Down rove; Left and Right are mine
}
```

A radio group, a tab list, a menu or a toolbar is one Tab stop, with the
arrow keys moving focus inside it
([ADR-0073](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0073-a-composite-is-one-tab-stop.md)). The group says
which arrows rove: `HORIZONTAL`, `VERTICAL`, or `BOTH` for a group whose
direction is the stylesheet's, which is what `radio-group` answers. `Home`
and `End` reach the ends of any scope. Traversal enters at the descendant
matching `:checked`, or the first one, so the selection is the roving
position and there is no second piece of state to disagree with it.

## The cursor

```css
button    { cursor: pointer }
.splitter { cursor: ew-resize }
```

```java
host.window().cursor(Cursor.WAIT);      // or decide it yourself
```

The cursor is a property of the painted rectangle, set from CSS or from
code, and it inherits down the stack of rectangles under the pointer rather
than the element tree. `cursor: pointer` on a button therefore covers the
label inside it, and the shape freezes during a drag
([ADR-0057](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0057-the-cursor-rides-on-the-painted-box.md)). Custom
image cursors are not built, and `grab` and `grabbing` fall back to `move`.

## Dropped files and text

```java
host.window().onFileDrop(drop -> {
    for (var path : drop.paths()) { board.add(path, drop.at()); }
});
host.window().onTextDrop(drop -> note.append(drop.lines()));
```

A desktop reports a drop as a beginning, a position, one event per file and
an end. The toolkit reassembles that into one `FileDrop` carrying every path
and the point it landed on, in the window's logical coordinates, because a
board needs to know where
([ADR-0330](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0330-a-dropped-file-arrives-somewhere.md)). Both
listeners return a `Subscription` to close when a widget that listened
leaves the tree.

## Context menus

```kdl
panel id="company" context-menu="rows" {
  text "Frodo Baggins"
  text "Samwise Gamgee"
}
```

```java
Menus.contextMenus(host, Map.of("rows", rowMenu()));
```

`context-menu="rows"` on any widget names a menu. A right-click, the `Menu`
key or `Shift+F10` on the focused widget finds the name by walking up from
what is under the pointer, and hands it to the one handler the application
registered with the point it happened at. What the name means is the
catalogue's, which is why `Menus.contextMenus` is the line that turns a name
into a menu ([ADR-0208](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0208-a-context-menu-answers-the-keyboard.md)).
Bare `F10` is the menu bar's.

## What a custom widget implements

A widget takes part in input by implementing `Handles`. Everything on it has
a default, so a widget overrides what it uses.

| Method | For |
|---|---|
| `onPointerCapture`, `onPointer` | the pointer, root-first and then deepest-first |
| `onKeyCapture`, `onKey` | keys, the same two phases |
| `onTextCapture`, `onText`, `onPreedit` | committed text, and an input method's composition |
| `isFocusable()` | whether `Tab` stops here. False by default |
| `focusScope()` | whether the descendants are one Tab stop, and which arrows rove |
| `onFocusChanged`, `onFocusWithin` | focus arriving at this node, or anywhere in its subtree |
| `wantsTextInput()` | whether the platform's text input is on while this has focus |
| `localPart()` | the part `local()` is measured against, as a CSS type name |
| `gestureAnchor()` | a number to remember for the length of a drag |
| `caretArea()`, `caretOffsetIn(area)` | where the caret is, for the input method's candidate window |

A `canvas` takes the same facts through its own `Input`, with `onPointer`,
`onKey`, `onText`, `onPreedit` and `wantsText()`, and it is focusable exactly
when it has one. The whole contract is in
[Writing a widget](writing-a-widget.md#input-and-focus).

## Read more

- [ADR-0054](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0054-hit-testing-runs-against-the-painted-frame.md): hit testing
- [ADR-0055](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0055-sdl-owns-keyboard-translation.md): keys and text
- [ADR-0056](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0056-the-wheel-is-lines-and-the-sign-is-ours.md): the wheel
- [ADR-0057](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0057-the-cursor-rides-on-the-painted-box.md): the cursor
- [ADR-0058](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0058-a-press-captures-the-pointer.md): a press captures
- [ADR-0073](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0073-a-composite-is-one-tab-stop.md): composites
- [ADR-0075](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0075-a-gestures-origin-is-the-routers.md): gestures
- [ADR-0378](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0378-the-desktops-own-modifier-has-a-name.md): the primary modifier
