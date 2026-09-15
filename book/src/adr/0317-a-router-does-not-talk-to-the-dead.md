# 317. A router does not talk to the dead

Date: 2026-09-15

## Status

Accepted. Closes `docs/gaps.md` G31. Completes
[ADR-0303](0303-the-router-lets-go-of-what-the-pointer-was-over.md), which made
the router let go of what the *pointer* was over and left the same hole one field
along.

## Context

Closing a surface that had the keyboard — a palette, a sheet, a panel with a field
in it — took the window down on the next frame:

```
java.lang.IllegalStateException: setState() on a state that is not mounted.
  at ...widget.State.setState(State.java:77)
  at ...form.textinput.TextInputState.focusChanged(TextInputState.java:504)
  at ...form.textinput.TextField.onFocusChanged(TextField.java:151)
  at ...input.PointerRouter.notifyFocus(PointerRouter.java:953)
  at ...input.PointerRouter.focus(PointerRouter.java:932)
  at ...input.PointerRouter.refocus(PointerRouter.java:299)
  at ...input.PointerRouter.updateRegions(PointerRouter.java:151)
  at ...Launcher.paint(Launcher.java:502)
```

**Every party in that stack is behaving correctly**, which is what made it worth
an ADR rather than a patch.

`refocus` is doing what its own javadoc promises — *"the router never holds an
element that is not in the tree"* — and the check it makes is the right one:

```java
if (focused == null || focused.isMounted()) {
    return;
}
…
focus(null, false);            // or focus(restoreTo, …)
```

Two lines later it hands that same element, which it has just established is **not
mounted**, to `focus`, which tells it so:

```java
if (lost != null && lost != focused) {
    notifyFocus(lost, false, fromKeyboard);
}
```

`focus` is right to notify `lost`: that is its contract for every ordinary focus
change, and a control that was not told it lost the keyboard would keep its caret
blinking. What it cannot know is that *this particular caller* is reporting a
**death** rather than a move.

And `State.setState` is right to throw. Its javadoc says an unmounted `setState`
means *"a callback outlived the widget that registered it, which is a leak worth
hearing about"*, and that is exactly the class of bug it catches everywhere else.

It is not overlay-specific and not application-specific. Any tree where a focused
control disappears reaches it, and `refocus`'s own javadoc names three: a tab that
switched, a list that shortened, a `dialog` with a field in it that closes on its
own button.

## Decision

**One clause, in the place that already knows.**

```java
// PointerRouter.focus(Element, boolean)
if (lost != null && lost != focused && lost.isMounted()) {
    notifyFocus(lost, false, fromKeyboard);
}
```

and the same guard in `notifyFocusWithin`, which walks the same two chains:

```java
for (var element : left) {
    if (!shared.contains(element) && element.isMounted() && element.widget() instanceof Handles handles) {
        handles.onFocusWithin(false, fromKeyboard);
    }
}
```

**Per element, not per notification.** The `:focus-within` chain from a dead node
runs up through its dead containers and then into ancestors that are still there —
a window whose sheet just closed really has lost focus-within, and it is told. Only
the elements that went away are skipped.

`mark` — the pseudo-class half of the same method — has had `element.isMounted()`
in it from the beginning, for the same reason and without anyone writing it down.
This makes the notification half agree with it.

## Consequences

An unmounted element has already been disposed: its state's `dispose` has run, its
bindings are closed and its subtree is gone. There is nobody left to tell, so
nothing is lost by not telling them — which is the whole argument that this is a
fix and not a suppression. The one thing a control could have wanted from a final
`onFocusChanged(false)` is to release something, and `dispose` is where that
belongs and already runs.

`State.setState`'s complaint keeps its meaning. It was the messenger here, and a
router that stops creating the one legitimate case makes every remaining one a real
leak again.

## Alternatives considered

**A flag on `focus`, so `refocus` can say "this is a death".** An extra boolean
through a method four other routes call, to describe a condition the callee can
observe for itself. The guard is cheaper and cannot be passed wrongly.

**Make `State.setState` tolerate it.** This was the tempting one, because it would
have fixed every caller at once. It would also have thrown away the assertion that
catches real leaks — the javadoc says so — and an application's own stale callback
would then fail silently instead of loudly.

**Let the application avoid the position.** What brd did meanwhile: move the
keyboard back to the content *before* closing an overlay, so `refocus` finds a
mounted element and returns at the first check. It is good behaviour on its own
terms and it is not a fix: an application cannot intercept a focus change it never
sees, and `PointerRouter` is `:core`'s and installed by the launcher.
