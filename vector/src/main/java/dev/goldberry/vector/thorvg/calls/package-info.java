/// The downcall holders, one record per part of the C API and one nested class
/// per function: `EngineCalls`, `CanvasCalls`, `PictureCalls`, and
/// `VectorCalls` for the two functions of the library's own.
///
/// Each holder keeps its unbound handle as a `private static final FD_<symbol>`
/// and its address as a field, and has a `call` in ordinary Java types; its doc
/// is the C prototype the descriptor was written from. A package of their own so
/// that a native image's `--initialize-at-build-time` can name it, as
/// `:media`'s holders have.
///
/// Read more: [Images](https://goldberry.dev/docs/components/drawing.html#image).
@NullMarked
package dev.goldberry.vector.thorvg.calls;

import org.jspecify.annotations.NullMarked;
