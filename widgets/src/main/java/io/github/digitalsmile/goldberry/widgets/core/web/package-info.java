/// `docs/core-widgets.md` §9's `web-view` as a widget — a web page inside the window,
/// sized by layout like any other box (ADR-0442).
///
/// [io.github.digitalsmile.goldberry.widgets.core.web.WebView] keeps the page in a
/// platform child window positioned over its box. Where a page cannot be a child,
/// which on Linux means Wayland, it draws why instead; the window form lives in
/// `…widgets.shell.web`.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.core.web;

import org.jspecify.annotations.NullMarked;
