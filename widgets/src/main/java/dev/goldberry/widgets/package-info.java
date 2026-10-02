/// The front door of the widget catalogue: what an application wires a window
/// up with before it names a single widget.
///
/// [dev.goldberry.widgets.Widgets] turns markup into widgets with every widget
/// module on the path already registered; [dev.goldberry.widgets.Controls]
/// supplies the stylesheets that give the widgets their look;
/// [dev.goldberry.widgets.Icons] is what an `icon=` attribute resolves against;
/// [dev.goldberry.widgets.Density] and [dev.goldberry.widgets.Scrollbars] are
/// the two preferences an application passes along with its theme. The widgets
/// themselves are in the sub-packages, one package per control.
///
/// Every reference is non-null unless it says `@Nullable`.
///
/// Read more: [The catalogue](https://goldberry.dev/docs/components/index.html).
@NullMarked
package dev.goldberry.widgets;

import org.jspecify.annotations.NullMarked;
