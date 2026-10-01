/// The native-image metadata of the module's foreign calls: FFmpeg's and every
/// system decoder's, written from the bindings into one
/// `reachability-metadata.json` (ADR-0339).
///
/// Not exported: `:media:foreignMetadata` runs [MediaForeignMetadata] at build
/// time. It was two generators with two copies of the grammar while the system
/// decoders were a module of their own, and is one since they joined this one
/// (ADR-0493).
///
/// Marked for NullAway from its first commit (`docs/testing.md` §2).
@NullMarked
package dev.goldberry.media.nativeimage;

import org.jspecify.annotations.NullMarked;
