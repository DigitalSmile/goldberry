package io.github.digitalsmile.goldberry.natives.webview;

/// How far through loading a page is.
///
/// The shim computes this from two WebKit facts — `webkit_web_view_is_loading`
/// and `webkit_web_view_get_estimated_load_progress` — because the interesting
/// state is the one neither reports alone: a page created and never navigated is
/// not loading and has made no progress, and is exactly as unready as one still
/// fetching.
///
/// Polled rather than signalled. See `goldberry_webview_load_state` for why.
public enum LoadState {

    /// The shim could not say — an engine that is not WebKit, or a platform
    /// where this has not been written. Treated as [#FINISHED] by callers, so a
    /// build that cannot answer behaves as every build did before there was a
    /// question to ask.
    UNKNOWN(-1),

    /// Created, and never asked for anything. The state between
    /// `goldberry_webview_create_embedded` and the `navigate` that follows it.
    IDLE(0),

    LOADING(1),

    /// The last load ended. Not necessarily well: WebKit substitutes an error
    /// document for a page that would not load, and that is still something to
    /// show rather than a spinner that never stops.
    FINISHED(2);

    private final int value;

    LoadState(int value) {
        this.value = value;
    }

    /// The number `goldberry_webview_load_state` returns.
    public int value() {
        return value;
    }

    /// Reads one back.
    ///
    /// An unrecognised number answers [#UNKNOWN] rather than throwing, which is
    /// the rule
    /// [io.github.digitalsmile.goldberry.natives.sdl.SdlSubsystem#decode]
    /// states: this is asked once a frame from a painter, and a shim that grew a
    /// fifth state should make a page appear rather than make a frame throw.
    public static LoadState of(int value) {
        for (var state : values()) {
            if (state.value == value) {
                return state;
            }
        }
        return UNKNOWN;
    }
}
