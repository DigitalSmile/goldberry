package dev.goldberry.css;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.select.Selector;

/// What the cascade needs to know about a node to style it: its type, id,
/// classes, parent and state.
///
/// The element tree implements it, and a test can hand the resolver a
/// hand-written one. It is deliberately the smallest set of questions a selector
/// can ask, so that matching and the cascade depend on nothing else about the
/// tree.
///
/// It is also the reason the selector subset stops where it does. An element
/// says where it sits among its siblings, [#indexInParent()] of
/// [#siblingCount()], which is what `:first-child` and `:nth-child` read; it
/// does not hand out its siblings, so `+`, `~` and `:has()` cannot be expressed.
/// A position is a number the tree already knows when it reconciles a list of
/// children, and the tree invalidates the children whose position changed. A
/// sibling's content is not.
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

    /// Whether a state pseudo-class currently holds.
    ///
    /// Never asked about [Selector.PseudoClass#ROOT] — that is answered by
    /// [#parent()] being null, so an implementation cannot get it wrong or
    /// disagree with the tree it lives in.
    boolean hasState(Selector.PseudoClass state);
}
