package io.github.digitalsmile.goldberry.render.web;

/// What a page calls when its own script calls back into the application —
/// the toolkit's word for
/// `io.github.digitalsmile.goldberry.natives.webview.WebviewCallback`.
///
/// A separate type for [WebSize]'s reason ([ADR-0280]): no type of `:natives`
/// appears in a signature an application can read.
///
/// ## What crosses
///
/// A bound name becomes a global JavaScript function, so
/// `window.save({title: "note"})` in the page arrives here as the string
/// `[{"title":"note"}]` — the arguments as a JSON **array**, exactly as the
/// engine hands them over.
///
/// It is **not parsed**. Goldberry ships no JSON reader and binding one for the
/// sake of a callback would put a dependency in the toolkit that every
/// application pays for and few would use. A page that wants to send one value
/// sends one value; a page that wants structure brings a reader
/// ([ADR-0448]).
///
/// ## The page is waiting on the answer
///
/// The call is a promise in the page, and what this returns resolves it. The
/// value must be **valid JSON or empty** — `42`, `"done"` *with* its quotes,
/// `{"ok":true}`, or `""` for `undefined`. A bare word is not JSON and the
/// page's `await` rejects on it.
///
/// **Throwing rejects the promise**, with the exception's message as the reason.
/// That is the designed path for failure rather than a fallback: a handler that
/// cannot answer has still told the page something, and a page awaiting a value
/// that never arrives is a hang with no error anywhere.
///
/// ## On the UI thread
///
/// The engine's loop is drained once a frame by the frame loop, so a handler
/// runs on the thread that paints — the rule every widget callback follows. It
/// may read and write application state and call `setState`. It must not block:
/// the page's promise and the next frame are both waiting on it.
@FunctionalInterface
public interface WebCallback {

    /// Handles one call from the page.
    ///
    /// @param arguments the arguments as a JSON array, never null — `[]` when
    ///        the page passed none
    /// @return the JSON value to resolve the promise with, or `""` for
    ///         `undefined`
    /// @throws RuntimeException to reject the promise, the message being the
    ///         reason the page receives
    String call(String arguments);
}
