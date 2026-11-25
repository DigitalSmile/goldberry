/// What a native image of this module has to be told: the foreign calls the
/// ThorVG bindings make, and where the natives jar keeps the library, written
/// from the bindings by `:vector:foreignMetadata` rather than traced from a
/// run. Not exported; the build runs it.
///
/// Read more: [Native image](https://goldberry.dev/docs/native.html#two-metadata-directories-traced-and-written).
@NullMarked
package dev.goldberry.vector.nativeimage;

import org.jspecify.annotations.NullMarked;
