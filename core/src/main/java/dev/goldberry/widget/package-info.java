/// The widget and element trees: the declarative layer an application writes in,
/// and the persistent tree that state, focus and the cascade hang off.
///
/// A widget is an immutable value, cheap to build and to throw away. An element is
/// its persistent instantiation, updated in place while a compatible widget keeps
/// appearing at its position, which is what gives a node identity. `setState` marks
/// an element dirty, and one flush per frame rebuilds each at most once. The widget
/// renderer, the element tree's own paint pass, turns the built tree into styled
/// boxes and stays here to share its style cache.
///
/// The package is null-marked: a parameter or return is non-null unless annotated
/// `@Nullable`.
///
/// Read more: [Writing a widget](https://goldberry.dev/docs/guide/writing-a-widget.html).
@NullMarked
package dev.goldberry.widget;

import org.jspecify.annotations.NullMarked;
