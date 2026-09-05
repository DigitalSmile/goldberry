# 264. A widget may find the toast stack

Date: 2026-09-05

## Status

Accepted. Closes the last open half of the overlay-layer entry.

## Context

The entry had already shrunk once and said so:

> `BuildContext.host()` exists now … so a control that must open something for
> itself can. What that answers is the *popup* half; **`Host.overlay` is still the
> door a `toast` raised from a handler deep in the tree would want**, and nothing
> wraps it in the `Overlay.of(context)`-shaped call that would put a toast up
> from there without the application's help. Smaller than it was, and the same
> shape.

`Toasts.at(host, controller, corner)` mounts a stack, and from then on the
*application* raises toasts through the `ToastController` it holds in a field.
That works for an application and not for a **widget**: a control deep in the
tree that wanted to say "Saved" had to be handed a callback by whoever built it,
and every layer in between had to carry one.

`BuildContext.findAncestorState` — the mechanism that answers this shape for
`scroll` and `form` — cannot. A toast stack is mounted in the **overlay layer**,
which is a *sibling* of the application's root under `window-root`, not an
ancestor of anything inside it. Walking up from a deep widget reaches
`window-root` and stops.

## Decision

**`Toasts.of(BuildContext)`**, returning the stack on this widget's window if one
has been attached.

```java
Toasts.of(context).ifPresent(toasts -> toasts.show("Saved"));
```

`BuildContext.host()` already gives the window, and `Toasts.at` is the one place
that knows which stack is on it. So the whole mechanism is a map from the first
to the second, and the only real decision is **where it lives**.

### Not on `Host`

The obvious shape is `host.toasts()`, or a general `host.service(Class)`. Both
put `:core` in the position of knowing what a toast is — and the reason `Toasts`,
`Menus` and `Dialogs` are three classes in `:widgets` rather than three methods
on the window is precisely that it must not. A general service locator is the
same problem with the type erased: it would let anything be registered against a
window, which is a larger mechanism than one lookup and a worse one to have
guessed at from a single consumer.

So the map is here, keyed by `Host`.

### Weak on the key, and plain

`WeakHashMap`, so a window that goes away takes its entry with it. A `Host`
outliving the application that made it is the one leak a convenience like this
could cause, and nothing else in this class is in a position to notice a window
closing.

Not a concurrent map, for the reason already at the top of the file: everything
that touches a `Host` is on the UI thread, and a second thread reaching a toast
stack has a larger problem than this map.

### Last attachment wins

`at` returns an `Overlay` handle so a window can move its toasts to another
corner. The lookup follows the most recent attachment, because handing out a
controller whose overlay has stopped drawing is the one answer that is certainly
wrong.

### Empty is an answer, twice

A widget built into a tree with **no window** — which is what most unit tests are
— and a window whose application never called `at`. Neither is a fault. A control
that threw on either would be a control that cannot be tested without a window,
and a toast nobody arranged to show is a toast that does not appear, which is
what the application decided by not attaching a stack.

## Alternatives considered

- **`host.toasts()`.** `:core` learns what a toast is, and the module boundary
  that keeps the catalog swappable stops meaning anything.
- **A general `host.service(Class)`.** One consumer is not enough to design a
  service locator against — the argument `BuildContext.host()` itself was held to
  ([ADR-0140](0140-a-widget-may-reach-its-window.md)), which waited for `select`
  to be the second. If `Menus` and `Dialogs` grow the same need, that is the
  moment, and this map is what would be replaced.
- **Making the stack an ancestor so `findAncestorState` works.** It would put the
  toast stack *inside* the application's tree, which is exactly what the overlay
  layer exists to avoid: an overlay takes no space from the content and is
  painted after it ([ADR-0100](0100-a-window-has-a-layer-above-its-application.md)).
- **Threading a callback down.** What applications do today, and what the entry
  is about. It works and it costs every intermediate widget a parameter it does
  not otherwise want.

## Consequences

- **A control can raise a toast**, and nothing between it and the window has to
  know. That is the whole of §7's "something that just happened" being available
  where things happen.
- **`Toasts` holds static state**, which it did not before. It is one weak map,
  documented, with a package-private `forgetAttachments` for tests — the fifth
  such pair in the toolkit, and the point at which the pattern is worth
  extracting was already noted in [ADR-0257](0257-a-diagnostic-is-asked-for-not-logged.md).
- **Seven tests**, including the two ways of answering nothing and the two windows
  not seeing each other's stacks. One of them mounts the stack as well as
  attaching it, because `TestHost.overlay` records rather than builds — a
  controller registered by `at` alone is still *detached* and swallows what it is
  shown, which would have made the test pass by asserting nothing.
- **`Menus` and `Dialogs` are unchanged**, and now visibly asymmetric with this.
  Neither has been asked for; when one is, the question of whether these three
  share a mechanism is the one to answer rather than repeating this map twice.
