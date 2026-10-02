/// The `tabs` strip and its `tab`s: a row of tab headers over one panel showing
/// the selected tab's content.
///
/// [dev.goldberry.widgets.panel.tabs.Tabs] reads the selected tab
/// through `bind` and reports picks, closes and new-tab requests; only the selected
/// tab's content is built. Each
/// [dev.goldberry.widgets.panel.tabs.Tab] is a header with a
/// label, an optional icon and colour, and the content it carries. The list, the
/// panel, the indicator and the close and add affordances are parts and stay in here.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Panels](https://goldberry.dev/docs/components/panels.html#tabs).
@NullMarked
package dev.goldberry.widgets.panel.tabs;

import org.jspecify.annotations.NullMarked;
