/// `docs/core-widgets.md` §9's `web-view` in its window form — a web page the
/// application asks the desktop to show in a platform window of its own (ADR-0441).
///
/// [dev.goldberry.widgets.shell.web.WebPage] is a value, since
/// WebKit and not Goldberry draws the page, and
/// [dev.goldberry.widgets.shell.web.WebViews] is the call that
/// opens it, returning empty where the web view library is not available. This form
/// serves the platforms and sessions where a page cannot be a child of the window,
/// which on Linux means Wayland; the widget form is `…widgets.core.web`.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widgets.shell.web;

import org.jspecify.annotations.NullMarked;
