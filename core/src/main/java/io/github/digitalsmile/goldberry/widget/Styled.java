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

    /// The widget's last word on its own style — the cascade's answer, with
    /// whatever only the widget can know written into it.
    ///
    /// §8's cascade layers end with `inline`: "a `style=` on a single node. Last,
    /// because it is the most specific statement anyone can make about one
    /// element." This is that layer, typed. A stylesheet cannot say where the
    /// **third of five** segments is, because it cannot count the segments; the
    /// widget can, and this is where it says so.
    ///
    /// ## Why it is here and not in `render`
    ///
    /// [Paints#render] already receives a style and could change what it draws
    /// with — and a value changed there would **snap**, because [WidgetRenderer]
    /// has already observed the cascade's style and started whatever transitions
    /// it declared. Applied here, a widget-computed value is part of what the
    /// animation observes, so it moves under `transition` like any other
    /// property: a segmented control's indicator translates between segments
    /// because this method puts the translation where the transition can see it
    /// ([ADR-0099](../../../../../../book/src/adr/0099-an-indicator-travels-on-a-grid.md)).
    ///
    /// ## What it is not
    ///
    /// Not a way around the stylesheet. It runs **after** the cascade, so
    /// anything it sets is unthemeable and unoverridable — which is right for a
    /// number nobody else can compute and wrong for everything else. The rule
    /// that keeps it honest: a widget may write here only what a stylesheet
    /// could not have written, and the toolkit's own use is exactly two values,
    /// both of them derived from a count no selector can express.
    ///
    /// The style is also what this node's children inherit, so a widget that
    /// changed `color` here would change theirs. That is CSS's rule for an
    /// inline style and is the reason this returns a whole style rather than a
    /// patch: the node has one style, and this is it.
    ///
    /// @param resolved what the cascade produced for this node
    default io.github.digitalsmile.goldberry.css.ComputedStyle restyle(
            io.github.digitalsmile.goldberry.css.ComputedStyle resolved) {
        return resolved;
    }

    /// The classes this widget computes from the **frame being rendered**, on top
    /// of its own.
    ///
    /// Empty for every widget but one, and the one is the point: a `hud` reading
    /// carries `over` or `near` depending on how the number it is about to draw
    /// compares with its budget, and a stylesheet has to be able to colour that
    /// ([ADR-0150](../../../../../../book/src/adr/0150-a-hud-reads-itself-against-a-budget.md)).
    ///
    /// **Why it cannot be [#classes()]**: the cascade reads a node's classes
    /// before that node's `render` runs, and the frame statistics only arrive in
    /// `render`. A widget is a value described once and drawn many times, so it
    /// cannot hold the answer either. This is the same shape as the pseudo-classes
    /// the renderer already mirrors from `isChecked()` and `isDisabled()` — a fact
    /// the widget knows and the element has to carry — with the frame added,
    /// because that is what this fact is about.
    ///
    /// **Not a licence to style by the frame.** Anything derivable from the
    /// widget belongs in [#classes()], which costs nothing per frame; this is for
    /// a value that is genuinely a property of the loop.
    ///
    /// @param frames what the loop has been doing, never null
    default java.util.Set<String> classes(
            io.github.digitalsmile.goldberry.FrameStats frames) {
        return java.util.Set.of();
    }

    /// Whether this node has pinned itself, for `:affixed`.
    ///
    /// `affix`'s, and nothing else's. Mirrored onto the element exactly as
    /// `:disabled` and `:checked` are, so a stylesheet, a hit test and the widget
    /// agree about it without three of them asking separately.
    default boolean isAffixed() {
        return false;
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

    /// Whether this node is on, for `:checked`.
    ///
    /// A widget's own fact for the same reason [#isDisabled()] is: nothing about
    /// the pointer or the keyboard says whether a checkbox is ticked, only the
    /// value it was described with. Mirrored onto the element by [WidgetRenderer]
    /// on the same pass and by the same argument — the stylesheet and the
    /// semantics must not be able to disagree about it.
    ///
    /// False for a control in the mixed state: see [#isIndeterminate()].
    default boolean isChecked() {
        return false;
    }

    /// Whether this node is in the mixed state, for `:indeterminate`.
    ///
    /// Mutually exclusive with [#isChecked()] — three states, two flags, and the
    /// third is both false. A control answering true to both would match
    /// `checkbox:checked` and `checkbox:indeterminate` at once and paint whichever
    /// rule the cascade happened to prefer.
    default boolean isIndeterminate() {
        return false;
    }

    /// Whether this node's value has failed validation, for `:invalid`
    /// (`docs/core-widgets.md` §4).
    ///
    /// A **widget's** state and not the router's, like `:checked` and unlike
    /// `:hover`: what decides it is a `Validator` the application supplied, run
    /// at a moment the field chose. Nothing about the pointer or the keyboard can
    /// answer it.
    ///
    /// Reported by the `field` *and* by the control inside it, and both are
    /// wanted: a stylesheet asks for `text-input:invalid` to redden the border
    /// and for `field:invalid field-message` to show the reason, and §8's subset
    /// has no way to walk from a child back up to its parent.
    default boolean isInvalid() {
        return false;
    }
}
