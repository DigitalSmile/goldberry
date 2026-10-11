package dev.goldberry.css;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.container.QueryContainer;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.select.PseudoElement;
import dev.goldberry.css.select.Selector;

/// What the cascade needs to know about a node to style it: its type, id,
/// classes, parent and state.
///
/// The element tree implements it, and a test can hand the resolver a
/// hand-written one. It is deliberately the smallest set of questions a selector
/// can ask, so that matching and the cascade depend on nothing else about the
/// tree.
///
/// An element says where it sits among its siblings, [#indexInParent()] of
/// [#siblingCount()], which is what `:first-child` and `:nth-child` read, and
/// hands out its [#siblings()] and [#children()], which is what `+`, `~` and
/// `:has()` walk. A tree that answers the last two is a tree that restyles an
/// element when a sibling or a descendant changes, under sheets that ask.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#selectors).
public interface StyleElement {

    /// The element type — `button`, `row`. Lowercase, matching the type
    /// selectors the parser produces.
    ///
    /// **May be null**, and a node with no type is the normal case for anything
    /// that exists only to compose. Such a node matches no type selector and
    /// carries no classes, so it is invisible to every selector except a
    /// descendant combinator passing through it — which is exactly how an
    /// unstyled `<div>` behaves. Deriving a name for those instead would make
    /// every private composition class selectable by accident.
    @Nullable
    String type();

    /// The `id`, or null. At most one per element.
    @Nullable
    String id();

    /// The classes on this element. Never null; empty is normal.
    Set<String> classes();

    /// The element this one sits inside, or null if it is the root.
    ///
    /// The only structural question the matcher asks, and the reason a selector
    /// is matched right to left: this walks up, and there is no way to walk down.
    @Nullable
    StyleElement parent();

    /// The custom properties this element resolved last time, if they are still
    /// good for `resolver` and for the `inherited` map its parent handed down.
    ///
    /// **Null means "ask again"**, and the default implementation always says so
    /// — which is correct and is what a test's hand-written element wants.
    ///
    /// This exists because collecting custom properties is a walk to the **root**:
    /// a node's are its parent's plus its own, so resolving one node at depth ten
    /// ran eleven cascades. Cached against the parent's map by identity, the walk
    /// collapses to one, and an unchanged parent keeps its children's entries
    /// valid without anything having to tell them.
    ///
    /// The same scheme the computed style already uses, one level down: a cascade
    /// is to custom properties what a style resolve is to a [ComputedStyle].
    ///
    /// @param resolver  the resolver asking, compared by identity
    /// @param inherited what this element's parent handed down, by identity
    default java.util.@Nullable Map<String, java.util.List<Token>> cachedCustomProperties(
            StyleResolver resolver, java.util.Map<String, java.util.List<Token>> inherited) {
        return null;
    }

    /// Remembers what [#cachedCustomProperties] should answer next time.
    ///
    /// The default does nothing, so an element that does not want a cache simply
    /// has none.
    default void cacheCustomProperties(
            StyleResolver resolver,
            java.util.Map<String, java.util.List<Token>> inherited,
            java.util.Map<String, java.util.List<Token>> resolved) {}

    /// This element's position among its parent's children, from 0.
    ///
    /// The default answers as if the element were alone, which is right for a
    /// root and for a test's hand-written element that never asks.
    default int indexInParent() {
        return 0;
    }

    /// How many children this element's parent has, this one included: 1 for a
    /// root.
    default int siblingCount() {
        return 1;
    }

    /// This element's position among its parent's children of its own
    /// [#type()], from 0, for `:first-of-type` and the rest. The default
    /// answers as [#indexInParent()] does.
    default int indexOfType() {
        return 0;
    }

    /// How many of its parent's children share this element's type, this one
    /// included: 1 for a root.
    default int typeCount() {
        return 1;
    }

    /// Its parent's children, this one among them, in order: what
    /// `:nth-child(An+B of S)` counts the matching ones of. A root, and an
    /// element that does not say, is alone.
    default List<? extends StyleElement> siblings() {
        return List.of(this);
    }

    /// Its children, in order: what `:has()` searches. An element that does
    /// not say has none.
    default List<? extends StyleElement> children() {
        return List.of();
    }

    /// The pseudo-element this element stands for, `::placeholder` say, or
    /// null for an element that stands for none, which is nearly all of them.
    default @Nullable PseudoElement pseudoElement() {
        return null;
    }

    /// What this element is as a query container, and the size it was last
    /// laid out at, or null when it is not one or has not been laid out.
    /// `@container` asks it of an element's ancestors.
    default @Nullable QueryContainer queryContainer() {
        return null;
    }

    /// Whether a state pseudo-class currently holds.
    ///
    /// Never asked about [Selector.PseudoClass#ROOT] — that is answered by
    /// [#parent()] being null, so an implementation cannot get it wrong or
    /// disagree with the tree it lives in.
    boolean hasState(Selector.PseudoClass state);
}
