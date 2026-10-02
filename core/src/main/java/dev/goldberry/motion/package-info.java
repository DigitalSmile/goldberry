/// Motion: transitions and keyframe animations, the easings they run on, and the
/// clock they read.
///
/// Animated values live in a per-node overlay applied at paint time and are never
/// written back into computed style, so the cascade and an animation cannot
/// fight. The clock is real in a window and virtual in a test, which is what
/// makes a picture of frame 3 of a transition the same on every machine.
/// Exported to applications.
///
/// `@NullMarked`, which puts this package under NullAway.
///
/// Read more: [The design system](https://goldberry.dev/docs/guide/design-system.html#motion).
@NullMarked
package dev.goldberry.motion;

import org.jspecify.annotations.NullMarked;
