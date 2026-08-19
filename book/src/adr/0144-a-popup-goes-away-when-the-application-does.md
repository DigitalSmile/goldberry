# 144. A popup goes away when the application does

Date: 2026-08-19

## Status

Accepted. Adds the focus half of the window SPI, which
[ADR-0103](0103-a-popup-is-a-second-tree-in-a-second-window.md)'s light dismissal
had been living without.

## Context

Open the showcase's menu, click on another application, and the menu is still
there — floating over the window you switched to, because a popup is
always-on-top by kind. Every desktop puts a menu away when its application stops
being the active one, and this one had no way to know: **there was no focus event
at all**, neither in `BackendEvent` nor in the SDL translation.

Light dismissal covered a press *inside* the owner window and `Escape`. Neither
of those happens when the user clicks somewhere else entirely.

## Decision

**`BackendEvent.FocusChanged(window, focused)`**, translated from
`SDL_EVENT_WINDOW_FOCUS_GAINED` and `SDL_EVENT_WINDOW_FOCUS_LOST` — two more
constants verified against the compiled C like every other one, which is the
check that caught them being unregistered on the first build.

Reported **per window**, because that is what every platform reports and the
honest thing for an SPI to carry. "The application lost focus" is a conclusion
drawn from the whole set, and only one thing needs to draw it, so only one thing
does: the launcher watches every window's focus through the runtime, and closes
every light-dismissed popup when none of them has it.

**The check is deferred by 60 ms**, and that is the whole mechanism rather than a
fudge. Opening a popup *is* a focus-lost for the window under it, immediately
followed by a focus-gained for the popup — so a menu that acted on the first of
those would close as it opened. One turn of the event loop later, both have
arrived and the question has its real answer.

## Consequences

**A menu no longer outlives the window that owns it.** The reported symptom, and
the worst kind of it: an always-on-top rectangle over somebody else's
application.

**A tooltip is unaffected**, because it opted out of light dismissal and this
goes through the same door. A tooltip is dismissed by the pointer leaving, which
is a different fact.

**60 ms is a number, and it is stated rather than tuned.** Short enough that
nobody sees the menu over the other application, long enough to cover a
focus pair the compositor delivers in two batches. If a driver is found that
takes longer, this is where it is written down.

**The headless backend never sends these events**, which is what makes them
testable: `PopupLifecycleTest` posts the pair a compositor would and asserts both
outcomes — the popup closes when focus left the application, and stays when it
merely moved to the popup itself. The second test is the one that would have
caught the naive implementation.
