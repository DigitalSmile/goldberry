package io.github.digitalsmile.goldberry.media.codec;

/// How the samples of an [AudioFrame] are stored.
///
/// A decoder hands frames over in whichever of these it decodes to. The Engine
/// converts every one of them to what the audio device plays, in one pass
/// (`docs/goldberry-media.md` §3). So a provider never converts formats itself,
/// and the built-in decoder hands FFmpeg's buffers over without a copy.
///
/// *Interleaved* formats keep every channel in one plane, frame after frame.
/// *Planar* formats keep one plane per channel.
public enum SampleFormat {
    U8(1, false, "U8"),
    S16(2, false, "S16"),
    S32(4, false, "S32"),
    S64(8, false, "S64"),
    F32(4, false, "FLT"),
    F64(8, false, "DBL"),
    U8_PLANAR(1, true, "U8P"),
    S16_PLANAR(2, true, "S16P"),
    S32_PLANAR(4, true, "S32P"),
    S64_PLANAR(8, true, "S64P"),
    F32_PLANAR(4, true, "FLTP"),
    F64_PLANAR(8, true, "DBLP");

    private final int bytesPerSample;
    private final boolean planar;
    private final String ffmpegSuffix;

    SampleFormat(int bytesPerSample, boolean planar, String ffmpegSuffix) {
        this.bytesPerSample = bytesPerSample;
        this.planar = planar;
        this.ffmpegSuffix = ffmpegSuffix;
    }

    /// Bytes per sample of one channel.
    public int bytesPerSample() {
        return bytesPerSample;
    }

    /// Whether each channel has a plane of its own.
    public boolean planar() {
        return planar;
    }

    /// The suffix of FFmpeg's `AV_SAMPLE_FMT_*` name for this format.
    public String ffmpegSuffix() {
        return ffmpegSuffix;
    }

    /// How many planes a frame of `channels` channels has in this format.
    public int planes(int channels) {
        return planar ? channels : 1;
    }

    /// The size in bytes of one plane holding `samples` samples of `channels`
    /// channels.
    public long planeSize(int channels, int samples) {
        return (long) bytesPerSample * samples * (planar ? 1 : channels);
    }
}
