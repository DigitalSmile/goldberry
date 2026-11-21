package dev.goldberry.widget.style;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.cascade.ScopedRules;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// Rules for `child` and everything under it, and for nothing else.
///
/// ```java
/// new StyleScope(ScopedRules.of(rules), page)
/// ```
///
/// A composition node: no type, no classes, no box, so it changes nothing about
/// how `child` lays out, and a descendant selector in the window's sheets
/// matches through it. A child combinator does not, as with every composition
/// node: `.page > .x` is not a match for an `.x` with a scope between it and
/// `.page`. What it adds is [ScopedRules] to the cascade of every node below
/// it, ranked above every normal declaration in the window's sheets. A scope
/// inside a scope ranks above the outer one.
///
/// A popup opened from inside a scope is a tree of its own and is not under
/// it, which is what a document's styles should reach: the document.
///
/// Rebuilt with different rules, it restyles its whole subtree; rebuilt with
/// equal ones, it keeps every cached style below it.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#the-cascade-four-layers).
///
/// @param rules what applies under this node
/// @param child the subtree
/// @param key   the node's key, or null
public record StyleScope(
        ScopedRules rules, Widget child, @Nullable Object key) implements Widget.Stateless {

    /// Written out so that the parameter taking null for a default can say so.
    public StyleScope(ScopedRules rules, Widget child, @Nullable Object key) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.child = Objects.requireNonNull(child, "child");
        this.key = key;
    }

    /// `rules` over `child`, with no key.
    public StyleScope(ScopedRules rules, Widget child) {
        this(rules, child, null);
    }

    @Override
    public Widget build(BuildContext context) {
        return child;
    }
}
