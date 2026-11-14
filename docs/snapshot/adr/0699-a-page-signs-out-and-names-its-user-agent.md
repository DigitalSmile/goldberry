# ADR-0699: A page signs out, and names its user agent

- **Status:** Accepted
- **Date:** 2026-11-14
- **Relates to:** ADR-0544 (a page says where it is going and hands over its
  cookies), ADR-0684 (a page's API is there before its scripts)

## Context

A messenger signs in to identity providers through a web view and must be
able to sign out, leaving no session behind, and some providers refuse an
agent they do not know (Tessera's G-32, here T-32). The web view could read
its jar and nothing else, and every page sends the engine's own user agent.

The entry also asks for a private store. On every engine that is decided
when the view is created, and webview/webview creates its views on the
default context, so it needs either the shim to build views itself or a patch
on webview/webview. The plan split it off for that reason.

## Decision

Three functions in the web view shim, under the same ABI 9 that W1.11 took
the library to, since nothing was released in between:

- `goldberry_webview_delete_cookies(w, url, name, fn, request)` deletes the
  cookies the engine would send to `url`, every one or those named `name`,
  and answers how many went. WebKitGTK reads them and deletes each
  (`webkit_cookie_manager_delete_cookie`), WKWebView filters
  `getAllCookies:` as the read does and deletes each, and WebView2 deletes
  each of a `GetCookies` read.
- `goldberry_webview_clear_cookies(w, fn, request)` empties the jar:
  `webkit_website_data_manager_clear` with `WEBKIT_WEBSITE_DATA_COOKIES`,
  `removeDataOfTypes:modifiedSince:` with the cookie type, and
  `DeleteAllCookies`.
- `goldberry_webview_user_agent(w, agent)`: `webkit_settings_set_user_agent`,
  `customUserAgent` and `ICoreWebView2Settings2::put_UserAgent`. NULL puts
  the engine's own back, except on WebView2, which has no way back.

Both changes are asynchronous as a read is, answered through one upcall stub
by a request number. WebView2 answers at once, so Java registers the question
before it asks. In Java: `Webview.deleteCookies`, `clearCookies` and
`userAgent`, the same three on `BackendWebView` (refused by default) and on
`WebViewController`, and `WebViewSpec.userAgent`, applied before the first
navigation so the first request carries it.

The macOS and Windows halves are **UNVERIFIED**: written against the
headers, marked so in the shim, and run by no lane here.

The private store is **not** in this record. It waits for the shim to build
its own views or carry a webview/webview patch.

## Consequences

- `WebViewSpec` has an eleventh component, `userAgent`; the ten-argument
  constructor stays.
- A fifteenth upcall shape, `(long, int) -> void`, which the native-image
  metadata declares with the rest; `ForeignSurfaceTest` counts it.
- On Linux and macOS the jar is the process's, so a sign-out through one page
  signs every page out, which is what a sign-out means there.
- Tested by `WebviewSignInTest` against WebKitGTK and a loopback server
  (delete one cookie by name, the server receiving the agent set, and an empty
  jar after a clear) and `WebViewSpecTest` (the agent carried through every
  other change, and a page that cannot do any of it refusing).
