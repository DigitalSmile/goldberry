package dev.goldberry.media.bitstream.av1;

import java.util.Objects;
import java.util.Optional;

/// One of the eight reference slots (§7.20): the frame stored in it, and
/// whether it may still be referred to.
///
/// What the decoding process saves per slot (`RefFrameType`,
/// `RefUpscaledWidth`, `SavedOrderHints`, `SavedGmParams`, the loop filter
/// deltas, the segmentation features, the film grain) is read from the header
/// the frame was decoded with, [#frame].
///
/// @param valid     `RefValid`
/// @param orderHint `RefOrderHint`: the frame's order hint, or the one an
///                  error resilient frame said the slot should hold
/// @param showable  `RefShowableFrame`: whether `show_existing_frame` may
///                  still show it
/// @param frame     the header the frame was decoded with; empty for a slot
///                  no frame was stored in
public record ReferenceFrame(boolean valid, int orderHint, boolean showable, Optional<FrameHeader> frame) {

    /// A slot no frame was stored in.
    public static final ReferenceFrame NONE = new ReferenceFrame(false, 0, false, Optional.empty());

    public ReferenceFrame {
        Objects.requireNonNull(frame, "frame");
    }

    /// The frame `header` decodes, stored.
    static ReferenceFrame of(FrameHeader header) {
        return new ReferenceFrame(true, header.orderHint(), header.showableFrame(), Optional.of(header));
    }

    /// The header of the frame in the slot, for a slot that must hold one.
    ///
    /// @throws IllegalArgumentException when no frame was stored in it
    public FrameHeader header() {
        return frame.orElseThrow(() -> new IllegalArgumentException("a reference slot that holds no frame"));
    }

    /// `RefFrameId`: 0 for a slot no frame was stored in.
    public int frameId() {
        return frame.map(FrameHeader::currentFrameId).orElse(0);
    }

    /// This slot, invalid, expected to hold the frame of order hint `hint`.
    ReferenceFrame invalidated(int hint) {
        return new ReferenceFrame(false, hint, showable, frame);
    }

    /// This slot, invalid.
    ReferenceFrame invalidated() {
        return invalidated(orderHint);
    }
}
