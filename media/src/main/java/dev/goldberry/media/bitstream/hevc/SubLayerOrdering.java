package dev.goldberry.media.bitstream.hevc;

/// One temporal sub-layer's decoded picture buffer limits (H.265 §7.4.3.1,
/// §7.4.3.2.1): what the buffer holds, how many pictures may wait for output
/// ahead of one, and how long.
///
/// @param maxDecPicBufferingMinus1 `…_max_dec_pic_buffering_minus1`
/// @param maxNumReorderPics        `…_max_num_reorder_pics`
/// @param maxLatencyIncreasePlus1  `…_max_latency_increase_plus1`
public record SubLayerOrdering(int maxDecPicBufferingMinus1, int maxNumReorderPics, int maxLatencyIncreasePlus1) {

    /// `SpsMaxLatencyPictures`, or −1 when there is no such limit.
    public int maxLatencyPictures() {
        return maxLatencyIncreasePlus1 == 0 ? -1 : maxNumReorderPics + maxLatencyIncreasePlus1 - 1;
    }
}
