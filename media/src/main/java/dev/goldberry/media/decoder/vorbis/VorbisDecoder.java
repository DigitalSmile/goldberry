package dev.goldberry.media.decoder.vorbis;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.log.Logs;
import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.bitstream.XiphLacing;
import dev.goldberry.media.codec.AudioFrame;
import dev.goldberry.media.codec.Decoder;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.Received;
import dev.goldberry.media.codec.SampleFormat;
import dev.goldberry.media.ffm.MediaLibrary;
import dev.goldberry.media.ffm.NativeStructs;
import dev.goldberry.media.ffm.calls.VorbisCalls;

/// A Vorbis decoder over libvorbis: one packet in, one run of planar float
/// samples out, at the stream's rate.
///
/// The record a container hands over is the three header packets Xiph-laced,
/// as Matroska's `CodecPrivate` holds them and the Ogg reader laces them; each goes
/// through `vorbis_synthesis_headerin` as an `ogg_packet` built here, since
/// libvorbis speaks in those. An audio packet is synthesised into a block,
/// the block into the DSP state, and the samples ready are borrowed straight
/// from libvorbis's own planes: it keeps them until `vorbis_synthesis_read`
/// says they were taken, which is done before the next packet goes in, so
/// that a frame handed out stays valid until the next call, as the SPI
/// promises. The first packet after the headers, and after a flush, yields
/// nothing: Vorbis overlaps each block with the one before.
public final class VorbisDecoder implements Decoder {

    private static final Logger LOG = Logs.of(VorbisDecoder.class);

    private static final long PACKET_PACKET = NativeStructs.OGG_PACKET.byteOffset(groupElement("packet"));
    private static final long PACKET_BYTES = NativeStructs.OGG_PACKET.byteOffset(groupElement("bytes"));
    private static final long PACKET_BOS = NativeStructs.OGG_PACKET.byteOffset(groupElement("b_o_s"));
    private static final long PACKET_EOS = NativeStructs.OGG_PACKET.byteOffset(groupElement("e_o_s"));
    private static final long PACKET_GRANULEPOS = NativeStructs.OGG_PACKET.byteOffset(groupElement("granulepos"));
    private static final long PACKET_PACKETNO = NativeStructs.OGG_PACKET.byteOffset(groupElement("packetno"));
    /// `bytes`, `b_o_s` and `e_o_s` are C `long`s: this wide on this target.
    private static final long PACKET_LONG_SIZE =
            NativeStructs.OGG_PACKET.select(groupElement("bytes")).byteSize();
    private static final long INFO_CHANNELS = NativeStructs.VORBIS_INFO.byteOffset(groupElement("channels"));
    private static final long INFO_RATE = NativeStructs.VORBIS_INFO.byteOffset(groupElement("rate"));
    private static final long INFO_LONG_SIZE =
            NativeStructs.VORBIS_INFO.select(groupElement("rate")).byteSize();

    private final VorbisCalls vorbis;
    private final Arena arena = Arena.ofShared();
    private final MemorySegment info;
    private final MemorySegment comment;
    private final MemorySegment dsp;
    private final MemorySegment block;
    private final MemorySegment packet;
    private final MemorySegment pcmOut;
    private final int channels;
    private final int rate;
    private MemorySegment input;
    private long packetNumber;
    /// Samples handed out in the last frame, which libvorbis still holds until
    /// they are read off.
    private int unread;
    private @Nullable AudioFrame pending;
    private boolean ended;
    private boolean closed;

    /// Opens a decoder for `request`, a Vorbis track.
    ///
    /// @throws MediaException [MediaError.InvalidData] when the record is not
    ///                        three Vorbis headers libvorbis accepts
    public VorbisDecoder(MediaLibrary library, DecoderRequest request) {
        Objects.requireNonNull(request, "request");
        this.vorbis = library.vorbis();
        this.info = arena.allocate(NativeStructs.VORBIS_INFO);
        this.comment = arena.allocate(NativeStructs.VORBIS_COMMENT);
        this.dsp = arena.allocate(NativeStructs.VORBIS_DSP_STATE);
        this.block = arena.allocate(NativeStructs.VORBIS_BLOCK);
        this.packet = arena.allocate(NativeStructs.OGG_PACKET);
        this.pcmOut = arena.allocate(ADDRESS);
        this.input = arena.allocate(4096);
        var initialised = 0;
        try {
            vorbis.infoInit().call(info);
            vorbis.commentInit().call(comment);
            initialised = 2;
            var headers = headers(request);
            for (var i = 0; i < headers.size(); i++) {
                var header = headers.get(i);
                fill(arena.allocateFrom(JAVA_BYTE, header), header.length, i == 0, i);
                var code = vorbis.synthesisHeaderin().call(info, comment, packet);
                if (code != 0) {
                    throw new MediaException(
                            new MediaError.InvalidData("vorbis_synthesis_headerin refused header " + i + ": " + code));
                }
            }
            check("vorbis_synthesis_init", vorbis.synthesisInit().call(dsp, info));
            initialised = 3;
            check("vorbis_block_init", vorbis.blockInit().call(dsp, block));
            initialised = 4;
            this.channels = info.get(JAVA_INT, INFO_CHANNELS);
            this.rate = Math.toIntExact(cLong(info, INFO_RATE, INFO_LONG_SIZE));
            if (channels <= 0 || rate <= 0) {
                throw new MediaException(
                        new MediaError.InvalidData("a Vorbis stream of " + channels + " channels at " + rate + " Hz"));
            }
            this.packetNumber = headers.size();
        } catch (RuntimeException | Error e) {
            clear(initialised);
            arena.close();
            throw e;
        }
    }

