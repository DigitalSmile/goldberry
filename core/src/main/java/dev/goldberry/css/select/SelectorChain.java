package dev.goldberry.css.select;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.StyleElement;

/// Everything a descendant or child combinator can read above an element:
/// each node on the [StyleElement#selectorParent()] walk, with what a compound
/// asks of it.
///
/// Two chains are equal exactly when no selector could tell them apart, so a
/// tree styled under one does not need restyling under the other. That is what
/// a popup asks after every frame of the window under it: whether anything on
/// its opener's chain changed (a class, a state, a node replaced, a sibling
/// added) since it was last styled.
///
/// The nodes compare by identity, as elements do, and everything else by value.
///
/// @param links the opener first, then its selector parents up to the root
public record SelectorChain(List<Link> links) {

    /// No chain: what a tree that was opened from nothing has above its root.
    public static final SelectorChain NONE = new SelectorChain(List.of());

    public SelectorChain {
        links = List.copyOf(links);
    }

    /// The chain from `start` up, or [#NONE] for null.
    public static SelectorChain of(@Nullable StyleElement start) {
        if (start == null) {
            return NONE;
        }
        var links = new ArrayList<Link>();
        for (StyleElement node = start; node != null; node = node.selectorParent()) {
            links.add(Link.of(node));
        }
        return new SelectorChain(links);
    }

    /// One node, as a compound sees it.
    ///
    /// @param node     the element itself, compared by identity
    /// @param type     its type, or null for a composition node
    /// @param id       its id, or null
    /// @param classes  its classes
    /// @param states   the state pseudo-classes that hold for it
    /// @param position where it sits among its siblings, of how many, and the
    ///                 same among those of its type: what the structural
    ///                 pseudo-classes read
    public record Link(
            StyleElement node,
            @Nullable String type,
            @Nullable String id,
            Set<String> classes,
            Set<Selector.PseudoClass> states,
            List<Integer> position) {

        public Link {
            classes = Set.copyOf(classes);
            states = Set.copyOf(states);
            position = List.copyOf(position);
        }

        static Link of(StyleElement node) {
            var states = EnumSet.noneOf(Selector.PseudoClass.class);
            for (var state : Selector.PseudoClass.values()) {
                // ROOT is the tree's answer and not the node's, and is never asked.
                if (state != Selector.PseudoClass.ROOT && node.hasState(state)) {
                    states.add(state);
                }
            }
            return new Link(
                    node,
                    node.type(),
                    node.id(),
                    node.classes(),
                    states,
                    List.of(node.indexInParent(), node.siblingCount(), node.indexOfType(), node.typeCount()));
        }

        /// The node by identity, the rest by value: two elements that happen
        /// to look alike are still two places in a tree.
        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof Link link
                    && link.node == node
                    && Objects.equals(link.type, type)
                    && Objects.equals(link.id, id)
                    && link.classes.equals(classes)
                    && link.states.equals(states)
                    && link.position.equals(position);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(node) * 31 + classes.hashCode();
        }
    }
}
