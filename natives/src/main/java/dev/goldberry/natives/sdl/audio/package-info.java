/// SDL audio output, exported to `goldberry-media` alone (ADR-0461).
///
/// The toolkit itself plays no audio. This package exists so the media engine's
/// audio sink can reach SDL, which is already linked into `libgoldberry`, without
/// a second audio library.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.natives.sdl.audio;

import org.jspecify.annotations.NullMarked;
