package io.github.digitalsmile.goldberry.natives.webview;

/// What a page calls when it calls back.
///
/// `webview_bind` makes a name a global JavaScript function, so
/// `window.<name>(1, "two")` in the page reaches this with `[1,"two"]` — the
/// arguments as a JSON **array**, which is the shape the engine hands over and
/// is not unpacked here. Goldberry ships no JSON parser and binding one for this
/// would put a dependency in `:natives` for the sake of a callback; what crosses
/// is text, and an application that wants objects brings its own reader.
///
/// ## The page is waiting
///
/// The call is a promise on the JS side, and whatever this returns resolves it.
/// A returned value must be **valid JSON or empty** — `"42"`, `"\"done\""`,
/// `"{\"ok\":true}"`, or `""` for `undefined`. A bare `done` is not JSON and the
/// page's `await` would reject on it.
///
/// Throwing **rejects** the promise, with the exception's message as the reason.
/// That is deliberate rather than a fallback: a handler that fails has told the
/// page something, and a page awaiting a value that never arrives is a hang with
/// no error anywhere.
///
/// ## On the UI thread
///
/// The engine's loop is driven by `Webview.pump()`, which the frame loop calls,
/// so a handler runs on the thread that paints — the same rule every widget
/// callback in the toolkit follows. It may touch state and call `setState`; it
/// must not block, because the page's promise and the next frame are both
/// waiting on it.
@FunctionalInterface
public interface WebviewCallback {

    /// Handles one call from the page.
    ///
    /// @param arguments the arguments as a JSON array, never null — `[]` when
    ///        the page passed none
    /// @return the JSON value to resolve the promise with, or `""`/null for
    ///         `undefined`
    /// @throws RuntimeException to reject the promise, with the message as the
    ///         reason
    String call(String arguments);
}
