package dev.goldberry.media.ffi;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/// Names a container from its first bytes, for a source no demuxer of this
/// build recognised (`docs/goldberry-media.md` §7, S7; ADR-0471).
///
/// FFmpeg probes a source with the demuxers it was built with, and a build
/// without MPEG-TS has nothing that knows what a transport stream looks like. It
/// fails with "Invalid data found when processing input", which reads like a
/// damaged file. The signatures here are the ones every player knows, a few
/// bytes each, so the error can say what the file is instead: "no demuxer for
/// MPEG-TS".
///
/// A signature is reported only when this build has **no** demuxer for it
/// ([#identify]'s `demuxers`). A container the build does read, and that failed
/// anyway, is damaged, and stays [dev.goldberry.media.MediaError.InvalidData].
final class ContainerSniffer {

    /// How many bytes from the start of a source are kept for sniffing. Three
    /// 192-byte M2TS packets need 580.
    static final int HEAD_BYTES = 1024;

    /// One container's signature.
    ///
    /// @param name    what the error calls it
    /// @param demuxer FFmpeg's name for the demuxer that would read it
    /// @param matches whether a source's first bytes are this container's
    record Signature(String name, String demuxer, Predicate<byte[]> matches) {}

    /// The signatures, most specific first.
    static final List<Signature> SIGNATURES = List.of(
            new Signature("AVI", "avi", head -> ascii(head, 0, "RIFF") && ascii(head, 8, "AVI ")),
            new Signature(
                    "AIFF",
                    "aiff",
                    head -> ascii(head, 0, "FORM") && (ascii(head, 8, "AIFF") || ascii(head, 8, "AIFC"))),
            new Signature(
                    "ASF (WMV, WMA)", "asf", head -> bytes(head, 0, 0x30, 0x26, 0xB2, 0x75, 0x8E, 0x66, 0xCF, 0x11)),
            new Signature("MPEG-TS", "mpegts", head -> syncEvery(head, 0, 188)),
            new Signature("MPEG-TS (M2TS)", "mpegts", head -> syncEvery(head, 4, 192)),
            new Signature("MPEG program stream (MPG, VOB)", "mpeg", head -> bytes(head, 0, 0x00, 0x00, 0x01, 0xBA)),
            new Signature("MPEG-1/2 video", "mpegvideo", head -> bytes(head, 0, 0x00, 0x00, 0x01, 0xB3)),
            new Signature(
                    "raw H.264",
                    "h264",
                    head -> bytes(head, 0, 0x00, 0x00, 0x00, 0x01) && head.length > 4 && (head[4] & 0x1F) == 7),
            new Signature("FLV", "flv", head -> ascii(head, 0, "FLV") && head.length > 3 && head[3] == 1),
            new Signature("RealMedia", "rm", head -> ascii(head, 0, ".RMF")),
            new Signature("MXF", "mxf", head -> bytes(head, 0, 0x06, 0x0E, 0x2B, 0x34)),
            new Signature("IVF", "ivf", head -> ascii(head, 0, "DKIF")),
            new Signature("Core Audio Format", "caf", head -> ascii(head, 0, "caff")),
            new Signature("AMR", "amr", head -> ascii(head, 0, "#!AMR")),
            new Signature("WavPack", "wv", head -> ascii(head, 0, "wvpk")),
            new Signature("Monkey's Audio", "ape", head -> ascii(head, 0, "MAC ")),
            new Signature("Musepack", "mpc8", head -> ascii(head, 0, "MPCK")),
            new Signature("Musepack", "mpc", head -> ascii(head, 0, "MP+")),
            new Signature("DSD (DSF)", "dsf", head -> ascii(head, 0, "DSD ")),
            new Signature(
                    "AAC (ADTS)",
                    "aac",
                    head -> head.length > 1 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xF6) == 0xF0),
            new Signature("AC-3", "ac3", head -> bytes(head, 0, 0x0B, 0x77)));

    private ContainerSniffer() {}

    /// The container `head`, a source's first bytes, is, when this build has no
    /// demuxer for it.
    ///
    /// @param demuxers the format names this build demuxes
    ///                 ([FfmpegCapabilities#demuxers])
    static Optional<Signature> identify(byte[] head, Set<String> demuxers) {
        return SIGNATURES.stream()
                .filter(signature -> signature.matches().test(head))
                .findFirst()
                .filter(signature -> !demuxers.contains(signature.demuxer()));
    }

    private static boolean ascii(byte[] head, int offset, String text) {
        var expected = text.getBytes(StandardCharsets.US_ASCII);
        if (head.length < offset + expected.length) {
            return false;
        }
        for (var i = 0; i < expected.length; i++) {
            if (head[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean bytes(byte[] head, int offset, int... expected) {
        if (head.length < offset + expected.length) {
            return false;
        }
        for (var i = 0; i < expected.length; i++) {
            if ((head[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /// Three sync bytes (`0x47`) a packet apart, from `offset`: a transport
    /// stream, whose packets have no other header to go by.
    private static boolean syncEvery(byte[] head, int offset, int packet) {
        for (var i = 0; i < 3; i++) {
            var at = offset + i * packet;
            if (head.length <= at || head[at] != 0x47) {
                return false;
            }
        }
        return true;
    }
}
