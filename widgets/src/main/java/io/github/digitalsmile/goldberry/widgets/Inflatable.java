package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.kdl.KdlInflater;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;

/// How one markup node becomes one widget.
///
/// [io.github.digitalsmile.goldberry.kdl.KdlInflater.Factory] with the [Wiring]
/// added, and the reason it exists rather than being a lambda in [Controls]: a
/// widget's own class is where its markup contract belongs. `Button.inflate` sits
/// beside the record it builds and the javadoc describing the attributes it
/// reads, so the three forms §9 requires every widget to have — Java, KDL and CSS
/// — are three things in one file rather than one thing in three
/// ([ADR-0130](../../../../../../book/src/adr/0130-a-widget-inflates-itself.md)).
///
/// A factory is a `static` method referenced as `Button::inflate`, which is what
/// keeps [Controls] a list of names.
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
        /// @throws IllegalStateException if the name is already registered —
        ///         §9 lets an application shadow a built-in, but silently and at
        ///         whichever point its registration ran is not a good way to find
        ///         that out
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
