package dev.goldberry.media.bitstream.vp9;

import java.util.Arrays;

import dev.goldberry.media.bitstream.BitWriter;

/// Writes a VP9 uncompressed header field by field, as the specification
/// orders them: how the tests make the frames no fixture has. Every field is
/// public and starts at a plain 64×64 keyframe of profile 0; a test sets the
/// ones its case needs and calls [#frame].
final class HeaderWriter {

    int profile;
    boolean reservedBit;
    boolean showExisting;
    int frameToShow;
    int frameType = UncompressedHeader.KEY_FRAME;
    boolean showFrame = true;
    boolean errorResilient;
    boolean intraOnly;
    int resetContext;
    int syncCode = UncompressedHeader.SYNC_CODE;
    int marker = 2;
    boolean twelveBit;
    int colorSpace = ColorConfig.CS_BT_709;
    boolean fullRange;
    int subsamplingX = 1;
    int subsamplingY = 1;
    boolean colorReserved;
    int refresh = 0xFF;
    int[] refIdx = {0, 1, 2};
    int[] signBias = {0, 0, 0};
    int foundRef = -1;
    int width = 64;
    int height = 64;
    int renderWidth;
    int renderHeight;
    boolean highPrecision;
    boolean switchable = true;
    int rawFilter;
    boolean refreshContext = true;
    boolean parallel = true;
    int contextIdx;
    int level = 10;
    int sharpness;
    boolean deltaEnabled;
    boolean deltaUpdate;
    /// The ref deltas sent, `null` for one not sent.
    Integer[] refDeltas = new Integer[4];

    Integer[] modeDeltas = new Integer[2];
    int baseQ = 60;
    int[] deltaQ = {0, 0, 0};
    boolean segmentation;
    boolean updateMap;
    int[] treeProbs = {255, 255, 255, 255, 255, 255, 255};
    boolean temporal;
    int[] predProbs = {255, 255, 255};
    boolean updateData;
    boolean absOrDelta;
    /// Each segment's features, `null` for one not enabled.
    Integer[][] features = new Integer[8][4];

    int tileColsIncrements;
    int tileRows;
    int compressedSize = 4;
    /// The bytes after the header, at least the compressed header's.
    int payload = 16;

    /// The frame: its uncompressed header, then [#payload] bytes.
    byte[] frame() {
        var w = new BitWriter();
        w.bits(2, marker).bits(1, profile & 1).bits(1, profile >> 1);
        if (profile == 3) {
            w.flag(reservedBit);
        }
        w.flag(showExisting);
        if (showExisting) {
            w.bits(3, frameToShow);
            return w.aligned();
        }
        w.bits(1, frameType).flag(showFrame).flag(errorResilient);
        if (frameType == UncompressedHeader.KEY_FRAME) {
            w.bits(24, syncCode);
            color(w);
            size(w);
        } else {
            if (!showFrame) {
                w.flag(intraOnly);
            }
            if (!errorResilient) {
                w.bits(2, resetContext);
            }
            if (intraOnly) {
                w.bits(24, syncCode);
                if (profile > 0) {
                    color(w);
                }
                w.bits(8, refresh);
                size(w);
            } else {
                w.bits(8, refresh);
                for (var i = 0; i < 3; i++) {
                    w.bits(3, refIdx[i]).bits(1, signBias[i]);
                }
                for (var i = 0; i < 3; i++) {
                    w.flag(foundRef == i);
                    if (foundRef == i) {
                        break;
                    }
                }
                if (foundRef < 0) {
                    w.bits(16, width - 1).bits(16, height - 1);
                }
                render(w);
                w.flag(highPrecision).flag(switchable);
                if (!switchable) {
                    w.bits(2, rawFilter);
                }
            }
        }
        if (!errorResilient) {
            w.flag(refreshContext).flag(parallel);
        }
        w.bits(2, contextIdx);
        w.bits(6, level).bits(3, sharpness).flag(deltaEnabled);
        if (deltaEnabled) {
            w.flag(deltaUpdate);
            if (deltaUpdate) {
                for (var delta : refDeltas) {
                    sendDelta(w, delta);
                }
                for (var delta : modeDeltas) {
                    sendDelta(w, delta);
                }
            }
        }
        w.bits(8, baseQ);
        for (var delta : deltaQ) {
            w.flag(delta != 0);
            if (delta != 0) {
                w.signMagnitude(4, delta);
            }
        }
        w.flag(segmentation);
        if (segmentation) {
            w.flag(updateMap);
            if (updateMap) {
                probs(w, treeProbs);
                w.flag(temporal);
                if (temporal) {
                    probs(w, predProbs);
                }
            }
            w.flag(updateData);
            if (updateData) {
                w.flag(absOrDelta);
                for (var segment : features) {
                    for (var j = 0; j < 4; j++) {
                        var value = segment[j];
                        w.flag(value != null);
                        if (value != null && SegmentFeatures.BITS.get(j) > 0) {
                            w.bits(SegmentFeatures.BITS.get(j), Math.abs(value));
                            if (SegmentFeatures.SIGNED.get(j)) {
                                w.flag(value < 0);
                            }
                        }
                    }
                }
            }
        }
        // As many columns as a frame this narrow may have: the increments,
        // and the zero that stops them short of the most.
        var sb64Cols = (((width + 7) >> 3) + 7) >> 3;
        var maxLog2 = 0;
        while ((sb64Cols >> (maxLog2 + 1)) >= 4) {
            maxLog2++;
        }
        for (var i = 0; i < tileColsIncrements; i++) {
            w.flag(true);
        }
        if (tileColsIncrements < maxLog2) {
            w.flag(false);
        }
        w.bits(1, tileRows > 0 ? 1 : 0);
        if (tileRows > 0) {
            w.bits(1, tileRows - 1);
        }
        w.bits(16, compressedSize);
        var header = w.aligned();
        return Arrays.copyOf(header, header.length + payload);
    }

    private void color(BitWriter w) {
        if (profile >= 2) {
            w.flag(twelveBit);
        }
        w.bits(3, colorSpace);
        if (colorSpace != ColorConfig.CS_RGB) {
            w.flag(fullRange);
            if (profile == 1 || profile == 3) {
                w.bits(1, subsamplingX).bits(1, subsamplingY).flag(colorReserved);
            }
        } else if (profile == 1 || profile == 3) {
            w.flag(colorReserved);
        }
    }

    private void size(BitWriter w) {
        w.bits(16, width - 1).bits(16, height - 1);
        render(w);
    }

    private void render(BitWriter w) {
        w.flag(renderWidth > 0);
        if (renderWidth > 0) {
            w.bits(16, renderWidth - 1).bits(16, renderHeight - 1);
        }
    }

    private static void sendDelta(BitWriter w, Integer delta) {
        w.flag(delta != null);
        if (delta != null) {
            w.signMagnitude(6, delta);
        }
    }

    private static void probs(BitWriter w, int[] probs) {
        for (var prob : probs) {
            w.flag(prob != 255);
            if (prob != 255) {
                w.bits(8, prob);
            }
        }
    }
}
