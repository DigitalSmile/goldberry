/// `docs/core-widgets.md` §5's `tabs` and its `tab` — a strip of tab headers over one
/// panel showing the selected tab's content.
///
/// [io.github.digitalsmile.goldberry.widgets.panel.tabs.Tabs] reads the selected tab
/// through `bind` and reports picks, closes and new-tab requests; only the selected
/// tab's content is built. Each
/// [io.github.digitalsmile.goldberry.widgets.panel.tabs.Tab] is a header with a
/// label, an optional icon and colour, and the content it carries. The list, the
/// panel, the indicator and the close and add affordances are parts and stay in here
/// (ADR-0107).
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import org.jspecify.annotations.NullMarked;
