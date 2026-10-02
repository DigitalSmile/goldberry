/// The native-image metadata of the module's foreign calls: FFmpeg's and every
/// system decoder's, written from the bindings into one
/// `reachability-metadata.json`. A foreign call is registered because it
/// exists, not because a traced run reached it.
///
/// Not exported: `:media:foreignMetadata` runs [MediaForeignMetadata] at build
/// time. Null-marked.
///
/// Read more: [The module](https://goldberry.dev/docs/components/media.html#the-module).
@NullMarked
package dev.goldberry.media.nativeimage;

import org.jspecify.annotations.NullMarked;
