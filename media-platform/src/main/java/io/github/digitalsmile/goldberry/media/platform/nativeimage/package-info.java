/// The native-image metadata of the module's foreign calls, written from the
/// bindings of every system's package (ADR-0339).
///
/// Not exported: `:media-platform:foreignMetadata` runs it at build time.
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package io.github.digitalsmile.goldberry.media.platform.nativeimage;

import org.jspecify.annotations.NullMarked;
