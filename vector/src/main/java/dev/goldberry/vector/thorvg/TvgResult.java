package dev.goldberry.vector.thorvg;

/// ThorVG's `Tvg_Result`, by name: what a call that answers one said.
///
/// Read when a call fails, so that a message says `INVALID_ARGUMENT` rather than
/// `1`. The numbers are `thorvg_capi.h`'s.
public enum TvgResult {
    /// `TVG_RESULT_SUCCESS`.
    SUCCESS(0),
    /// `TVG_RESULT_INVALID_ARGUMENT`: for a document, bytes ThorVG would not read.
    INVALID_ARGUMENT(1),
    /// `TVG_RESULT_INSUFFICIENT_CONDITION`: asked of something not there yet.
    INSUFFICIENT_CONDITION(2),
    /// `TVG_RESULT_FAILED_ALLOCATION`.
    FAILED_ALLOCATION(3),
    /// `TVG_RESULT_MEMORY_CORRUPTION`.
    MEMORY_CORRUPTION(4),
    /// `TVG_RESULT_NOT_SUPPORTED`: a format or feature this build leaves out.
    NOT_SUPPORTED(5),
    /// `TVG_RESULT_SYSTEM_ERROR`.
    SYSTEM_ERROR(6),
    /// `TVG_RESULT_UNKNOWN`, and any number this enum does not list.
    UNKNOWN(255);

    private final int code;

    TvgResult(int code) {
        this.code = code;
    }

    /// The number ThorVG returns.
    public int code() {
        return code;
    }

    /// The result `code` names; [#UNKNOWN] for a number not listed.
    public static TvgResult of(int code) {
        for (var result : values()) {
            if (result.code == code) {
                return result;
            }
        }
        return UNKNOWN;
    }

    /// `ThorVG answered NOT_SUPPORTED (5)`, for a message.
    public static String describe(int code) {
        return "ThorVG answered " + of(code) + " (" + code + ")";
    }
}
