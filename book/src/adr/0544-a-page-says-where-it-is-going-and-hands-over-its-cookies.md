# ADR-0544: A page says where it is going and hands over its cookies

- **Status:** Accepted. Extends [ADR-0441](0441-a-web-page-is-a-window-not-a-box.md)
  and [ADR-0448](0448-a-page-calls-back-through-a-name-it-was-given.md); bumps
  the web view shim's ABI from 7 to 8.
- **Date:** 2026-10-02
- **Relates to:** [ADR-0442](0442-a-page-is-a-child-window-where-the-window-system-allows-one.md),
  [ADR-0444](0444-a-page-stands-aside-for-a-modal.md),
  [ADR-0458](0458-a-page-on-macos-is-a-view-not-a-window.md),
  [ADR-0521](0521-every-natives-jar-carries-the-web-view.md),
  `docs/goldberry-gaps.md` #29

## Context

Deploy Orc has to call Grafana's API for a user who is not an Org Admin. Grafana
there accepts its HttpOnly `grafana_session` cookie or a service-account token,
and login is Keycloak SSO only. The natural fix is to let the user sign in in a
`web-view` and use the session the page ends up with. Two things stood in the
way, and neither is reachable from the page's own script:

- **An HttpOnly cookie is invisible to JavaScript** by design. Only the engine's
  cookie store holds it: `WKHTTPCookieStore` on macOS, WebKitGTK's
  `WebKitCookieManager`, WebView2's `ICoreWebView2CookieManager`.
- **A redirect to a custom scheme** (`myapp://callback?code=…`), which is how an
  OAuth sign-in comes back to a desktop application without a loopback server,
  is a URL no engine can load. The only moment it exists is the engine's
  decision to try: `decidePolicyForNavigationAction`, WebKitGTK's
  `decide-policy`, WebView2's `NavigationStarting`.

The shim exported neither. `BackendWebView` offered navigate, html, eval,
bounds and the load state, and JS bindings through ADR-0448.

The packaging half of #29, that no published natives jar carried the web view,
was ADR-0521 and is not this record's.

## Decision

**Two shim exports, ABI 8, and two calls on the Java side: a page's cookies for
a URL, HttpOnly ones included, and a predicate asked before every navigation
that can cancel it.**

- `goldberry_webview_cookies(w, url, fn, request)` reads the engine's jar for
  `url` and answers through `fn(request, text)` later, on the UI thread: GLib's
  context is drained by `pump`, and AppKit's run loop and the Win32 queue by
  SDL's own pump. The answer is **text**, one cookie per line and seven
  tab-separated fields (name, value, domain, path, expiry in seconds or -1,
  secure, http-only), with `\\ \t \n \r` escaped. One shape for three engines'
  cookie types, and a field added later is a column, not a struct layout two
  languages must agree on. NULL means the engine tried and could not; -1 from
  the call means it never will.
  - WebKitGTK and WebView2 match cookies to the URL themselves. WKWebView's
    `getAllCookies:` hands back every cookie, so the shim applies RFC 6265's
    domain-match, path-match and Secure rule, with NSHTTPCookie's convention
    that a domain without a leading dot is that host only.
- `goldberry_webview_on_navigate(w, fn, page)` asks `fn(page, uri)` inside the
  engine's own decision; zero cancels. A second call replaces the first, NULL
  removes it, and destroying the page removes it before the engine goes.
  - **Main frame only, except on Linux.** WKWebView says which frame through
    `targetFrame.isMainFrame` and WebView2's `NavigationStarting` is the main
    frame's; WebKitGTK's `decide-policy` does not say, so on Linux a frame's own
    navigations are asked about too. New-window requests are not asked about:
    webview/webview connects no handler that would open one.
  - On macOS this is a `WKNavigationDelegate` of the shim's own class, which
    webview.h does not set; the decision handler is a block the shim calls
    through Clang's block ABI.
- Java: `Webview.cookies(String)` → `CompletableFuture<List<HttpCookie>>` and
  `Webview.onNavigate(Predicate<String>)` in `:natives`, each with one upcall
  stub for the process and a number to find the question by, as ADR-0448's
  bindings have. A cookie read still outstanding when its page closes fails
  then, rather than waiting for an answer that would come to nobody.
- `:core`: `BackendWebView.cookies(URI)` returns a `CompletionStage`, failed
  with `UnsupportedOperationException` by default.
  `WebViewSpec.onNavigate(Predicate<URI>)` is installed before the first
  navigation, as callbacks are. A URI `java.net.URI` will not parse is let
  through, and so is every navigation when the predicate throws.
- `:widgets`: `WebPage.onNavigate`, so a `WebViews.open` page has it, and
  `WebView.onNavigate` and `WebView.controller(WebViewController)` for the
  embedded one. The widget always hooks its page and asks the **current** page
  value's predicate, so a rebuild with a new lambda takes effect without a
  reload, as ADR-0449 requires of everything else about a page.

## Consequences

- A sign-in can finish inside the application: the user signs in in a page, the
  predicate catches `myapp://callback?code=…` and cancels it, and
  `controller.cookies(url)` reads the session.
- On Linux this is tested with a real page. `WebviewSignInTest` runs a loopback
  server that sets an HttpOnly cookie and redirects to `myapp://callback?code=x`.
  The probe sees the redirect, reads both cookies with the right flags, and a
  navigation it refuses never reaches the server. It skips without a display.
- macOS and Windows are written against the documentation and compiled by
  nobody yet. That includes the block literal, which needs `-fblocks` (CMake
  passes it on Darwin), the WebView2 handlers, and the cookie matching on macOS.
- A stale `libgoldberry-webview` of ABI 7 is refused at bind time with the
  existing message, and no page opens.
- On Linux and macOS every page shares one jar, so a session signed in to in
  one page is readable from any other. That is the engines' own model and is
  documented, not hidden.
