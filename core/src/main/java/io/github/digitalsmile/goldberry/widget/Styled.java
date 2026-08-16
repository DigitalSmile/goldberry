package io.github.digitalsmile.goldberry.widget;

import java.util.Set;

/// A widget that a stylesheet can name.
///
/// §9 says `class` / `id` / `style` behave as in HTML, and §11's parity invariant
/// says every built-in widget is CSS-styleable. This is the interface that makes
/// both true: an element asks its widget for these, so the same widget value
/// answers a Java caller, a KDL attribute and a CSS selector.
///
/// Not every widget implements it. A composition-only widget — one that exists to
/// return other widgets — has nothing to style, and giving it a type name would
/// put a node in the cascade that no author knows about.
public interface Styled extends Widget {

    /// The type name a CSS type selector matches: `button`, `text-input`.
    ///
    /// Defaults to the class name in kebab-case, so `TextInput` is `text-input`
    /// without anyone writing it down twice.
    default String cssType() {
        var simple = getClass().getSimpleName();
        var name = new StringBuilder();
        for (var i = 0; i < simple.length(); i++) {
            var c = simple.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    name.append('-');
                }
                name.append(Character.toLowerCase(c));
            } else {
                name.append(c);
            }
        }
        return name.toString();
    }

    /// The `#id`, or null.
    default String id() {
        return null;
    }

    /// The `.class` names.
    default Set<String> classes() {
        return Set.of();
    }

    /// Whether this node is disabled, for `:disabled`.
    ///
    /// The one pseudo-class a **widget** owns rather than the router. `:hover`,
    /// `:active` and `:focus` are facts about the pointer and the keyboard, and
    /// input derives them; `:disabled` is a fact about the description, and only
    /// the widget knows it.
    ///
    /// Mirrored onto the element by [WidgetRenderer] on every render, so it
    /// survives a rebuild the same way the router's states do — and so a
    /// stylesheet, a hit test and an activation all agree about it without three
    /// of them asking the widget separately.
    default boolean isDisabled() {
        return false;
    }
}
