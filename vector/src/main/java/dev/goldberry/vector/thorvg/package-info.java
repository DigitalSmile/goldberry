/// The bindings to `libgoldberry-vector`: the Foreign Function and Memory side of
/// the module, and nothing above it.
///
/// `libgoldberry-vector` is one shared library per target that links ThorVG
/// statically and exports its C API. This package loads it
/// ([dev.goldberry.vector.thorvg.VectorNatives]), checks the ABI the superbuild
/// wrote beside it and the one the library answers, and binds every function
/// this module calls, one holder per symbol under `…thorvg.calls`.
/// [dev.goldberry.vector.thorvg.VectorLibrary] is the result.
///
/// Not exported. No ThorVG handle appears in anything that is.
///
/// Read more: [Images](https://goldberry.dev/docs/components/drawing.html#image).
@NullMarked
package dev.goldberry.vector.thorvg;

import org.jspecify.annotations.NullMarked;
