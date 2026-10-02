package dev.goldberry.widget;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Observable;

/// An immutable description of a piece of user interface.
///
/// A widget is a **value**: cheap to build, cheap to throw away, and holding no
/// state. It is not the thing on screen; it describes what the thing on screen
/// should be, and the element tree turns descriptions into something persistent.
/// An application writes records that implement one of the shapes below and
/// mostly never sees an [Element] at all.
///
/// ```java
/// record Greeting(String name) implements Widget.Stateless {
///     public Widget build(BuildContext context) {
///         return new Text("Hello, " + name);
///     }
/// }
/// ```
///
/// ## The three shapes
///
/// - [Stateless] — `build()` returns other widgets. Composition.
/// - [Stateful] — owns a [State] that survives rebuilds and can ask for more of
///   them.
/// - [Leaf] — has no widgets to build and produces a box of its own. `text`,
///   `icon` and `spacer` are leaves, and so is a custom widget that paints.
///
/// ## Keys
///
/// [#key()] tells the reconciler that two widgets at the same position in two
/// different builds are *the same node*, so its state, focus and animation carry
/// over. Without one, widgets are matched by type and position, which is right
/// until a list is reordered. Give list items a key.
///
/// Read more:
/// [Writing a widget](https://goldberry.dev/docs/guide/writing-a-widget.html#the-three-shapes).
public interface Widget {

    /// A widget that describes **nothing at all**: no box, no space, no selector.
    ///
    /// Every `build` has to return a widget. A widget with nothing to show could
    /// describe an empty box, but an empty box is still a child, so a `column`
    /// with a `gap` puts the gap round it and the thing that vanished leaves a
    /// hole. This describes no box, so the layout never hears about it.
    ///
    /// ```java
    /// return text.isBlank() ? Widget.nothing() : new MessageBox(…);
    /// ```
    ///
    /// The element stays in the tree, holding its state, its place in the
    /// reconciler and its binding, which is the point: a widget that describes
    /// nothing this frame and something the next is one node whose value changed.
    ///
    /// **Not `display: none`.** A stylesheet still has no way to take a node out
    /// of a layout. What a widget decides about its own content it may say here;
    /// what a stylesheet decides is unchanged.
    ///
    /// A method rather than a constant: a `static final` field on an interface
    /// that holds an instance of one of its own subtypes makes initialising
    /// [Widget] depend on initialising [Nothing] and back again, which is a
    /// class-initialisation cycle. The instance is a singleton either way; it
    /// carries no state, so a second one would only give the reconciler something
    /// to tell apart.
    static Widget nothing() {
        return Nothing.INSTANCE;
    }

    /// This widget's identity within its parent, or null for "match by
    /// position".
    ///
    /// Compared with `equals`, so a `String`, an `Integer` or a record all work.
    default @Nullable Object key() {
        return null;
    }

    /// The value this widget's content comes from, or null for the usual case.
    ///
    /// This is what `bind=` in markup sets. A widget that returns one is
    /// subscribed by its element for as long as that element lives, and a change
    /// marks the element as needing a build, so the value reaches the screen by
    /// the same route a `setState` does.
    ///
    /// An [Observable] and not a `Property`: a widget reads and watches, and
    /// **cannot write**. There is no `set` to call, so a control built from markup
    /// cannot reach the application's model; what the user did travels back up as
    /// an action, and the application decides what it means.
    ///
    /// On the widget rather than on a wrapper, because a `Bound` widget wrapping
    /// the real one would put an extra element between a node and its parent, and
    /// `panel > text` would then match an unbound `text` and miss a bound one.
    ///
    /// **What the value means is the widget's own business.** For `text` it is the
    /// content; for `checkbox` it is the checked state. The framework only knows
    /// when to rebuild.
    ///
    /// Read more:
    /// [Attributes and binding](https://goldberry.dev/docs/guide/writing-a-widget.html#attributes-and-binding).
    default @Nullable Observable<?> binding() {
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
        /// Called once when the element is first mounted, not on every rebuild;
        /// that is the whole point of the element layer.
        State<?> createState();
    }

    /// A widget that produces a box rather than more widgets.
    ///
    /// The bottom of the tree. A leaf usually also implements `Styled`, so a
    /// stylesheet can see it, and `Paints`, so it can turn its resolved style into
    /// a box. An application that only wants to draw uses a `canvas` widget
    /// instead of writing a leaf.
    ///
    /// Read more: [Paints](https://goldberry.dev/docs/guide/writing-a-widget.html#paints-from-a-style-to-a-box).
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
