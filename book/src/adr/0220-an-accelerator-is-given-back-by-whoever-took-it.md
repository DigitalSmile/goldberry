# 220. An accelerator is given back by whoever took it

Date: 2026-08-30

## Status

Accepted. Closes a `TODO.md` entry opened by
[ADR-0163](0163-a-menu-bar-owns-its-menus.md).

## Context

A `menubar` registers the accelerator of every command in its menus when it is
mounted, and gives them back when it is not — an accelerator is an entry in a map
that outlives the widget tree, so not giving them back is a leak of a kind a
widget is otherwise incapable of.

The window's map was keyed by the shortcut alone. So `removeShortcut(Ctrl+O)`
removed whatever was on `Ctrl+O`:

```java
new ElementTree(new MenuBar(new Item("File").submenu(
        new Item("Open…", …).accelerator("Ctrl+O"))), host);   // the bar binds Ctrl+O
host.shortcut(Shortcut.of("Ctrl+O"), openADocument);           // the application takes it over
tree.unmount();                                                // and the bar takes it away
```

The application's binding is gone, and nothing said so. `TODO.md` recorded it as
"two things claiming one key is already a conflict where the last registration
wins; this is that conflict at the other end", and named the fix: "the map
remembering owners, and `menubar` would be the only thing that used it".

## Decision

**The map remembers who bound each key.** A binding is `(action, owner)`, and the
owner is compared by **identity** — "who bound it" is a question about an object,
not about a value that might be equal to another one.

**Two ways to give a key back, and they mean different things.**

- `removeShortcut(shortcut)` removes whatever is bound. That is what an
  *application* unbinding its own key means, and it is the behaviour every
  existing caller had.
- `removeShortcut(shortcut, owner)` removes it **only if that owner still holds
  it**. That is what a widget giving back keys it took means, and it is a no-op
  when somebody else has taken the key since.

**The bind side is unchanged.** Two commands on one key is an authoring mistake
and the later registration still wins — silently refusing the second would make a
menu whose second `Ctrl+O` does nothing and says nothing. What changed is only
that the loser can no longer unbind the winner.

**A displaced binding is not restored.** The map holds one binding per key, so a
key the bar took *from* the application is not handed back when the bar goes away
— it is simply unbound. Restoring would mean a stack per key, and a stack is a
different feature with a different question in it ("which of the three things
that wanted `Ctrl+O` should fire after the second one leaves?"). The entry this
closes asks for neither.

**`menubar` is the only owner in the toolkit**, exactly as predicted. It passes
its own state object — the thing whose lifetime the bindings match.

## Alternatives considered

- **Returning a handle from `shortcut(…)` and unbinding through it.** The
  principled version: the token *is* the proof of ownership, and there is no way
  to spell "unbind somebody else's". It changes the signature of a published
  method every application already calls, and the check it performs internally is
  the same identity comparison this does — so it buys tidiness at the cost of the
  API's stability, for one caller.
- **Refusing a second binding for a key already taken.** It makes the collision
  loud, and it makes the last-writer-wins rule — which applications rely on to
  override a toolkit default — impossible.
- **A stack of bindings per key**, so unbinding restores the previous one. Real,
  and more machinery than the problem: it needs an answer for what happens when
  the *middle* of the stack goes away, and neither the bar nor an application has
  asked for one.
- **Leaving it and documenting it**, which is what ADR-0163 did. It survived one
  record; the entry stayed open because "a widget can silently unbind an
  application's key" is a bug that shows up as a shortcut that stopped working
  three screens later.

## Consequences

- **`Host` gains two methods**: `shortcut(Shortcut, Runnable, Object)` and
  `removeShortcut(Shortcut, Object)`. The four-year-old two-argument forms mean
  "owned by nobody", which is what an application's own binding is.
- **`Accelerators.bind`/`unbind` take an owner**, and `MenuBarState` passes
  `this`. The unowned overloads stay, because an application walking a `Menu` of
  its own is a legitimate caller with no owner to name.
- **`TestHost` mirrors the ownership**, because `MenuBarTest` asserts against it
  and a fixture that ignored the owner would pass the test the real router
  fails.
- **Two tests at each level.** In `:core`, that an owner gives back only what it
  still holds and that removing by key alone is unchanged; in `:widgets`, that
  unmounting a bar leaves a key the application took after it and still gives
  back the ones nobody took.
- **What is still open in this area**: a bare `Alt` tap does not activate the bar
  (`F10` does), which is a key-release rule and not a map.
