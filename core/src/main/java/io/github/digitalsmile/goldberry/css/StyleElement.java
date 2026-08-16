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

    /// Whether a state pseudo-class currently holds.
    ///
    /// Never asked about [Selector.PseudoClass#ROOT] — that is answered by
    /// [#parent()] being null, so an implementation cannot get it wrong or
    /// disagree with the tree it lives in.
    boolean hasState(Selector.PseudoClass state);
}
