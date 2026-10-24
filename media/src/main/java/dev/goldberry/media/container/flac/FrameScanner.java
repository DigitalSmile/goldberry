package dev.goldberry.media.container.flac;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;
import dev.goldberry.media.container.ByteReader;

/// Finds FLAC frames in a stream that does not say where they end.
///
/// A frame starts with a sync code and a header whose fields are checked by a
/// CRC-8; a frame ends where the next such header starts. The scanner keeps a
/// window of bytes ahead of the frame it is on, so a source that cannot seek is
/// read once, forward.
final class FrameScanner {

    /// One frame: its bytes, the sample it starts at, and its block size.
    @SuppressWarnings("ArrayRecordComponent")
    record Frame(byte[] bytes, long firstSample, int blockSize) {}

    /// A parsed header.
    private record Header(int length, long number, boolean variableBlocksize, int blockSize) {}

    private static final int CHUNK = 64 * 1024;

    private final ByteReader in;
    private final int rate;
    private final int channels;
    /// The fixed block size, or 0 for a variable one.
    private final int fixedBlockSize;
    private final int maxFrame;
    private byte[] window = new byte[CHUNK];
    private int windowStart;
    private int windowEnd;
    private boolean ended;

    FrameScanner(ByteReader in, int rate, int channels, int fixedBlockSize, int maxFrame) {
        this.in = in;
        this.rate = rate;
        this.channels = channels;
        this.fixedBlockSize = fixedBlockSize;
        this.maxFrame = maxFrame;
    }

    /// The most a frame can be, as STREAMINFO said or FLAC's bound.
    int maxFrame() {
        return maxFrame;
    }

    /// Starts over at `position`, which may be anywhere: the next frame header
    /// from there is where reading resumes.
    void restart(long position) {
        in.seek(position);
        windowStart = 0;
        windowEnd = 0;
        ended = false;
    }

    /// The next frame, or null at the end of the stream.
    @Nullable
    Frame next() {
        // Find a header at or after the window start.
        Header header;
        while (true) {
            if (!ensure(windowStart + 16)) {
                if (windowEnd - windowStart < 4) {
                    return null;
                }
            }
            header = headerAt(windowStart);
            if (header != null && nextHeaderConfirms(windowStart + header.length())) {
                break;
            }
            windowStart++;
            if (windowStart >= windowEnd) {
                compact();
                if (ended && windowEnd - windowStart < 4) {
                    return null;
                }
            }
        }
        // The frame runs to the next confirmed header, or to the end.
        var at = windowStart + header.length();
        while (true) {
            if (!ensure(at + 16)) {
                if (ended) {
                    at = windowEnd;
                    break;
                }
            }
            if (at >= windowEnd) {
                at = windowEnd;
                break;
            }
            if ((window[at] & 0xFF) == 0xFF && at + 1 < windowEnd && (window[at + 1] & 0xFE) == 0xF8) {
                var candidate = headerAt(at);
                if (candidate != null && nextHeaderConfirms(at + candidate.length())) {
                    break;
                }
            }
            at++;
            if (at - windowStart > maxFrame) {
                throw new MediaException(new MediaError.InvalidData("a FLAC frame longer than " + maxFrame + " bytes"));
            }
        }
        var bytes = Arrays.copyOfRange(window, windowStart, at);
        windowStart = at;
        var firstSample = header.variableBlocksize() ? header.number() : header.number() * fixedBlockSize;
        if (!header.variableBlocksize() && fixedBlockSize == 0) {
            // A fixed-blocksize stream whose STREAMINFO states a range: count by
            // this frame's own block size, which is right for every frame but the last.
            firstSample = header.number() * header.blockSize();
        }
        return new Frame(bytes, firstSample, header.blockSize());
    }

    /// Whether a header parses at `at + length`, or the stream ends there: what
    /// tells a real header from a sync code inside a frame's data.
    private boolean nextHeaderConfirms(int at) {
        if (!ensure(at + 16) && at >= windowEnd) {
            return true; // the last frame
        }
        if (at >= windowEnd) {
            return true;
        }
        if (at + 2 > windowEnd) {
            return false;
        }
        if ((window[at] & 0xFF) != 0xFF || (window[at + 1] & 0xFE) != 0xF8) {
            // Not a header right after this header: fine, the frame has data. A
            // header is confirmed by its CRC alone then.
            return true;
        }
        return headerAt(at) != null;
    }

    /// Makes at least `end` bytes of window available, reading more if needed.
    ///
    /// @return whether `end` is within the window
    private boolean ensure(int end) {
        while (windowEnd < end && !ended) {
            if (end > window.length) {
                if (end > window.length) {
                    window = Arrays.copyOf(window, Math.max(window.length * 2, end));
                }
            }
            var room = window.length - windowEnd;
            var count = Math.min(room, CHUNK);
            if (count == 0) {
                window = Arrays.copyOf(window, window.length * 2);
                continue;
            }
            var read = readInto(windowEnd, count);
            if (read <= 0) {
                ended = true;
                break;
            }
            windowEnd += read;
        }
        return windowEnd >= end;
    }

