/// How often a window that is animating asks for its next frame, given whether
/// it can be seen, whether it has the focus and whether the desktop is saving
/// power.
///
/// Only the frames an animation asks for are paced here: a transition, a
/// keyframe animation, a spinner, anything that keeps the loop awake by
/// animating. A frame something asked for — a click, a state change, a new
/// video picture — is painted as soon as it is due, as before.
///
/// Internal to the toolkit; the window's own methods are what an application
/// calls.
///
/// Read more: [Windows](https://goldberry.dev/docs/guide/windows.html#the-lifecycle).
///
/// `@NullMarked`, which puts this package under NullAway.
@NullMarked
package dev.goldberry.frame.pacing;

import org.jspecify.annotations.NullMarked;
