/// The SDL event vocabulary Goldberry dispatches on: the `SDL_EventType` values that
/// have a consumer, and which way round a wheel event's values are. Plain constants
/// that touch no foreign memory.
///
/// Exported to every module.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.sdl.event;

import org.jspecify.annotations.NullMarked;
