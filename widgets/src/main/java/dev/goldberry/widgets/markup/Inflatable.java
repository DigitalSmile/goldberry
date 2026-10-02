package dev.goldberry.widgets.markup;

import java.util.List;

import dev.goldberry.kdl.KdlInflater;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.Controls;

/// How one markup node becomes one widget.
///
/// A factory is a `static` method on the widget's own class, referenced as
/// `Button::inflate`, and the inflater calls it with the node, the node's
/// already-inflated children and the [Wiring] its names resolve against.
///
/// It is a `dev.goldberry.kdl.KdlInflater.Factory` with the wiring added, and it
/// lives on the widget rather than as a lambda in [Controls] because a widget's
/// markup contract belongs beside the record it builds: the Java, the KDL
/// attributes and the CSS type of one widget are then described in one file,
/// and [Controls] stays a list of names.
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html#parsing-and-inflating).
@FunctionalInterface
public interface Inflatable {

    /// Builds one node.
    ///
    /// @param node     the markup node, for its arguments and properties
    /// @param children this node's children, already inflated
    /// @param wiring   what a name in the node resolves against
    Widget inflate(KdlNode node, List<Widget> children, Wiring wiring);

    /// Registers factories against one [Wiring].
    ///
    /// What turns `Button::inflate` into the two-argument factory a
    /// [KdlInflater] takes, so a catalog reads as a list of names and the wiring
    /// is bound once rather than captured nineteen times.
    ///
    /// Ordered, and deliberately not a `Map`: the order names are registered in
    /// is the order an unknown node is reported against, and that list is the
    /// most useful thing an error message about a typo can say.
    final class Catalog {

        private final KdlInflater<Widget> inflater;
        private final Wiring wiring;

        /// Adds to `inflater`, resolving names against `wiring`.
        public Catalog(KdlInflater<Widget> inflater, Wiring wiring) {
            this.inflater = inflater;
            this.wiring = wiring;
        }

        /// Registers one name.
        ///
        /// @throws IllegalStateException if the name is already registered: an
        ///         application may shadow a built-in, but doing so silently, at
        ///         whichever point its registration happened to run, is not a
        ///         good way to find out
        public Catalog add(String name, Inflatable factory) {
            inflater.register(name, (node, children) -> factory.inflate(node, children, wiring));
            return this;
        }

        /// The inflater everything was added to.
        public KdlInflater<Widget> inflater() {
            return inflater;
        }
    }
}
