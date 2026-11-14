# ADR-0684: A page runs the application's script first, and hears from it

- **Status:** Accepted
- **Date:** 2026-11-09
- **Relates to:** ADR-0441 (a web page is a window, not a box), the page-to-app callbacks (`WebViewSpec.on`)

## Context

A downstream messenger hosts mini apps. Each expects an API object
(`window.Telegram.WebApp`) to exist before its first line runs, and expects
events pushed into it from the host. A page could already call the
application through bindings. But the application could not run a script
before the page's own (upstream webview's `webview_init` was not bound), and
`eval`, which the shim had, was on `BackendWebView` and not on the
`WebViewController` an application holds. The workaround was a bootstrap page
served by the client, which defeats a third party's own URL.

## Decision

- **`goldberry_webview_init(w, js)`** in the shim, over upstream
  `webview_init`. The web view ABI goes from 8 to 9. `Webview.init` and
  `BackendWebView.init` sit above it.
- **`WebViewSpec.initScript(String)`** and `WebPage.initScript` and
  `WebView.initScript` add scripts in order. The engine adds them after the
  callbacks and before the first navigation, so the first document sees them.
  Like the callbacks, they are not part of what `WebPage.showsSameAs`
  compares.
- **`WebViewController.eval(String)`** runs script in the open page, and
  **`postMessage(String json)`** dispatches a `message` event whose `data` is
  the JSON as given. Both answer false while no page is open, as `cookies`
  answers with a failed stage.

## Consequences

- A `libgoldberry-webview` of ABI 8 left on a path is refused by name, as the
  ABI check has always done.
- "Painted overlays cannot cover the page" is unchanged: a page is a native
  child window (ADR-0441).
- Tested by `WebviewInitTest`, a real WebKitGTK page in a child JVM, where the
  document's first inline script reports the global the init script defined,
  and an `eval` reaches a binding. Also by `ExportListTest`, which checks the
  shim and the Java bindings agree on the new symbol,
  `WebViewControllerTest` (eval, postMessage's script, init scripts in order)
  and `WebViewSpecTest`.
