package io.github.digitalsmile.goldberry.media.platform.macos;

/// The channel order the frame contract asks for, in Core Audio's labels.
///
/// An [io.github.digitalsmile.goldberry.media.codec.AudioFrame]'s channels are in
/// FFmpeg's default order for their count (`av_channel_layout_default`): the
/// Engine's resampler reads them so. AudioToolbox decodes in the order of the
/// stream, which for AAC 5.1 is centre first and for AC-3 is centre second. The
/// decoder is told to produce these labels in this order instead
/// (`kAudioConverterOutputChannelLayout`), so the converter moves each channel to
/// its place.
///
/// Core Audio's `LeftSurround` and `RightSurround` are the surround pair of a 5.1
/// mix, where FFmpeg's default 5.1 names them back left and back right. Both are
/// the pair after the LFE, which is what matters here: FFmpeg's own AAC decoder
/// puts them there too.
final class ChannelLabels {

    // AudioChannelLabel values (CoreAudioBaseTypes.h).
    static final int LEFT = 1;
    static final int RIGHT = 2;
    static final int CENTER = 3;
    static final int LFE_SCREEN = 4;
    static final int LEFT_SURROUND = 5;
    static final int RIGHT_SURROUND = 6;
    static final int CENTER_SURROUND = 9;
    static final int REAR_SURROUND_LEFT = 33;
    static final int REAR_SURROUND_RIGHT = 34;

    private ChannelLabels() {}

    /// The labels, in order, for `channels` channels, or an empty array when the
    /// decoder's own order is already FFmpeg's: mono and stereo.
    ///
    /// FFmpeg's defaults (`channel_layout_map`, first match by count): 2.1, 4.0,
    /// 5.0, 5.1, 6.1 and 7.1.
    ///
    /// @throws IllegalArgumentException for more than eight channels, which
    ///                                  neither codec's common profiles carry
    static int[] forChannels(int channels) {
        return switch (channels) {
            case 1, 2 -> new int[0];
            // 2.1: FL FR LFE. A 3.0 stream's centre is folded into the fronts.
            case 3 -> new int[] {LEFT, RIGHT, LFE_SCREEN};
            // 4.0: FL FR FC BC.
            case 4 -> new int[] {LEFT, RIGHT, CENTER, CENTER_SURROUND};
            // 5.0: FL FR FC BL BR.
            case 5 -> new int[] {LEFT, RIGHT, CENTER, LEFT_SURROUND, RIGHT_SURROUND};
            // 5.1: FL FR FC LFE BL BR.
            case 6 -> new int[] {LEFT, RIGHT, CENTER, LFE_SCREEN, LEFT_SURROUND, RIGHT_SURROUND};
            // 6.1: FL FR FC LFE BC SL SR.
            case 7 -> new int[] {LEFT, RIGHT, CENTER, LFE_SCREEN, CENTER_SURROUND, LEFT_SURROUND, RIGHT_SURROUND};
            // 7.1: FL FR FC LFE BL BR SL SR.
            case 8 ->
                new int[] {
                    LEFT,
                    RIGHT,
                    CENTER,
                    LFE_SCREEN,
                    REAR_SURROUND_LEFT,
                    REAR_SURROUND_RIGHT,
                    LEFT_SURROUND,
                    RIGHT_SURROUND
                };
            default -> throw new IllegalArgumentException("no channel order for " + channels + " channels");
        };
    }
}
