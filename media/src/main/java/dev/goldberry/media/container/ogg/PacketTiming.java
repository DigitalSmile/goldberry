package dev.goldberry.media.container.ogg;

import dev.goldberry.media.bitstream.OpusPackets;

/// How long a packet of one logical stream plays, in samples of its rate:
/// what Ogg does not write and a page's granule position only sums.
///
/// Each codec says in its own way: Opus in the packet's first byte, FLAC in
/// the frame header, and Vorbis by the block sizes of this packet and the
/// one before, which makes it the one with state. A seek resets that state,
/// so the first packet read after one is measured as the stream's first was,
/// which is what FFmpeg's parser does too and what its packet list shows.
sealed interface PacketTiming {

    /// The samples `packet` plays, or 0 when the packet does not say.
    int duration(byte[] packet);

    /// Forgets the packet before: the next one is measured as a stream's first.
    default void reset() {}

    /// An Opus packet's TOC.
    record Opus() implements PacketTiming {
        @Override
        public int duration(byte[] packet) {
            return OpusPackets.samples(packet);
        }
    }

    /// A FLAC frame header's block size.
    record Flac() implements PacketTiming {
        @Override
        public int duration(byte[] packet) {
            return FlacHeaders.blockSize(packet);
        }
    }

    /// A stream whose packets are not measured: nothing is known of the codec.
    record None() implements PacketTiming {
        @Override
        public int duration(byte[] packet) {
            return 0;
        }
    }

    /// Vorbis: a packet plays a quarter of its block and a quarter of the one
    /// before, the two windows overlapping. Before any packet, the block before
    /// is taken to be mode 0's, as FFmpeg's parser takes it, so that the first
    /// packet of a stream and of a seek is timed the same way here as there.
    final class Vorbis implements PacketTiming {

        private final VorbisHeaders.Identification identification;
        private final VorbisHeaders.Modes modes;
        private int previous;

        Vorbis(VorbisHeaders.Identification identification, VorbisHeaders.Modes modes) {
            this.identification = identification;
            this.modes = modes;
            reset();
        }

        @Override
        public int duration(byte[] packet) {
            int current;
            try {
                current = blocksize(modes.isLong(packet));
            } catch (IllegalArgumentException e) {
                return 0;
            }
            var duration = (previous + current) / 4;
            previous = current;
            return duration;
        }

        @Override
        public void reset() {
            previous = blocksize(modes.longBlock()[0]);
        }

        private int blocksize(boolean isLong) {
            return isLong ? identification.blocksize1() : identification.blocksize0();
        }
    }
}
