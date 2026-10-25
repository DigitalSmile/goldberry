package dev.goldberry.media.bitstream.av1;

import java.util.Objects;

/// A frame header that shows a frame decoded before (§5.9.2 with
/// `show_existing_frame` set): nothing is decoded, the frame in a reference
/// slot is output, with its film grain. When that frame is a key frame,
/// showing it is a key frame of its own: it is loaded (§7.21) and stored in
/// every slot.
///
/// @param frameToShowMapIdx     `frame_to_show_map_idx`: the slot shown
/// @param framePresentationTime `frame_presentation_time`, 0 when not coded
/// @param displayFrameId        `display_frame_id`, 0 without frame ids
/// @param shown                 the header the shown frame was decoded with
public record ShowExistingFrame(
        int frameToShowMapIdx, long framePresentationTime, int displayFrameId, FrameHeader shown)
        implements UncompressedHeader {

    public ShowExistingFrame {
        Objects.requireNonNull(shown, "shown");
    }

    /// `frame_type`: the shown frame's.
    public int frameType() {
        return shown.frameType();
    }

    /// `refresh_frame_flags`: every slot when a key frame is shown, else none.
    public int refreshFrameFlags() {
        return shown.frameType() == FrameHeader.KEY_FRAME ? References.ALL_FRAMES : 0;
    }
}
