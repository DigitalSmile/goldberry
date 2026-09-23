/// SDL audio output, exported to `goldberry-media` alone (ADR-0461).
///
/// The toolkit itself plays no audio. This package exists so the media engine's
/// audio sink can reach SDL, which is already linked into `libgoldberry`, without
/// a second audio library.
package io.github.digitalsmile.goldberry.natives.sdl.audio;
