/// The web view in its window form: a web page the application asks the
/// desktop to show in a platform window of its own.
///
/// [dev.goldberry.widgets.shell.web.WebPage] is a value, since the desktop's
/// engine and not Goldberry draws the page, and
/// [dev.goldberry.widgets.shell.web.WebViews] is the call that opens it,
/// returning empty where the web view library is not available. This form
/// serves the sessions where a page cannot be a child of the window, which on
/// Linux means Wayland; the widget form is `web-view` in `widgets.core.web`.
///
/// Null-marked: every reference is non-null unless it says `@Nullable`.
///
/// Read more:
/// [Markdown, HTML and the web](https://goldberry.dev/docs/components/content.html#the-web-view).
@NullMarked
package dev.goldberry.widgets.shell.web;

import org.jspecify.annotations.NullMarked;
