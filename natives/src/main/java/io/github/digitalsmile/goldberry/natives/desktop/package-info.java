/// What the desktop says that SDL does not ask it.
///
/// One question so far — whether to reduce motion — and three platforms that
/// answer it three ways: the XDG settings portal over D-Bus on Linux,
/// `SystemParametersInfoW` on Windows, and `NSWorkspace` on macOS
/// ([ADR-0383]).
///
/// Everything here fails **quietly and completely**: a missing library, a
/// missing service, a missing key, a refused call and an unexpected type all
/// answer [MotionPreference#UNKNOWN], which is what the toolkit did before any
/// of this existed. A desktop setting is worth asking for and is not worth
/// crashing over.
@org.jspecify.annotations.NullMarked
package io.github.digitalsmile.goldberry.natives.desktop;
