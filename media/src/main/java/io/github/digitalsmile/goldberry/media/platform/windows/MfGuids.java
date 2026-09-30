package io.github.digitalsmile.goldberry.media.platform.windows;

/// The GUIDs the bindings pass: categories, interfaces, media types and
/// attribute keys.
///
/// Each is copied from the Windows SDK header named beside it, not read from a
/// running system; CI on Windows is what confirms them.
final class MfGuids {

    // --- MFT categories (mfapi.h) ---

    /// `MFT_CATEGORY_VIDEO_DECODER` (`mfapi.h`).
    static final Guid MFT_CATEGORY_VIDEO_DECODER = Guid.parse("d6c02d4b-6833-45b4-971a-05a4b04bab91");
    /// `MFT_CATEGORY_AUDIO_DECODER` (`mfapi.h`).
    static final Guid MFT_CATEGORY_AUDIO_DECODER = Guid.parse("9ea73fb4-ef7a-4559-8d5d-719d8f0426c7");

    // --- Interfaces ---

    /// `IID_IMFTransform` (`mftransform.h`).
    static final Guid IID_IMFTransform = Guid.parse("bf94c121-5b05-4e6f-8000-ba598961414d");
    /// `IID_IMF2DBuffer` (`mfobjects.h`).
    static final Guid IID_IMF2DBuffer = Guid.parse("7dc9d5f9-9ed9-44ec-9bbf-0600bb589fbb");

    // --- Major types (mfapi.h) ---

    /// `MFMediaType_Video` (`mfapi.h`): `'vids'` as a FourCC GUID.
    static final Guid MFMediaType_Video = Guid.parse("73646976-0000-0010-8000-00aa00389b71");
    /// `MFMediaType_Audio` (`mfapi.h`): `'auds'` as a FourCC GUID.
    static final Guid MFMediaType_Audio = Guid.parse("73647561-0000-0010-8000-00aa00389b71");

    // --- Video subtypes (mfapi.h, DEFINE_MEDIATYPE_GUID over a FourCC) ---

    /// `MFVideoFormat_H264` (`mfapi.h`).
    static final Guid MFVideoFormat_H264 = Guid.fourCc("H264");
    /// `MFVideoFormat_HEVC` (`mfapi.h`).
    static final Guid MFVideoFormat_HEVC = Guid.fourCc("HEVC");
    /// `MFVideoFormat_NV12` (`mfapi.h`).
    static final Guid MFVideoFormat_NV12 = Guid.fourCc("NV12");
    /// `MFVideoFormat_P010` (`mfapi.h`).
    static final Guid MFVideoFormat_P010 = Guid.fourCc("P010");

    // --- Audio subtypes (mfapi.h; the first two are WAVE_FORMAT_ tags as GUIDs) ---

    /// `MFAudioFormat_AAC` (`mfapi.h`): `WAVE_FORMAT_MPEG_HEAAC`, 0x1610.
    static final Guid MFAudioFormat_AAC = Guid.parse("00001610-0000-0010-8000-00aa00389b71");
    /// `MFAudioFormat_Float` (`mfapi.h`): `WAVE_FORMAT_IEEE_FLOAT`, 3.
    static final Guid MFAudioFormat_Float = Guid.parse("00000003-0000-0010-8000-00aa00389b71");
    /// `MFAudioFormat_Dolby_AC3` (`mfapi.h`), which is DirectShow's
    /// `MEDIASUBTYPE_DOLBY_AC3` (`ksuuids.h`).
    static final Guid MFAudioFormat_Dolby_AC3 = Guid.parse("e06d802c-db46-11cf-b4d1-00805f6cbbea");
    /// `MFAudioFormat_Dolby_DDPlus` (`mfapi.h`): E-AC-3.
    static final Guid MFAudioFormat_Dolby_DDPlus = Guid.parse("a7fb87af-2d02-42fb-a4d4-05cd93843bdd");

    // --- Media type attributes (mfapi.h) ---

