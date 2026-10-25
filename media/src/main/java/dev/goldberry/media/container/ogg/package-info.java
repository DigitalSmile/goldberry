/// Ogg: pages, the packets they carry across their boundaries, and the four
/// codecs the mapping names — Opus, Vorbis, FLAC and Theora — read into tracks
/// and packets.
///
/// [dev.goldberry.media.container.ogg.OggContainer] is the container;
/// [dev.goldberry.media.container.ogg.OggPage] reads one page;
/// [dev.goldberry.media.container.ogg.OggStream] is one logical stream, named
/// by its first packet; [dev.goldberry.media.container.ogg.PacketTiming] says
/// how long each codec's packets are, which Ogg does not write, with the
/// headers [dev.goldberry.media.container.ogg.VorbisHeaders] and
/// [dev.goldberry.media.container.ogg.FlacHeaders] read for it, and
/// [dev.goldberry.media.bitstream.TheoraHeaders] for a Theora stream's granule
/// positions.
///
/// Read more: [The module](https://goldberry.dev/docs/components/media.html#the-module).
@NullMarked
package dev.goldberry.media.container.ogg;

import org.jspecify.annotations.NullMarked;
