package dev.goldberry.media.container.matroska;

/// The Matroska element IDs this demuxer reads and [MatroskaWriter] writes (IETF
/// Matroska, the element registry), with their length-marker bits as the
/// specification writes them.
///
/// Only what is read or written is named. An element not here is skipped by
/// size, which is how EBML is meant to be read forward through versions.
public final class MatroskaIds {

    private MatroskaIds() {}

    // ---- the header ----------------------------------------------------------

    public static final long EBML = 0x1A45DFA3L;
    public static final long EBML_VERSION = 0x4286;
    public static final long EBML_READ_VERSION = 0x42F7;
    public static final long EBML_MAX_ID_LENGTH = 0x42F2;
    public static final long EBML_MAX_SIZE_LENGTH = 0x42F3;
    public static final long DOC_TYPE = 0x4282;
    public static final long DOC_TYPE_VERSION = 0x4287;
    public static final long DOC_TYPE_READ_VERSION = 0x4285;

    // ---- the segment, and its top-level children ----------------------------

    public static final long SEGMENT = 0x18538067L;
    public static final long SEEK_HEAD = 0x114D9B74L;
    public static final long INFO = 0x1549A966L;
    public static final long TRACKS = 0x1654AE6BL;
    public static final long CLUSTER = 0x1F43B675L;
    public static final long CUES = 0x1C53BB6BL;
    public static final long ATTACHMENTS = 0x1941A469L;
    public static final long CHAPTERS = 0x1043A770L;
    public static final long TAGS = 0x1254C367L;

    // ---- SeekHead ------------------------------------------------------------

    public static final long SEEK = 0x4DBB;
    public static final long SEEK_ID = 0x53AB;
    public static final long SEEK_POSITION = 0x53AC;

    // ---- Info ----------------------------------------------------------------

    public static final long TIMESTAMP_SCALE = 0x2AD7B1;
    public static final long DURATION = 0x4489;
    public static final long MUXING_APP = 0x4D80;
    public static final long WRITING_APP = 0x5741;

    // ---- Tracks --------------------------------------------------------------

    public static final long TRACK_ENTRY = 0xAE;
    public static final long TRACK_NUMBER = 0xD7;
    public static final long TRACK_UID = 0x73C5;
    public static final long TRACK_TYPE = 0x83;
    public static final long FLAG_DEFAULT = 0x88;
    public static final long FLAG_LACING = 0x9C;
    public static final long DEFAULT_DURATION = 0x23E383;
    public static final long NAME = 0x536E;
    public static final long LANGUAGE = 0x22B59C;
    public static final long LANGUAGE_BCP47 = 0x22B59D;
    public static final long CODEC_ID = 0x86;
    public static final long CODEC_PRIVATE = 0x63A2;
    public static final long CODEC_DELAY = 0x56AA;

    public static final long VIDEO = 0xE0;
    public static final long PIXEL_WIDTH = 0xB0;
    public static final long PIXEL_HEIGHT = 0xBA;
    public static final long ALPHA_MODE = 0x53C0;
    /// The four-character code of a `V_UNCOMPRESSED` track's pixels: `NV12`, `I420`.
    public static final long UNCOMPRESSED_FOURCC = 0x2EB524;
    public static final long COLOUR = 0x55B0;
    public static final long MATRIX_COEFFICIENTS = 0x55B1;
    public static final long RANGE = 0x55B9;
    public static final long TRANSFER_CHARACTERISTICS = 0x55BA;
    public static final long PRIMARIES = 0x55BB;
    public static final long MAX_CLL = 0x55BC;
    public static final long MASTERING_METADATA = 0x55D0;
    public static final long LUMINANCE_MAX = 0x55D9;

    public static final long AUDIO = 0xE1;
    public static final long SAMPLING_FREQUENCY = 0xB5;
    public static final long CHANNELS = 0x9F;
    public static final long BIT_DEPTH = 0x6264;

    public static final long CONTENT_ENCODINGS = 0x6D80;
    public static final long CONTENT_ENCODING = 0x6240;
    public static final long CONTENT_ENCODING_TYPE = 0x5033;
    public static final long CONTENT_COMPRESSION = 0x5034;
    public static final long CONTENT_COMP_ALGO = 0x4254;
    public static final long CONTENT_COMP_SETTINGS = 0x4255;

    /// `TrackType` values.
    public static final int TRACK_TYPE_VIDEO = 1;

    public static final int TRACK_TYPE_AUDIO = 2;
    public static final int TRACK_TYPE_SUBTITLE = 17;

    /// `ContentCompAlgo` values: zlib, and the one that is not a compression at
    /// all but bytes to put back in front of every frame.
    public static final int COMP_ALGO_ZLIB = 0;

    public static final int COMP_ALGO_HEADER_STRIPPING = 3;

    // ---- Cluster -------------------------------------------------------------

    public static final long TIMESTAMP = 0xE7;
    public static final long SIMPLE_BLOCK = 0xA3;
    public static final long BLOCK_GROUP = 0xA0;
    public static final long BLOCK = 0xA1;
    public static final long BLOCK_DURATION = 0x9B;
    public static final long REFERENCE_BLOCK = 0xFB;
    public static final long BLOCK_ADDITIONS = 0x75A1;
    public static final long BLOCK_MORE = 0xA6;
    public static final long BLOCK_ADD_ID = 0xEE;
    public static final long BLOCK_ADDITIONAL = 0xA5;

    /// The `BlockAddID` a WebM picture's alpha plane travels under.
    public static final long BLOCK_ADD_ID_ALPHA = 1;

    // ---- Cues ----------------------------------------------------------------

    public static final long CUE_POINT = 0xBB;
    public static final long CUE_TIME = 0xB3;
    public static final long CUE_TRACK_POSITIONS = 0xB7;
    public static final long CUE_TRACK = 0xF7;
    public static final long CUE_CLUSTER_POSITION = 0xF1;

    // ---- Attachments ---------------------------------------------------------

    public static final long ATTACHED_FILE = 0x61A7;
    public static final long FILE_NAME = 0x466E;
    public static final long FILE_MEDIA_TYPE = 0x4660;
    public static final long FILE_DATA = 0x465C;

    // ---- Chapters ------------------------------------------------------------

    public static final long EDITION_ENTRY = 0x45B9;
    public static final long EDITION_FLAG_HIDDEN = 0x45BD;
    public static final long EDITION_FLAG_DEFAULT = 0x45DB;
    public static final long CHAPTER_ATOM = 0xB6;
    public static final long CHAPTER_TIME_START = 0x91;
    public static final long CHAPTER_TIME_END = 0x92;
    public static final long CHAPTER_FLAG_HIDDEN = 0x98;
    public static final long CHAPTER_FLAG_ENABLED = 0x4598;
    public static final long CHAPTER_DISPLAY = 0x80;
    public static final long CHAP_STRING = 0x85;

    /// Whether `id` is one of the Segment's direct children, which is how a
    /// Cluster of unknown size is known to have ended.
    public static boolean isTopLevel(long id) {
        return id == SEEK_HEAD
                || id == INFO
                || id == TRACKS
                || id == CLUSTER
                || id == CUES
                || id == ATTACHMENTS
                || id == CHAPTERS
                || id == TAGS;
    }
}
