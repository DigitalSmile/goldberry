/// MP4, the ISO base media file format and QuickTime: the `moov` box's tracks
/// and sample tables, read into tracks and packets.
///
/// [dev.goldberry.media.container.mp4.Mp4Container] is the container;
/// [dev.goldberry.media.container.mp4.BoxReader] walks the boxes;
/// [dev.goldberry.media.container.mp4.SampleTable] flattens a track's `stbl`
/// into one row per sample; [dev.goldberry.media.container.mp4.SampleEntries]
/// turns an `stsd` entry into the Decoder SPI's vocabulary;
/// [dev.goldberry.media.container.mp4.EditList] is what of the `elst` box is
/// honoured.
///
/// Read more: [The module](https://goldberry.dev/docs/components/media.html#the-module).
@NullMarked
package dev.goldberry.media.container.mp4;

import org.jspecify.annotations.NullMarked;
