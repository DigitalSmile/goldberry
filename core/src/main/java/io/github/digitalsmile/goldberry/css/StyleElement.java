package io.github.digitalsmile.goldberry.css;

import java.util.Set;

/// What the cascade needs to know about a node to style it.
///
/// The element tree of [ADR-0004] does not exist yet. This is the seam it will
/// implement — deliberately the smallest set of questions a selector can ask, so
/// that matching and the cascade can be built and tested now and the element tree
/// can arrive later without either changing.
///
/// It is also the reason `ARCHITECTURE.md` §8's selector subset stops where it
/// does. There is no `nextSibling()` here and no `indexInParent()`, so `+`, `~`
/// and `:nth-child` cannot be expressed — which is the point: every one of them
/// forces the matcher to know about ordering, and ordering is what makes
/// invalidation expensive.
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
    String type();

    /// The `id`, or null. At most one per element.
    String id();

    /// The classes on this element. Never null; empty is normal.
    Set<String> classes();

    /// The element this one sits inside, or null if it is the root.
    ///
    /// The only structural question the matcher asks, and the reason a selector
    /// is matched right to left: this walks up, and there is no way to walk down.
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
    /// valid without anything having to tell them
    /// ([ADR-0152](../../../../../../book/src/adr/0152-the-cascade-looks-at-rules-that-could-match.md)).
    ///
    /// The same scheme the computed style already uses (ADR-0070), one level
    /// down: a cascade is to custom properties what a style resolve is to a
    /// [ComputedStyle].
    ///
    /// @param resolver  the resolver asking, compared by identity
    /// @param inherited what this element's parent handed down, by identity
    default java.util.Map<String, java.util.List<Token>> cachedCustomProperties(
            StyleResolver resolver, java.util.Map<String, java.util.List<Token>> inherited) {
        return null;
    }

    /// Remembers what [#cachedCustomProperties] should answer next time.
    ///
    /// The default does nothing, so an element that does not want a cache simply
    /// has none.
    default void cacheCustomProperties(StyleResolver resolver,
            java.util.Map<String, java.util.List<Token>> inherited,
            java.util.Map<String, java.util.List<Token>> resolved) {
    }

    /// Whether a state pseudo-class currently holds.
    ///
    /// Never asked about [Selector.PseudoClass#ROOT] — that is answered by
    /// [#parent()] being null, so an implementation cannot get it wrong or
    /// disagree with the tree it lives in.
    boolean hasState(Selector.PseudoClass state);
}
