package dev.goldberry.media.container.mp4;

import java.nio.charset.StandardCharsets;

/// The four-character codes of the boxes this demuxer reads, as the big-endian
/// integers they are on disk, so a box's type is compared and switched on
/// without making a string of it.
final class BoxTypes {

    static final int FTYP = fourcc("ftyp");
    static final int MOOV = fourcc("moov");
    static final int MDAT = fourcc("mdat");
    static final int MOOF = fourcc("moof");
    static final int FREE = fourcc("free");
    static final int SKIP = fourcc("skip");
    static final int WIDE = fourcc("wide");
    static final int UUID = fourcc("uuid");
    static final int MVHD = fourcc("mvhd");
    static final int MVEX = fourcc("mvex");
    static final int TREX = fourcc("trex");
    static final int TRAK = fourcc("trak");
    static final int TKHD = fourcc("tkhd");
    static final int TREF = fourcc("tref");
    static final int CHAP = fourcc("chap");
    static final int EDTS = fourcc("edts");
    static final int ELST = fourcc("elst");
    static final int MDIA = fourcc("mdia");
    static final int MDHD = fourcc("mdhd");
    static final int HDLR = fourcc("hdlr");
    static final int MINF = fourcc("minf");
    static final int STBL = fourcc("stbl");
    static final int STSD = fourcc("stsd");
    static final int STTS = fourcc("stts");
    static final int CTTS = fourcc("ctts");
    static final int STSC = fourcc("stsc");
    static final int STSZ = fourcc("stsz");
    static final int STZ2 = fourcc("stz2");
    static final int STCO = fourcc("stco");
    static final int CO64 = fourcc("co64");
    static final int STSS = fourcc("stss");
    static final int UDTA = fourcc("udta");
    static final int META = fourcc("meta");
    static final int ILST = fourcc("ilst");
    static final int COVR = fourcc("covr");
    static final int CHPL = fourcc("chpl");
    static final int DATA = fourcc("data");
    static final int TRAF = fourcc("traf");
    static final int TFHD = fourcc("tfhd");
    static final int TFDT = fourcc("tfdt");
    static final int TRUN = fourcc("trun");

    /// Handler types, `hdlr`'s `handler_type`.
    static final int VIDE = fourcc("vide");
    static final int SOUN = fourcc("soun");
    static final int SUBT = fourcc("subt");
    static final int SBTL = fourcc("sbtl");
    static final int TEXT = fourcc("text");

    /// Sample entries and their configuration boxes.
    static final int AV01 = fourcc("av01");
    static final int AV1C = fourcc("av1C");
    static final int VP09 = fourcc("vp09");
    static final int VP08 = fourcc("vp08");
    static final int VPCC = fourcc("vpcC");
    static final int AVC1 = fourcc("avc1");
    static final int AVC3 = fourcc("avc3");
    static final int AVCC = fourcc("avcC");
    static final int HVC1 = fourcc("hvc1");
    static final int HEV1 = fourcc("hev1");
    static final int HVCC = fourcc("hvcC");
    static final int COLR = fourcc("colr");
    static final int NCLX = fourcc("nclx");
    static final int NCLC = fourcc("nclc");
    static final int MDCV = fourcc("mdcv");
    static final int CLLI = fourcc("clli");
    static final int MP4A = fourcc("mp4a");
    static final int ESDS = fourcc("esds");
    static final int OPUS = fourcc("Opus");
    static final int DOPS = fourcc("dOps");
    static final int FLAC = fourcc("fLaC");
    static final int DFLA = fourcc("dfLa");
    static final int MP3 = fourcc(".mp3");
    static final int AC3 = fourcc("ac-3");
    static final int EC3 = fourcc("ec-3");
    static final int LPCM = fourcc("lpcm");
    static final int SOWT = fourcc("sowt");
    static final int TWOS = fourcc("twos");
    static final int IPCM = fourcc("ipcm");
    static final int PCMC = fourcc("pcmC");
    static final int TX3G = fourcc("tx3g");
    static final int WVTT = fourcc("wvtt");
    static final int BTRT = fourcc("btrt");

    private BoxTypes() {}

    /// The integer a four-character code is on disk.
    static int fourcc(String code) {
        var bytes = code.getBytes(StandardCharsets.ISO_8859_1);
        if (bytes.length != 4) {
            throw new IllegalArgumentException("not four characters: " + code);
        }
        return (bytes[0] & 0xFF) << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
    }

    /// The four characters of `type`, for a message: `©too` and `avc1`.
    static String name(int type) {
        var bytes = new byte[] {(byte) (type >>> 24), (byte) (type >>> 16), (byte) (type >>> 8), (byte) type};
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }
}
