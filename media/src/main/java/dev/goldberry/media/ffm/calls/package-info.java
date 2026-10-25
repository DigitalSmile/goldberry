/// The downcall holders, one record per upstream library and one nested class
/// per function: `Dav1dCalls`, `VpxCalls`, `OpusCalls`, `VorbisCalls`,
/// `TheoraCalls`, `FlacCalls`, `YuvCalls`, `Mp3Calls`, and `MediaCalls` for the
/// two functions of the library's own.
///
/// Each holder keeps its unbound handle as a `private static final FD_<symbol>`
/// and its address as a field, and has a `call` in ordinary Java types; its doc
/// is the C prototype the descriptor was written from. A package of their own so
/// that a native image's `--initialize-at-build-time` can name it, as
/// `:natives`' holders have.
///
/// Read more: [The module](https://goldberry.dev/docs/components/media.html#the-module).
@NullMarked
package dev.goldberry.media.ffm.calls;

import org.jspecify.annotations.NullMarked;
