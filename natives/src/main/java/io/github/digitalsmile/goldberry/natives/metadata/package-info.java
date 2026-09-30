/// The foreign half of this module's GraalVM reachability metadata, found from the
/// classes rather than from a traced run (ADR-0339).
///
/// Run by `:natives:foreignMetadata` before the jar is built. The file it writes
/// travels in the jar under `META-INF/native-image/`, where `native-image` finds it
/// for any application with this module on its path. Not exported.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.natives.metadata;

import org.jspecify.annotations.NullMarked;
