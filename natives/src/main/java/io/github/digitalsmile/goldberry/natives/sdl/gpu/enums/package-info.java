/// SDL_GPU's enumerations: formats, usages, blend and compare operations,
/// vertex formats. They are tables of C constants that touch no foreign memory.
///
/// Split from the wrappers in `…natives.sdl.gpu`, which hold the handles, as
/// `blend2d.enums` and `harfbuzz.enums` are split from theirs (ADR-0172,
/// ADR-0496). Exported to `:core` and `:gpu` alone, with the wrappers.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.natives.sdl.gpu.enums;

import org.jspecify.annotations.NullMarked;
