/// The SDL event vocabulary Goldberry dispatches on: the `SDL_EventType` values that
/// have a consumer, and which way round a wheel event's values are. Plain constants
/// that touch no foreign memory.
///
/// Exported to every module.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.sdl.event;

import org.jspecify.annotations.NullMarked;
