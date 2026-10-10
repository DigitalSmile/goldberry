package dev.goldberry.media.platform.macos;

import java.io.Serial;

import org.jspecify.annotations.Nullable;

/// Apple's `OSStatus` error codes, and the exception a failed framework call
/// raises.
///
/// Core Audio's codes are four-character codes (`'fmt?'`), and VideoToolbox's
/// and Core Media's are negative numbers in documented ranges. Both are shown the
/// way Apple's headers name them, so a log line can be searched for.
final class OsStatus {

    /// `noErr`.
    static final int OK = 0;

    /// `kVTInvalidSessionErr`: the session is gone, as after the machine slept
    /// or the GPU changed. A new session decodes from the next keyframe.
    static final int VT_INVALID_SESSION = -12_903;

    /// `kVTVideoDecoderBadDataErr`: one packet the decoder could not read.
    static final int VT_BAD_DATA = -12_909;

    private OsStatus() {}

    /// `status` as Apple's headers write it: a four-character code in quotes, a
    /// known name, or the number.
    static String describe(int status) {
        @Nullable
        String name = switch (status) {
            case OK -> "noErr";
            case -12_900 -> "kVTPropertyNotSupportedErr";
            case -12_902 -> "kVTParameterErr";
            case VT_INVALID_SESSION -> "kVTInvalidSessionErr";
            case -12_904 -> "kVTAllocationFailedErr";
            case -12_906 -> "kVTCouldNotFindVideoDecoderErr";
            case -12_909 -> "kVTVideoDecoderBadDataErr";
            case -12_910 -> "kVTVideoDecoderUnsupportedDataFormatErr";
            case -12_911 -> "kVTVideoDecoderMalfunctionErr";
            case -12_913 -> "kVTVideoDecoderNotAvailableNowErr";
            case -17_694 -> "kVTVideoDecoderReferenceMissingErr";
            case -8_969 -> "codecBadDataErr";
            case -50 -> "kAudio_ParamError";
            default -> null;
        };
        if (name != null) {
            return name + " (" + status + ")";
        }
        var fourCc = fourCc(status);
        return fourCc != null ? "'" + fourCc + "' (" + status + ")" : Integer.toString(status);
    }

    /// `status` as four printable ASCII characters, or null when it is not one.
    static @Nullable String fourCc(int status) {
        var chars = new char[4];
        for (var i = 0; i < 4; i++) {
            var c = (status >>> (24 - 8 * i)) & 0xFF;
            if (c < 0x20 || c > 0x7E) {
                return null;
            }
            chars[i] = (char) c;
        }
        return new String(chars);
    }

    /// The four-character code `code` as the `int` Apple's APIs take.
    static int code(String code) {
        if (code.length() != 4) {
            throw new IllegalArgumentException("a four-character code has four characters: '" + code + "'");
        }
        var value = 0;
        for (var i = 0; i < 4; i++) {
            var c = code.charAt(i);
            if (c > 0x7E) {
                throw new IllegalArgumentException("'" + code + "' is not ASCII");
            }
            value = (value << 8) | c;
        }
        return value;
    }

    /// Throws [Failure] when `status` is not `noErr`.
    static void check(String function, int status) {
        if (status != OK) {
            throw new Failure(function, status);
        }
    }

    /// A framework call that answered an error.
    static final class Failure extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        private final int status;

        Failure(String function, int status) {
            super(function + " failed: " + describe(status));
            this.status = status;
        }

        /// The `OSStatus` the call answered.
        int status() {
            return status;
        }
    }
}