    /// The three headers `request`'s record laces.
    private static List<byte[]> headers(DecoderRequest request) {
        var extradata = request.extradata();
        if (extradata.byteSize() == 0) {
            throw new MediaException(new MediaError.InvalidData("a Vorbis track without its headers"));
        }
        List<byte[]> headers;
        try {
            headers = XiphLacing.split(extradata.toArray(JAVA_BYTE));
        } catch (IllegalArgumentException e) {
            throw new MediaException(new MediaError.InvalidData(String.valueOf(e.getMessage())), e);
        }
        if (headers.size() != 3) {
            throw new MediaException(
                    new MediaError.InvalidData("a Vorbis record of " + headers.size() + " packets, not three"));
        }
        return headers;
    }

    @Override
    public boolean send(Packet packet) {
        requireOpen();
        if (ended) {
            throw new IllegalStateException("a packet after the end");
        }
        if (pending != null) {
            return false;
        }
        consumed();
        var bytes = packet.data();
        var length = Math.toIntExact(bytes.byteSize());
        if (length == 0) {
            return true;
        }
        if (input.byteSize() < length) {
            input = arena.allocate(Math.max(length, input.byteSize() * 2));
        }
        MemorySegment.copy(bytes, 0, input, 0, length);
        fill(input, length, false, packetNumber++);
        var code = vorbis.synthesis().call(block, this.packet);
        if (code != 0) {
            // A packet libvorbis cannot read is skipped, as FFmpeg's wrapper
            // skips it: the stream goes on from the next one.
            LOG.debug("vorbis_synthesis refused a packet of {} bytes: {}", length, code);
            return true;
        }
        check("vorbis_synthesis_blockin", vorbis.synthesisBlockin().call(dsp, block));
        var samples = vorbis.synthesisPcmout().call(dsp, pcmOut);
        if (samples <= 0) {
            return true;
        }
        pending = new AudioFrame(SampleFormat.F32_PLANAR, rate, channels, samples, planes(samples), packet.ptsNanos());
        unread = samples;
        return true;
    }

    /// The planes libvorbis filled, borrowed: `*pcm` points at one float
    /// pointer per channel.
    @SuppressWarnings("restricted")
    private List<MemorySegment> planes(int samples) {
        var pointers = pcmOut.get(ADDRESS, 0).reinterpret(ADDRESS.byteSize() * channels);
        var planes = new ArrayList<MemorySegment>(channels);
        for (var channel = 0; channel < channels; channel++) {
            planes.add(pointers.getAtIndex(ADDRESS, channel).reinterpret((long) samples * JAVA_FLOAT.byteSize()));
        }
        return planes;
    }

    @Override
    public void sendEnd() {
        requireOpen();
        ended = true;
    }

    @Override
    public Received receive() {
        requireOpen();
        var frame = pending;
        if (frame != null) {
            pending = null;
            return new Received.Decoded(frame);
        }
        consumed();
        return ended ? Received.ENDED : Received.NEEDS_INPUT;
    }

    @Override
    public void flush() {
        requireOpen();
        pending = null;
        unread = 0;
        ended = false;
        check("vorbis_synthesis_restart", vorbis.synthesisRestart().call(dsp));
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        pending = null;
        clear(4);
        arena.close();
    }

    /// The channel count the headers state, for tests.
    int channels() {
        return channels;
    }

    /// The rate the headers state, for tests.
    int rate() {
        return rate;
    }

    /// Tells libvorbis that the last frame's samples were taken, once.
    private void consumed() {
        if (unread > 0) {
            vorbis.synthesisRead().call(dsp, unread);
            unread = 0;
        }
    }

    /// Writes the `ogg_packet` libvorbis reads: `data`'s first `length` bytes,
    /// no granule position, numbered.
    private void fill(MemorySegment data, int length, boolean first, long number) {
        packet.set(ADDRESS, PACKET_PACKET, data);
        setCLong(packet, PACKET_BYTES, PACKET_LONG_SIZE, length);
        setCLong(packet, PACKET_BOS, PACKET_LONG_SIZE, first ? 1 : 0);
        setCLong(packet, PACKET_EOS, PACKET_LONG_SIZE, 0);
        packet.set(JAVA_LONG, PACKET_GRANULEPOS, -1);
        packet.set(JAVA_LONG, PACKET_PACKETNO, number);
    }

    /// Frees what was initialised, in the reverse order: the block, the DSP
    /// state, then the comment and the info.
    private void clear(int initialised) {
        if (initialised >= 4) {
            vorbis.blockClear().call(block);
        }
        if (initialised >= 3) {
            vorbis.dspClear().call(dsp);
        }
        if (initialised >= 2) {
            vorbis.commentClear().call(comment);
            vorbis.infoClear().call(info);
        }
    }

    private static void setCLong(MemorySegment struct, long offset, long size, long value) {
        if (size == 8) {
            struct.set(JAVA_LONG, offset, value);
        } else {
            struct.set(JAVA_INT, offset, Math.toIntExact(value));
        }
    }

    private static long cLong(MemorySegment struct, long offset, long size) {
        return size == 8 ? struct.get(JAVA_LONG, offset) : struct.get(JAVA_INT, offset);
    }

    private static void check(String what, int code) {
        if (code != 0) {
            throw new MediaException(new MediaError.InvalidData(what + ": " + code));
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("decoder closed");
        }
    }
}
