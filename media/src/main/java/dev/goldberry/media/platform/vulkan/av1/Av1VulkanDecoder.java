package dev.goldberry.media.platform.vulkan.av1;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.bitstream.av1.Av1Parser;
import dev.goldberry.media.bitstream.av1.Frame;
import dev.goldberry.media.bitstream.av1.FrameHeader;
import dev.goldberry.media.bitstream.av1.References;
import dev.goldberry.media.bitstream.av1.SequenceHeader;
import dev.goldberry.media.bitstream.av1.ShowExistingFrame;
import dev.goldberry.media.bitstream.av1.TileGroup;
import dev.goldberry.media.codec.ColorDescription;
import dev.goldberry.media.codec.DecoderRequest;
import dev.goldberry.media.codec.Packet;
import dev.goldberry.media.codec.PixelFormat;
import dev.goldberry.media.platform.vulkan.CodecDecoder;
import dev.goldberry.media.platform.vulkan.HostPictures;
import dev.goldberry.media.platform.vulkan.StreamSignal;
import dev.goldberry.media.platform.vulkan.VideoCapabilities;
import dev.goldberry.media.platform.vulkan.VideoClaim;
import dev.goldberry.media.platform.vulkan.VideoDevice;
import dev.goldberry.media.platform.vulkan.VideoProfile;
import dev.goldberry.media.platform.vulkan.VideoSession;
import dev.goldberry.media.platform.vulkan.VulkanCodec;

/// AV1 on Vulkan Video: each temporal unit read OBU by OBU, each frame told to
/// the decode with its header and the place of its header and tiles in the
/// unit, and copied back when it is shown, as NV12 or P010.
///
/// The session is made at the first key frame, at the sequence header's
/// largest frame size; its parameters are the sequence header, made again
/// when another comes. Main profile, 4:2:0 of 8 or 10 bits, without film grain
/// or superres, which the built-in decoder applies: a stream that needs either
/// is not claimed, and a frame that turns to superres is refused, which moves
/// the Engine to the next decoder.
public final class Av1VulkanDecoder implements CodecDecoder {

    private final VideoDevice device;
    private final HostPictures pictures;
    private final byte[] record;
    private final Av1Slots slots = new Av1Slots();
    private @Nullable Av1Parser parser;
    private @Nullable VideoSession session;
    private @Nullable SequenceHeader sessionSequence;
    private @Nullable SequenceHeader parametersSequence;
    private long parameters;

    // The frame whose tile groups are being gathered.
    private @Nullable FrameHeader frame;
    private References frameReferences = References.NONE;
    private Av1Pictures.@Nullable Layout layout;

    /// A decoder of `request`'s track on `device`, its pictures copied into
    /// `pictures`.
    public Av1VulkanDecoder(VideoDevice device, DecoderRequest request, HostPictures pictures) {
        this.device = device;
        this.pictures = pictures;
        this.record = request.extradata().toArray(JAVA_BYTE);
    }

