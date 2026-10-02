/// SDL_GPU's enumerations: formats, usages, blend and compare operations,
/// vertex formats. They are tables of C constants that touch no foreign memory.
///
/// Split from the wrappers in `…natives.sdl.gpu`, which hold the handles, as
/// `blend2d.enums` and `harfbuzz.enums` are split from theirs. Exported to
/// `:core` and `:gpu` alone, with the wrappers.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
@NullMarked
package dev.goldberry.natives.sdl.gpu.enums;

import org.jspecify.annotations.NullMarked;
