/// SDL audio, exported to `goldberry-media` and `goldberry-mic` alone.
///
/// The toolkit itself plays and records nothing. This package exists so the
/// media engine's audio sink, and the microphone module's recordings, can reach
/// SDL, which is already linked into `libgoldberry`, without a second audio
/// library.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.sdl.audio;

import org.jspecify.annotations.NullMarked;
