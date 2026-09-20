package io.github.digitalsmile.goldberry.render.web;

/// How far through loading a page is — the toolkit's word for
/// `io.github.digitalsmile.goldberry.natives.webview.LoadState`.
///
/// A separate enum for [WebSize]'s reason ([ADR-0280]): no type of `:natives`
/// appears in a signature an application can read. `WebViewEngine` translates
/// between the two with an exhaustive switch, which is the line that fails to
/// compile when a constant is added to one and not the other.
///
/// ## What it is for
///
/// A page is a platform window above the frame, so nothing Goldberry paints can
/// cover it — including any indication that it is still loading. From the moment
/// the window is mapped until the document paints, what a user sees is WebKit's
/// default white, for however long the network takes.
///
/// The only answer is to keep the page out of sight until it has something to
/// show and paint a `spinner` in the widget's own box meanwhile, and that needs
/// somebody to ask whether it has ([ADR-0445]).
public enum WebLoad {

    /// The engine would not say — not a WebKit build, or a platform where this
    /// has not been written.
    ///
    /// **Read as "show the page".** A build that cannot answer must behave as
    /// every build did before there was a question to ask, which means a page
    /// that appears when it is opened rather than one that never appears at all.
    UNKNOWN,

    /// Open, and never asked for anything — the state between the page being
    /// created and the `navigate` that follows it. As unready as [#LOADING], and
    /// the reason this is not a boolean.
    IDLE,

    LOADING,

    /// The last load ended. Not necessarily well: WebKit substitutes its own
    /// error document for a page that would not load, and showing that beats a
    /// spinner that never stops.
    FINISHED;

    /// Whether there is anything worth looking at yet.
    ///
    /// The question every caller actually has, so that "unknown counts as ready"
    /// is decided once here rather than at each call site.
    public boolean isReady() {
        return this == FINISHED || this == UNKNOWN;
    }
}
