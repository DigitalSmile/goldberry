/// Raw MPEG audio streams, the `.mp3` file: an ID3v2 tag, a stream of frames
/// each carrying its own header, and an ID3v1 tag at the end.
///
/// [dev.goldberry.media.container.mp3.Mp3Container] is the container;
/// [dev.goldberry.media.container.mp3.FrameHeader] reads the four bytes every
/// frame starts with; [dev.goldberry.media.container.mp3.VbrHeader] reads the
/// Xing, Info, VBRI and LAME tags of the first frame;
/// [dev.goldberry.media.container.mp3.Id3v2] skips the tag and takes its
/// pictures.
///
/// Read more: [The module](https://goldberry.dev/docs/components/media.html#the-module).
@NullMarked
package dev.goldberry.media.container.mp3;

import org.jspecify.annotations.NullMarked;