    private int readInto(int at, int count) {
        var got = 0;
        while (got < count && !in.atEnd()) {
            window[at + got] = (byte) in.u8();
            got++;
            if (got >= 4096) {
                break;
            }
        }
        return got;
    }

    private void compact() {
        if (windowStart == 0) {
            return;
        }
        System.arraycopy(window, windowStart, window, 0, windowEnd - windowStart);
        windowEnd -= windowStart;
        windowStart = 0;
    }

    /// The header at `at`, or null when the bytes there are not one.
    private @Nullable Header headerAt(int at) {
        if (at + 6 > windowEnd) {
            return null;
        }
        if ((window[at] & 0xFF) != 0xFF || (window[at + 1] & 0xFE) != 0xF8) {
            return null;
        }
        var variable = (window[at + 1] & 0x01) != 0;
        var blockCode = (window[at + 2] & 0xFF) >>> 4;
        var rateCode = window[at + 2] & 0x0F;
        var channelCode = (window[at + 3] & 0xFF) >>> 4;
        var depthCode = (window[at + 3] & 0x0E) >>> 1;
        if (blockCode == 0
                || rateCode == 15
                || channelCode > 10
                || depthCode == 3
                || depthCode == 7
                || (window[at + 3] & 0x01) != 0) {
            return null;
        }
        var channelsHere = channelCode < 8 ? channelCode + 1 : 2;
        if (channelsHere != channels) {
            return null;
        }
        var p = at + 4;
        // The UTF-8-like coded number.
        var first = window[p] & 0xFF;
        int extra;
        long number;
        if (first < 0x80) {
            extra = 0;
            number = first;
        } else if ((first & 0xE0) == 0xC0) {
            extra = 1;
            number = first & 0x1F;
        } else if ((first & 0xF0) == 0xE0) {
            extra = 2;
            number = first & 0x0F;
        } else if ((first & 0xF8) == 0xF0) {
            extra = 3;
            number = first & 0x07;
        } else if ((first & 0xFC) == 0xF8) {
            extra = 4;
            number = first & 0x03;
        } else if ((first & 0xFE) == 0xFC) {
            extra = 5;
            number = first & 0x01;
        } else if (first == 0xFE) {
            extra = 6;
            number = 0;
        } else {
            return null;
        }
        if (p + 1 + extra + 3 > windowEnd) {
            return null;
        }
        p++;
        for (var i = 0; i < extra; i++) {
            var b = window[p++] & 0xFF;
            if ((b & 0xC0) != 0x80) {
                return null;
            }
            number = (number << 6) | (b & 0x3F);
        }
        int blockSize;
        switch (blockCode) {
            case 1 -> blockSize = 192;
            case 2, 3, 4, 5 -> blockSize = 576 << (blockCode - 2);
            case 6 -> {
                if (p + 1 > windowEnd) {
                    return null;
                }
                blockSize = (window[p++] & 0xFF) + 1;
            }
            case 7 -> {
                if (p + 2 > windowEnd) {
                    return null;
                }
                blockSize = ((window[p] & 0xFF) << 8 | (window[p + 1] & 0xFF)) + 1;
                p += 2;
            }
            default -> blockSize = 256 << (blockCode - 8);
        }
        var rateHere = switch (rateCode) {
            case 0 -> rate;
            case 1 -> 88_200;
            case 2 -> 176_400;
            case 3 -> 192_000;
            case 4 -> 8_000;
            case 5 -> 16_000;
            case 6 -> 22_050;
            case 7 -> 24_000;
            case 8 -> 32_000;
            case 9 -> 44_100;
            case 10 -> 48_000;
            case 11 -> 96_000;
            case 12, 13, 14 -> -1;
            default -> -1;
        };
        if (rateCode == 12) {
            if (p + 1 > windowEnd) {
                return null;
            }
            rateHere = (window[p++] & 0xFF) * 1000;
        } else if (rateCode == 13 || rateCode == 14) {
            if (p + 2 > windowEnd) {
                return null;
            }
            rateHere = ((window[p] & 0xFF) << 8 | (window[p + 1] & 0xFF)) * (rateCode == 13 ? 1 : 10);
            p += 2;
        }
        if (rateHere != rate) {
            return null;
        }
        if (p + 1 > windowEnd) {
            return null;
        }
        var crc = window[p] & 0xFF;
        if (crc8(window, at, p - at) != crc) {
            return null;
        }
        return new Header(p + 1 - at, number, variable, blockSize);
    }

    /// FLAC's CRC-8, polynomial 0x07, over `length` bytes from `from`.
    static int crc8(byte[] bytes, int from, int length) {
        var crc = 0;
        for (var i = from; i < from + length; i++) {
            crc ^= bytes[i] & 0xFF;
            for (var bit = 0; bit < 8; bit++) {
                crc = (crc & 0x80) != 0 ? ((crc << 1) ^ 0x07) & 0xFF : (crc << 1) & 0xFF;
            }
        }
        return crc;
    }
}
