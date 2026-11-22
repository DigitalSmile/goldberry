<!-- Destination: book/src/guide/windows.md, a new `## When nobody is looking`
     after `## Fullscreen`. Written 2026-11-22. -->

## When nobody is looking

A window that is animating asks for its next frame only as often as it can be
seen and as the desktop allows.

| The window is | Animation frames |
|---|---|
| covered by other windows, on another Space, hidden or minimized | none, until it can be seen |
| on a desktop saving power | at most 30 a second |
| not focused, with an inactive cap set | at most the cap a second |
| anything else | as fast as it presents |

Only the frames an animation asks for are held: a transition, a keyframe
animation, a spinner. A click, a state change or a new video picture is painted
as soon as it is due. An animation that waited catches up to the clock on the
first frame that is seen.

Occlusion is what the platform reports: macOS reports it, and X11 does under a
window manager that sends visibility events. A desktop that does not say leaves
the window counted as visible.

An unfocused window is not slowed by default, since the window beside the one
being typed in is still being looked at and a video in it is still being
watched. An application whose motion is decoration asks for it:

```java
window.inactiveFrameCap(4);
```

Or it pauses the motion itself, from the stylesheet:

```css
.attention:window-inactive { animation-play-state: paused }
```

Or from code, with the window's own signals:

```java
window.onFocusChanged(focused -> ticker.paused(!focused));
window.onOcclusionChanged(hidden -> feed.paused(hidden));
window.onPowerChanged(power -> sparkles.setVisible(!power.onBattery()));
```

`window.powerState()` says where the power comes from, how much battery is
left, and whether the desktop's saving mode is on: macOS's Low Power Mode,
Windows' battery saver, or a Linux desktop's power-saver profile. No platform
announces a change, so a window reads it again every 20 seconds and tells
`onPowerChanged` what changed. While the saving mode is on, the window holds
its animations to 30 frames a second by itself; running on battery alone
changes nothing until the application decides it should.
