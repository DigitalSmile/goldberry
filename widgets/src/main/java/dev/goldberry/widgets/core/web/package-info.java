/// The `web-view` widget — a web page inside the window, kept in a platform child
/// window and sized by layout like any other box.
///
/// [dev.goldberry.widgets.core.web.WebView] keeps the page in a
/// platform child window positioned over its box. Where a page cannot be a child,
/// which on Linux means Wayland, it draws why instead; the window form lives in
/// `…widgets.shell.web`.
///
/// Null-marked: every reference is non-null unless annotated otherwise.
///
/// Read more: [Markdown, HTML and the web](https://goldberry.dev/docs/components/content.html#the-web-view).
@NullMarked
package dev.goldberry.widgets.core.web;

import org.jspecify.annotations.NullMarked;
