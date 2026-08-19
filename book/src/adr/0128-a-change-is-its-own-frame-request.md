# 128. A change is its own frame request

Date: 2026-08-19

## Status

Accepted. Finishes [ADR-0125](0125-a-raw-field-is-woven-into-a-binding.md): the
model stopped holding containers, and this is it giving up the last thing it was
doing on the toolkit's behalf.

## Context

Every action in the showcase ended the same way:

```java
@Action("app.click")
public void click() {
    clicks++;
    changed();          // <- ask the window to repaint
}
```

Nine methods, nine `changed()` calls, and a `Runnable onChanged` field plus a
setter to install it. The line has no meaning of its own. It is never *wrong* —
it is only ever **missing**, and when it is missing the symptom is a value that
changed and a window that did not repaint until something else happened to move.
That is among the worst bugs a toolkit can hand somebody, because the code that
is wrong looks complete.

It was also imprecise in the other direction. `reset()` called `changed()`
whether or not `clicks` was already zero, so a button that did nothing still
asked for a frame.

## Decision

**A `@Bind` field changing *is* the frame request.**

`FieldListeners` gained a second kind of listener — one that wants to know that
*something* moved, without caring what — and `Models.onChange(model, runnable)`
is where a window subscribes:

```java
Models.onChange(model, host::repaint);
```

An action is now an assignment and nothing else.

Fired **once per change and not once per write**, which is the same rule
everything else in the binding layer follows: a model that assigns the value
already there notifies nobody and asks for no frame. So `reset()` on a zero
counter is now genuinely free.

Fired **after** the per-field listeners for that change, so a subscriber watching
one path has already run by the time the frame is asked for. That ordering is
what let the showcase's other callback go too: `onRestyle` was a second hook the
model had to remember to call, and a stylesheet depends on exactly two values, so
it became two subscriptions:

```java
Models.observable(model, "app.theme").subscribe(value -> restyle.run());
Models.observable(model, "app.density").subscribe(value -> restyle.run());
```

`density` was an unbound field and is now bound, which is what made that
possible. Nothing displays it — but "nothing displays it" was never the same
question as "does anything depend on it".

## Consequences

`ShowcaseModel` lost `onChanged`, `onRestyle`, both setters and nine `changed()`
calls. It now contains fields, methods that assign to them, and four derived
getters. There is nothing in it about frames.

**An action that moves three fields asks for three frames.** That is not a
regression: the frame scheduler already coalesces requests within a frame, for
the same reason it coalesces three `setState` calls (ADR-0122). It is worth
stating because the old code asked exactly once per action by construction, and
this asks per change.

**A model that changes something the UI depends on but does not bind still needs
telling.** Nothing enforces that a value the view reads is a `@Bind` field —
`ShowcaseModel.added` is deliberately not one. The rule is now "if the UI depends
on it, bind it", which is a better rule than "remember to call `changed()`", but
it is still a rule.

**The listener runs on the write.** A model that assigns a field in a tight loop
calls `repaint` once per iteration. Nothing in the toolkit did that, and the
frame scheduler makes it cheap, but a model doing real batch work should assign
its result once rather than accumulate in a bound field — which was already true
of `Property`.
