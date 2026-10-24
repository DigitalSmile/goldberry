/// Matroska and WebM: the EBML Segment's Info, Tracks, Cues, Attachments and
/// Clusters, read into tracks and packets.
///
/// [dev.goldberry.media.container.matroska.MatroskaContainer] is the container;
/// [dev.goldberry.media.container.matroska.MatroskaCodecs] turns a `CodecID`
/// string and a track's entry into the Decoder SPI's vocabulary;
/// [dev.goldberry.media.container.matroska.Lacing] splits a block into its
/// frames; [dev.goldberry.media.container.matroska.Cues] is the seek index.
///
/// Read more: [The module](https://goldberry.dev/docs/components/media.html#the-module).
@NullMarked
package dev.goldberry.media.container.matroska;

import org.jspecify.annotations.NullMarked;
