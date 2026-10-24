/// The containers, read in Java: what a file holds and the packets in it.
///
/// [dev.goldberry.media.container.Container] is the shape the Engine's demux
/// thread drives; [dev.goldberry.media.container.Containers] sniffs a source's
/// first bytes and opens the right one. Each container is a package of its
/// own under this one: `matroska` (which is WebM too), `mp4`, `ogg`, and the
/// raw audio streams. [dev.goldberry.media.container.ByteReader] is the buffered
/// view over a `MediaIO` they all read through.
///
/// Not exported: an application sees tracks and packets through the player
/// and the probe, never a container.
///
/// Read more: [The module](https://goldberry.dev/docs/components/media.html#the-module).
@NullMarked
package dev.goldberry.media.container;

import org.jspecify.annotations.NullMarked;
