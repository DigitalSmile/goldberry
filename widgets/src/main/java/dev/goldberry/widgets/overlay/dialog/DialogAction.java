package dev.goldberry.widgets.overlay.dialog;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// One button in a [Dialog]'s action bar, and **the role it plays**.
///
/// ```kdl
/// dialog title="Unsaved changes" {
///     text "Your draft has not been saved."
///     action role="dismissive" press="app.stay" "Keep editing"
///     action role="affirmative" press="app.discard" "Discard"
/// }
/// ```
///
/// ## Why a role and not a class
///
/// A dialog needs two things a plain row of buttons cannot give it: a button
/// for `Esc` and one for `Enter`, and the **platform's button order** applied
/// by the action bar automatically. Both need the dialog to know *which button is which*, and a
/// class is a styling hook that anybody may put on anything — a dialog reading
/// `.primary` to decide what `Enter` does would be a keyboard map that a theme
/// could break.
///
/// So the role is a value on the action, and the class that follows from it is
/// the dialog's to write: an affirmative is `button.primary`, and the others are
/// what a `button` is by default.
///
/// A description rather than a widget that draws itself —
/// [dev.goldberry.widgets.panel.tabs.Tab]'s
/// arrangement, and for the same reason: the bar decides the order, so the
/// buttons cannot each decide where they go.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#action).
///
/// @param label      what the button says
/// @param role       what pressing it means — see [Role]
/// @param onPress    what it does; the dialog wraps this so the panel closes
///                   *first* and the application is told when the closing
///                   animation is over
/// @param attributes the `id` and classes, which land on the button
@Markup("action")
public record DialogAction(
        String label, Role role, @Nullable Runnable onPress, Attributes attributes)
        implements Widget.Leaf, Attributed<DialogAction> {

    /// What pressing a button means: the two roles a key presses, and the one
    /// no key does.
    public enum Role {

        /// The one `Enter` presses: Save, Discard, Replace. **At most one** — two
        /// default buttons is a dialog where `Enter` is a coin toss, and the
        /// dialog refuses to build one.
        AFFIRMATIVE,

        /// The one `Esc` presses, and the one a press on the scrim means: Cancel,
        /// Keep editing. At most one, for [#AFFIRMATIVE]'s reason.
        DISMISSIVE,

        /// Everything else — "Don't save" in the three-button save dialog, "Help".
        /// No key, and any number of them.
        ///
        /// The default, because a button whose role nobody stated is a button
        /// that should not silently acquire `Enter`.
        NEUTRAL;

        /// The role a document named.
        ///
        /// @throws IllegalArgumentException if the word is not one of the three
        public static Role of(@Nullable String text) {
            if (text == null || text.isBlank()) {
                return NEUTRAL;
            }
            try {
                return valueOf(text.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "a dialog action's role is \"affirmative\", \"dismissive\" or" + " \"neutral\", not \"" + text
                                + "\"",
                        e);
            }
        }
    }

    /// Written out so that the parameters taking null for a default can say so.
    public DialogAction(
            String label, @Nullable Role role, @Nullable Runnable onPress, @Nullable Attributes attributes) {
        Objects.requireNonNull(label, "label");
        role = role == null ? Role.NEUTRAL : role;
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.label = label;
        this.role = role;
        this.onPress = onPress;
        this.attributes = attributes;
    }

    /// An action with a role and something to do.
    public DialogAction(String label, Role role, Runnable onPress) {
        this(label, role, onPress, Attributes.NONE);
    }

    @Override
    public DialogAction withAttributes(Attributes value) {
        return new DialogAction(label, role, onPress, value);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// Builds an `action` from markup.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new DialogAction(
                Wiring.label(node),
                Role.of(node.stringProperty("role")),
                wiring.action(node, "press"),
                Attributes.of(node));
    }
}
