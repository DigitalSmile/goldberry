/// The foreign half of this module's GraalVM reachability metadata, found from the
/// classes rather than from a traced run, so a holder is registered because it
/// exists and not because a run happened to reach it.
///
/// Run by `:natives:foreignMetadata` before the jar is built. The file it writes
/// travels in the jar under `META-INF/native-image/`, where `native-image` finds it
/// for any application with this module on its path. Not exported.
///
/// Marked for NullAway, so a parameter that may be null says so on its signature.
///
/// Read more:
/// [Two metadata directories](https://goldberry.dev/docs/native.html#two-metadata-directories-traced-and-written).
@NullMarked
package dev.goldberry.natives.metadata;

import org.jspecify.annotations.NullMarked;