    /// What `request`'s track needs of the device: the Main profile at the
    /// sequence header's largest size, from its `av1C`; empty for a track
    /// without one, or one of another profile, chroma format or depth, or with
    /// film grain.
    public static Optional<VideoClaim> claim(DecoderRequest request) {
        if (request.extradata().byteSize() == 0) {
            return Optional.empty();
        }
        try {
            return SequenceHeader.fromAv1C(request.extradata().toArray(JAVA_BYTE))
                    .flatMap(Av1VulkanDecoder::profile)
                    .flatMap(profile -> SequenceHeader.fromAv1C(
                                    request.extradata().toArray(JAVA_BYTE))
                            .map(sequence -> new VideoClaim(
                                    profile, sequence.maxFrameWidthMinus1() + 1, sequence.maxFrameHeightMinus1() + 1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /// The profile `sequence` decodes with, if this decoder decodes it.
    static Optional<VideoProfile> profile(SequenceHeader sequence) {
        var colour = sequence.colorConfig();
        if (sequence.seqProfile() != 0
                || colour.monoChrome()
                || colour.subsamplingX() != 1
                || colour.subsamplingY() != 1
                || (colour.bitDepth() != 8 && colour.bitDepth() != 10)
                || sequence.filmGrainParamsPresent()) {
            return Optional.empty();
        }
        return Optional.of(new VideoProfile(VulkanCodec.AV1, 0, colour.bitDepth()));
    }

    @Override
    public List<Output> decode(Packet packet) {
        var data = packet.data().toArray(JAVA_BYTE);
        var units = parse(data);
        var due = new ArrayList<Output>();
        for (var parsed : units) {
            var offset = parsed.obu().offset();
            switch (parsed.content()) {
                case SequenceHeader _ -> {
                    // Read by the parser; the session takes it at the next frame.
                }
                case ShowExistingFrame shown -> due.add(showExisting(shown, packet.ptsNanos()));
                case FrameHeader header -> begin(header, parsed.references(), offset);
                case Frame whole -> {
                    begin(whole.header(), parsed.references(), offset);
                    tiles(whole.tileGroup(), offset)
                            .ifPresent(_ -> decodeFrame(data, packet.ptsNanos()).ifPresent(due::add));
                }
                case TileGroup group ->
                    tiles(group, offset)
                            .ifPresent(_ -> decodeFrame(data, packet.ptsNanos()).ifPresent(due::add));
                default -> {}
            }
        }
        return due;
    }

    /// The OBUs of `data`, read by the stream's parser; after a reset, nothing
    /// until a temporal unit that starts with a key frame, which a fresh
    /// parser reads.
    private List<Av1Parser.Parsed> parse(byte[] data) {
        var current = parser;
        if (current != null) {
            return current.parse(data);
        }
        var fresh = new Av1Parser();
        try {
            if (record.length > 0) {
                fresh.configure(record);
            }
            var parsed = fresh.parse(data);
            for (var unit : parsed) {
                var header = switch (unit.content()) {
                    case FrameHeader h -> h;
                    case Frame f -> f.header();
                    default -> null;
                };
                if (header != null) {
                    if (header.frameType() != FrameHeader.KEY_FRAME) {
                        return List.of();
                    }
                    parser = fresh;
                    return parsed;
                }
            }
            return List.of();
        } catch (IllegalArgumentException e) {
            // A unit that needs references the parser was never given.
            return List.of();
        }
    }

    private void begin(FrameHeader header, References references, int offset) {
        frame = header;
        frameReferences = references;
        layout = new Av1Pictures.Layout(offset, List.of(), List.of());
    }

    /// Adds `group`'s tiles to the frame; present when it was the frame's last.
    private Optional<Boolean> tiles(TileGroup group, int offset) {
        var current = layout;
        if (current == null) {
            throw new IllegalArgumentException("a tile group with no frame header before it");
        }
        layout = current.with(group, offset);
        return group.last() ? Optional.of(true) : Optional.empty();
    }

    private Optional<Output> decodeFrame(byte[] data, long pts) {
        var header = frame;
        var current = layout;
        if (header == null || current == null) {
            throw new IllegalStateException("no frame to decode");
        }
        frame = null;
        layout = null;
        var size = header.frameSize();
        if (size.useSuperres()) {
            throw new IllegalArgumentException("an AV1 frame with superres: not decoded here");
        }
        ensureSession(header.sequenceHeader());
        var references = frameReferences;
        var setup = slots.free();
        var named = slots.named(header.refFrameIdx());
        var width = size.frameWidth();
        var height = size.frameHeight();
        var planes = session()
                .decode(
                        arena -> {
                            var bound = new ArrayList<VideoSession.Slot>();
                            for (var slot : Av1Slots.distinct(named)) {
                                var reference = referenceIn(header, named, slot);
                                bound.add(new VideoSession.Slot(
                                        slot, Av1Pictures.slotInfo(arena, references.slot(reference))));
                            }
                            return new VideoSession.Picture(
                                    data,
                                    Av1Pictures.pictureInfo(arena, header, current, named),
                                    new VideoSession.Slot(
                                            setup, Av1Pictures.slotInfo(arena, header, header.orderHint())),
                                    bound,
                                    parameters,
                                    width,
                                    height);
                        },
                        0,
                        0,
                        width,
                        height);
        slots.refresh(header.refreshFrameFlags(), setup);
        if (!header.showFrame()) {
            return Optional.empty();
        }
        return Optional.of(output(planes, header.sequenceHeader(), width, height, pts));
    }

    /// The reference slot whose frame is in buffer slot `slot`: the first of
    /// the frame's seven references there.
    private static int referenceIn(FrameHeader header, int[] named, int slot) {
        for (var i = 0; i < named.length; i++) {
            if (named[i] == slot) {
                return header.refFrameIdx().get(i);
            }
        }
        throw new IllegalStateException("slot " + slot + " is no reference of the frame");
    }

    private Output showExisting(ShowExistingFrame shown, long pts) {
        var index = shown.frameToShowMapIdx();
        var slot = slots.slotOf(index);
        if (slot == Av1Slots.NONE) {
            throw new IllegalArgumentException("show_existing_frame of slot " + index + ", which holds nothing");
        }
        var header = shown.shown();
        var size = header.frameSize();
        var planes = session().readBack(slot, 0, 0, size.frameWidth(), size.frameHeight());
        if (shown.frameType() == FrameHeader.KEY_FRAME) {
            slots.loadEverywhere(index);
        }
        return output(planes, header.sequenceHeader(), size.frameWidth(), size.frameHeight(), pts);
    }

    private Output output(VideoSession.Planes planes, SequenceHeader sequence, int width, int height, long pts) {
        var colour = sequence.colorConfig();
        var format = colour.bitDepth() == 8 ? PixelFormat.NV12 : PixelFormat.P010;
        var copied = pictures.copy(planes, format, width, height);
        var description = colour.colorDescriptionPresent()
                ? ColorDescription.fromH273(colour.colorPrimaries(), colour.transferCharacteristics())
                : ColorDescription.UNSPECIFIED;
        var signal = StreamSignal.of(
                colour.colorDescriptionPresent(),
                colour.matrixCoefficients(),
                true,
                colour.colorRange(),
                description,
                height);
        return new Output(copied, pts, signal.matrix(), signal.fullRange(), signal.colour());
    }

    /// Makes the session for `sequence` when it needs another than the one
    /// there is, and its parameters when the sequence header changed.
    private void ensureSession(SequenceHeader sequence) {
        var made = sessionSequence;
        if (session == null
                || made == null
                || made.maxFrameWidthMinus1() != sequence.maxFrameWidthMinus1()
                || made.maxFrameHeightMinus1() != sequence.maxFrameHeightMinus1()
                || made.colorConfig().bitDepth() != sequence.colorConfig().bitDepth()) {
            var profile = profile(sequence)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "only Main AV1, 4:2:0 of 8 or 10 bits without film grain, is decoded here"));
            var width = sequence.maxFrameWidthMinus1() + 1;
            var height = sequence.maxFrameHeightMinus1() + 1;
            var capabilities = VideoCapabilities.query(device, profile)
                    .filter(c -> c.fits(width, height))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "the device does not decode " + profile + " at " + width + "×" + height));
            close();
            session = new VideoSession(
                    device, profile, capabilities, width, height, Math.min(Av1Slots.SLOTS, capabilities.maxDpbSlots()));
            sessionSequence = sequence;
            slots.clear();
        }
        if (!sequence.equals(parametersSequence)) {
            var live = session();
            try (var arena = Arena.ofConfined()) {
                var next = live.parameters(Av1Pictures.createInfo(arena, sequence));
                live.destroyParameters(parameters);
                parameters = next;
            }
            parametersSequence = sequence;
        }
    }

    @Override
    public List<Output> drain() {
        return List.of();
    }

    @Override
    public void reset() {
        parser = null;
        frame = null;
        layout = null;
        slots.clear();
    }

    @Override
    public void close() {
        var current = session;
        session = null;
        parametersSequence = null;
        if (current != null) {
            current.destroyParameters(parameters);
            parameters = 0;
            current.close();
        }
    }

    private VideoSession session() {
        var current = session;
        if (current == null) {
            throw new IllegalStateException("no session");
        }
        return current;
    }
}