    /// `MF_MT_MAJOR_TYPE`, a GUID.
    static final Guid MF_MT_MAJOR_TYPE = Guid.parse("48eba18e-f8c9-4687-bf11-0a74c9f96a8f");
    /// `MF_MT_SUBTYPE`, a GUID.
    static final Guid MF_MT_SUBTYPE = Guid.parse("f7e34c9a-42e8-4714-b74b-cb29d72c35e5");
    /// `MF_MT_FRAME_SIZE`, a UINT64: width in the high 32 bits, height in the
    /// low.
    static final Guid MF_MT_FRAME_SIZE = Guid.parse("1652c33d-d6b2-4012-b834-72030849a37d");
    /// `MF_MT_DEFAULT_STRIDE`, a UINT32 holding a signed `LONG`: bytes from one
    /// row of the first plane to the next, negative for a bottom-up picture.
    static final Guid MF_MT_DEFAULT_STRIDE = Guid.parse("644b4e48-1e02-4516-b0eb-c01ca9d49ac6");
    /// `MF_MT_MINIMUM_DISPLAY_APERTURE`, a blob holding an `MFVideoArea`: the
    /// crop.
    static final Guid MF_MT_MINIMUM_DISPLAY_APERTURE = Guid.parse("d7388766-18fe-48c6-a177-ee894867c8c4");
    /// `MF_MT_YUV_MATRIX`, a UINT32 `MFVideoTransferMatrix`.
    static final Guid MF_MT_YUV_MATRIX = Guid.parse("3e23d450-2c75-4d25-a00e-b91670d12327");
    /// `MF_MT_VIDEO_NOMINAL_RANGE`, a UINT32 `MFNominalRange`.
    static final Guid MF_MT_VIDEO_NOMINAL_RANGE = Guid.parse("c21b8ee5-b956-4071-8daf-325edf5cab11");
    /// `MF_MT_INTERLACE_MODE`, a UINT32 `MFVideoInterlaceMode`.
    static final Guid MF_MT_INTERLACE_MODE = Guid.parse("e2724bb8-e676-4806-b4b2-a8d6efb44ccd");
    /// `MF_MT_USER_DATA`, a blob: for AAC, the `HEAACWAVEINFO` tail and the
    /// `AudioSpecificConfig`.
    static final Guid MF_MT_USER_DATA = Guid.parse("b6bc765f-4c3b-40a4-bd51-2535b66fe09d");
    /// `MF_MT_AAC_PAYLOAD_TYPE`, a UINT32: 0 for raw access units.
    static final Guid MF_MT_AAC_PAYLOAD_TYPE = Guid.parse("bfbabe79-7434-4d1c-94f0-72a3b9e17188");
    /// `MF_MT_AAC_AUDIO_PROFILE_LEVEL_INDICATION`, a UINT32.
    static final Guid MF_MT_AAC_AUDIO_PROFILE_LEVEL_INDICATION = Guid.parse("7632f0e6-9538-4d61-acda-ea29c8c14456");
    /// `MF_MT_AUDIO_SAMPLES_PER_SECOND`, a UINT32.
    static final Guid MF_MT_AUDIO_SAMPLES_PER_SECOND = Guid.parse("5faeeae7-0290-4c31-9e8a-c534f68d9dba");
    /// `MF_MT_AUDIO_NUM_CHANNELS`, a UINT32.
    static final Guid MF_MT_AUDIO_NUM_CHANNELS = Guid.parse("37e48bf5-645e-4c5b-89de-ada9e29b696a");
    /// `MF_MT_AUDIO_BITS_PER_SAMPLE`, a UINT32.
    static final Guid MF_MT_AUDIO_BITS_PER_SAMPLE = Guid.parse("f2deb57f-40fa-4764-aa33-ed4f2d1ff669");
    /// `MF_MT_AUDIO_BLOCK_ALIGNMENT`, a UINT32: bytes per frame of samples.
    static final Guid MF_MT_AUDIO_BLOCK_ALIGNMENT = Guid.parse("322de230-9eeb-43bd-ab7a-ff412251541d");
    /// `MF_MT_AUDIO_AVG_BYTES_PER_SECOND`, a UINT32.
    static final Guid MF_MT_AUDIO_AVG_BYTES_PER_SECOND = Guid.parse("1aab75c8-cfef-451c-ab95-ac034b8e1731");
    /// `MF_MT_AUDIO_CHANNEL_MASK`, a UINT32 `SPEAKER_…` mask (`ksmedia.h`).
    static final Guid MF_MT_AUDIO_CHANNEL_MASK = Guid.parse("55fb5765-644a-4caf-8479-938983bb1588");

    // --- Enumerations (mfobjects.h) ---

    /// `MFVideoTransferMatrix_Unknown`.
    static final int MATRIX_UNKNOWN = 0;
    /// `MFVideoTransferMatrix_BT709`.
    static final int MATRIX_BT709 = 1;
    /// `MFVideoTransferMatrix_BT601`.
    static final int MATRIX_BT601 = 2;
    /// `MFVideoTransferMatrix_SMPTE240M`.
    static final int MATRIX_SMPTE240M = 3;
    /// `MFVideoTransferMatrix_BT2020_10`.
    static final int MATRIX_BT2020_10 = 4;
    /// `MFVideoTransferMatrix_BT2020_12`.
    static final int MATRIX_BT2020_12 = 5;

    /// `MFNominalRange_Unknown`.
    static final int RANGE_UNKNOWN = 0;
    /// `MFNominalRange_0_255`: full range.
    static final int RANGE_0_255 = 1;
    /// `MFNominalRange_16_235`: limited range.
    static final int RANGE_16_235 = 2;

    /// `MFVideoInterlace_Progressive`.
    static final int INTERLACE_PROGRESSIVE = 2;
    /// `MFVideoInterlace_MixedInterlaceOrProgressive`: what a decoder is told of
    /// a stream it has not read yet, which may code either.
    static final int INTERLACE_MIXED = 7;

    private MfGuids() {}
}
