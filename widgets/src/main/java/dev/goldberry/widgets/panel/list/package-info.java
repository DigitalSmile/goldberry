/// The `list`: a vertical list over an item model, with any widget as a row, a
/// selection model and full keyboard navigation.
///
/// [dev.goldberry.widgets.panel.list.ListView] is the widget,
/// named so that it does not shadow `java.util.List`; its styled node is `list`.
/// [dev.goldberry.widgets.panel.list.Selection] is the none,
/// single or multi model that `tree` and `table` share. The list is virtualized when
/// it is told a row height. In markup a `list` is a
/// [dev.goldberry.widgets.markup.Bound], placed by the document
/// and described by the application. The rows and the spacers standing in for unbuilt
/// rows are parts.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Collections](https://goldberry.dev/docs/components/collections.html#list).
@NullMarked
package dev.goldberry.widgets.panel.list;

import org.jspecify.annotations.NullMarked;
