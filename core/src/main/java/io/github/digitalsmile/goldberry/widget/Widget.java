package io.github.digitalsmile.goldberry.widget;

import io.github.digitalsmile.goldberry.bind.Observable;
import java.util.List;

/// An immutable description of a piece of user interface.
///
/// The first of the three trees ([ADR-0004]). A widget is a **value**: cheap to
/// build, cheap to throw away, and holding no state. It is not the thing on
/// screen — it is a description of what the thing on screen should be, and the
/// element tree is what turns descriptions into something persistent.
///
/// Application authors write records implementing one of the two shapes below
/// and mostly never see an [Element] at all.
///
/// ## The two shapes
///
/// - [Stateless] — `build()` returns other widgets. Composition.
/// - [Stateful] — owns a [State] that survives rebuilds and can ask for more of
///   them.
///
/// and one more that only the toolkit implements:
///
/// - [Leaf] — has no children to build and produces a box directly. `text`,
///   `icon`, `spacer`.
///
/// ## Keys
///
/// [#key()] is what tells the reconciler that two widgets at the same position in
/// two different builds are *the same node* — so its state, focus and animation
/// carry over. Without one, widgets are matched by type and position, which is
/// right until a list is reordered. Give list items a key.
public interface Widget {

    /// This widget's identity within its parent, or null for "match by
    /// position".
    ///
    /// Compared with `equals`, so a `String`, an `Integer` or a record all work.
    default Object key() {
        return null;
    }

    /// The value this widget's content comes from, or null for the usual case.
    ///
    /// §9's `bind`. A widget that returns one is subscribed by its element for as
    /// long as that element lives, and a change marks the element as needing a
    /// build — so the value reaches the screen by the same route a `setState`
    /// does ([ADR-0062]).
    ///
    /// An [Observable] and not a `Property`: a widget reads and watches, and
    /// **cannot write**. What the user does travels back up as an action, and the
    /// application decides what it means ([ADR-0063]).
    ///
    /// Deliberately on the widget rather than on a wrapper. A `Bound` widget
    /// wrapping the real one would put an extra element between a node and its
    /// parent, and `panel > text` would then match an unbound `text` and miss a
    /// bound one — the same node, styled differently for a reason no stylesheet
    /// can see.
    ///
    /// **What the value means is the widget's own business.** For `text` it is the
    /// content; for a future `checkbox` it will be the checked state, which that
    /// control also writes back to. The framework only knows when to rebuild.
    default Observable<?> binding() {
        return null;
    }

    /// A widget built from other widgets.
    interface Stateless extends Widget {

        /// Describes this widget in terms of others.
        ///
        /// Must be **pure**: it is called whenever the framework needs a fresh
        /// description, which is more often than an author can usefully predict,
        /// and anything it mutates will be mutated an unpredictable number of
        /// times.
        Widget build(BuildContext context);
    }

    /// A widget with state that outlives its rebuilds.
    interface Stateful extends Widget {

        /// Creates the state for one element.
        ///
        /// Called once when the element is first mounted, not on every rebuild —
        /// that is the whole point of the element layer.
        State<?> createState();
    }

    /// A widget that produces a box rather than more widgets.
    ///
    /// The bottom of the tree. Implemented inside the toolkit; an application
    /// that wants to draw arbitrarily uses a `canvas` widget rather than this.
    interface Leaf extends Widget {

        /// The children to lay this widget's own content around, if any.
        ///
        /// Most leaves have none. A `row` is a leaf in the sense that it paints
        /// nothing itself, but it still has children to place.
        default List<Widget> children() {
            return List.of();
        }
    }
}
