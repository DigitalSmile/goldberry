/// The widget and element trees: the declarative layer an application writes in,
/// and the persistent tree that state, focus and the cascade hang off (ADR-0004,
/// ADR-0052).
///
/// A widget is an immutable value, cheap to build and to throw away. An element is
/// its persistent instantiation, updated in place while a compatible widget keeps
/// appearing at its position, which is what gives a node identity. `setState` marks
/// an element dirty, and one flush per frame rebuilds each at most once. The widget
/// renderer, the element tree's own paint pass, turns the built tree into styled
/// boxes and stays here to share its style cache.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package dev.goldberry.widget;

import org.jspecify.annotations.NullMarked;
