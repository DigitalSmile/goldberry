package io.github.digitalsmile.goldberry.widgets.controls.chip;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.attr.Bindable;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// A small rounded label you can choose and take away — `docs/core-widgets.md`
/// §3's `chip`.
///
/// ```kdl
/// chip "Draft"
/// chip dot=#true class="warning" "Degraded"
/// chip icon="filter" selected=#true press="app.toggle-filter" "Unread"
/// chip dismiss="app.drop-tag" "typescript"
/// ```
///
/// ## A chip is not a badge, and the difference is that you can press it
///
/// [io.github.digitalsmile.goldberry.widgets.controls.badge.Badge] is §3's
/// "count/status chip": a leaf with text, no focus, no state, nothing to click.
/// It answers *what is true* — three unread, one build failing. A chip answers
/// *what you picked*: a filter that is on or off, a tag you can take off a
/// document, a value that came from somewhere and can go back. That is a
/// control, so it is focusable, it carries `:checked`, and it has a keyboard
/// (ADR-0305).
///
/// The two look alike on purpose and share their metrics in `controls.css` for
/// the reason `select-chip` already shares them: §3 gives one row of numbers for
/// a stadium with words in it, and three rules that have to be kept agreeing is
/// how they stop agreeing.
///
/// ## The dot and the icon are one slot, not two
///
/// §3's row says "an optional leading **dot or icon**", and the widget refuses
/// both at once rather than stacking them. Two leading marks in a 20-tall stadium
/// is 8 points of glyph in front of the word it is about, and the second one is
/// never the one a reader looks at — but more to the point, the two mean the same
/// thing at different resolutions. A dot is a status nobody has to recognise; an
/// icon is a status they do. Asking for both is asking a question that has no
/// answer, so it is a refusal at construction rather than a drawing decision.
///
/// ## The dot's colour is data, not a class
///
/// The dot draws in the toolkit's colour until somebody says otherwise, and for
/// a status that a stylesheet can name — `chip.danger chip-dot` — that is the
/// whole story. It is not the whole story for a *Project*: a hue that lives in a
/// row of a database has no class a rule could be written for, and thirty
/// projects would be thirty rules an author cannot write in advance.
///
/// So [#withDot(int)] takes the colour itself, as `0xAARRGGBB` — the toolkit's
/// own currency at the paint boundary, which is what keeps a colour *type* out of
/// the widget API. `0` means "the stylesheet decides", exactly as it does for
/// every other colour a document may write (`docs/gaps.md` G36,
/// [ADR-0328]).
///
/// ## Selection is the document's, and that is not a `tab`'s answer
///
/// A [io.github.digitalsmile.goldberry.widgets.panel.tabs.Tab]'s `selected` is
/// supplied by its strip on every build, because a strip
/// exists to hold the invariant that exactly one is chosen. A chip has no strip:
/// a row of filter chips has none, two, or all of them on, and nothing about the
/// row makes that wrong. So `selected=#true` is an ordinary attribute a document
/// may write, mirrored to `:checked` — which is the pseudo-class, not a second
/// vocabulary, so `chip:checked` is the whole of styling a chosen one.
///
/// **It selects nothing itself.** Pressing raises `press` and the application
/// decides; a chip whose handler does nothing stays as it was, which is the
/// visible form of "the model did not change"
/// (ADR-0063).
///
/// `bind=` is the other half and is what makes a row of filters writable as a
/// document: the written `selected` is the fallback and the bound value wins,
/// which is `toggle`'s and `checkbox`'s arrangement exactly. An [Observable] and
/// never a `Property`, because one-way binding is the whole rule — markup names
/// where a value comes from and has no way to name where it goes
/// (ADR-0063).
///
/// ## Keyboard
///
/// `Space` and `Enter` press it, which is §3's rule for everything you press.
/// `Delete` and `Backspace` dismiss it when it has a dismiss, which is the
/// keyboard's answer to a × that is otherwise a 10-point pointer target — the
/// same argument `tab`'s `Delete` makes, and the same key.
///
/// @param label     what the chip reads; never empty, because a chip with no word
///                  in it is a coloured dot that nothing can announce (§13)
/// @param icon      an optional leading icon, mutually exclusive with [#dot]
/// @param dot       whether to draw the 6-point status dot before the label
/// @param dotColor  the dot's colour as `0xAARRGGBB`, or 0 for the stylesheet's
///                  — see [#withDot(int)]
/// @param selected  mirrored to `:checked` when nothing is bound — see
///                  [#resolved()]
/// @param source    §9's `bind=`, read-only
/// @param onPress   what the user asked to choose, or null for a chip that only
///                  reads
/// @param onDismiss what the user asked to take away, or null for a chip with no
///                  × at all
/// @param disabled  drawn and hit-tested as always, but neither pressable nor
///                  dismissable — [io.github.digitalsmile.goldberry.widgets.controls.button.Button]'s
///                  rule and its reason
/// @param attributes `id` and `class`, exactly as on every other widget
@Markup("chip")
public record Chip(
        String label,
        Icon icon,
        boolean dot,
        int dotColor,
        boolean selected,
        Observable<?> source,
        Runnable onPress,
        Runnable onDismiss,
        boolean disabled,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<Chip>, Bindable<Chip>, Semantics {

    public Chip {
        Objects.requireNonNull(label, "label");
        if (label.isEmpty()) {
            throw new IllegalArgumentException(
                    "a chip needs a label: a chip is a word you can choose, and one with no word"
                            + " is a coloured dot with nothing to read out (§13)");
        }
        if (dotColor != 0 && !dot) {
            throw new IllegalArgumentException("a chip that draws no dot has no dot to colour, and \"" + label
                    + "\" asked for one; withDot(argb) turns it on as well as colours it");
        }
        if (dot && icon != null) {
            throw new IllegalArgumentException("a chip takes a leading dot or a leading icon, not both --"
                    + " they are the same status at two resolutions, and \"" + label + "\" asked for each");
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A chip that only reads — the `badge`-shaped case, with a label and nothing
    /// else.
    public Chip(String label) {
        this(label, null, false, 0, false, null, null, null, false, Attributes.NONE);
    }

    /// A chip you can choose, which is the one this widget exists for.
    public Chip(String label, boolean selected, Runnable onPress) {
        this(label, null, false, 0, selected, null, onPress, null, false, Attributes.NONE);
    }

    /// This chip with a leading icon.
    ///
    /// The icon is **borrowed**, like a button's: a widget is a value rebuilt
    /// every frame, so it must not own something with a `close()` (ADR-0043).
    ///
    /// @throws IllegalArgumentException if this chip already asked for a dot
    public Chip withIcon(Icon value) {
        return new Chip(
                label,
                Objects.requireNonNull(value, "icon"),
                dot,
                dotColor,
                selected,
                source,
                onPress,
                onDismiss,
                disabled,
                attributes);
    }

    /// This chip with the status dot before its label.
    ///
    /// @throws IllegalArgumentException if this chip already has an icon
    public Chip withDot(boolean value) {
        return new Chip(
                label, icon, value, value ? dotColor : 0, selected, source, onPress, onDismiss, disabled, attributes);
    }

    /// This chip with the status dot before its label, **in this colour** —
    /// `0xAARRGGBB`, or 0 for whatever the stylesheet resolved.
    ///
    /// It turns the dot on as well as colouring it, because there is no other
    /// thing a colour for the dot could mean: a chip with `withDot(0xFFBF616A)`
    /// and no dot would be a value that says two contradictory things, and the
    /// constructor refuses that pair rather than picking one.
    ///
    /// A colour and not a class name, because the value is **data**: a Project's
    /// hue is a row in a database, and a stylesheet cannot have a rule per
    /// Project (`docs/gaps.md` G36, [ADR-0328]).
    ///
    /// @throws IllegalArgumentException if this chip already has an icon
    public Chip withDot(int argb) {
        return new Chip(label, icon, true, argb, selected, source, onPress, onDismiss, disabled, attributes);
    }

    /// This chip chosen, or not. Mirrored to `:checked`.
    public Chip selected(boolean value) {
        return new Chip(label, icon, dot, dotColor, value, source, onPress, onDismiss, disabled, attributes);
    }

    /// This chip reporting that the user chose it.
    public Chip onPress(Runnable handler) {
        return new Chip(label, icon, dot, dotColor, selected, source, handler, onDismiss, disabled, attributes);
    }

    /// This chip with a × that asks for it to be taken away.
    ///
    /// It raises the handler rather than removing anything: what a row of chips
    /// shows is the application's list, and only the application may shorten it
    /// — `tab`'s `close` exactly (ADR-0063).
    public Chip onDismiss(Runnable handler) {
        return new Chip(label, icon, dot, dotColor, selected, source, onPress, handler, disabled, attributes);
    }

    /// This chip, disabled or not.
    public Chip disabled(boolean value) {
        return new Chip(label, icon, dot, dotColor, selected, source, onPress, onDismiss, value, attributes);
    }

    /// Whether this chip is chosen **right now**: the bound value, or the
    /// written one.
    ///
    /// [io.github.digitalsmile.goldberry.widgets.controls.toggle.Toggle#resolved()]'s
    /// rule, including its fallback: a path that answers something which is not a
    /// boolean leaves the written value standing rather than guessing, because a
    /// filter that read `"yes"` as off would be a control quietly disagreeing
    /// with its model.
    public boolean resolved() {
        if (source == null) {
            return selected;
        }
        return source.get() instanceof Boolean value ? value : selected;
    }

    @Override
    public Chip bound(Observable<?> value) {
        return new Chip(label, icon, dot, dotColor, selected, value, onPress, onDismiss, disabled, attributes);
    }

    @Override
    public Observable<?> binding() {
        return source;
    }

    @Override
    public Chip withAttributes(Attributes value) {
        return new Chip(label, icon, dot, dotColor, selected, source, onPress, onDismiss, disabled, value);
    }

    @Override
    public String cssType() {
        return "chip";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public boolean isChecked() {
        return resolved();
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    /// Focusable only when there is something to do with it.
    ///
    /// A chip that neither presses nor dismisses is a `badge` with a different
    /// type name, and putting one in the Tab order would strand a keyboard user
    /// on a word — the same argument that keeps a disabled control out of it.
    /// This is the one place a chip and a badge really are the same widget, and
    /// the check is what says so.
    @Override
    public boolean isFocusable() {
        return !disabled && (onPress != null || onDismiss != null);
    }

    /// The dot or nothing, the label, and the × or nothing.
    ///
    /// The label is a **child box** rather than text on the chip's own node, for
    /// [io.github.digitalsmile.goldberry.widgets.controls.select.SelectChip]'s
    /// reason: a box with text is a measured leaf, Yoga never lays a measured
    /// node's children out, and a chip that held its own text would have nowhere
    /// to put the ×.
    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(3);
        if (dot) {
            parts.add(new ChipDot(dotColor));
        }
        parts.add(new ChipLabel(label));
        if (onDismiss != null) {
            parts.add(new ChipDismiss(disabled ? null : this::dismiss));
        }
        return List.copyOf(parts);
    }

    /// The icon goes **between** the dot slot and the label, which costs nothing
    /// because the two are mutually exclusive — there is no frame in which both
    /// a dot box and an icon box exist, so the order is a statement about where
    /// a leading mark goes rather than an arrangement of two of them.
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        if (icon == null) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
        var content = new ArrayList<Box>(children.size() + 1);
        content.add(Box.icon(icon, style.color()));
        content.addAll(children);
        return Box.of().style(style).children(content.toArray(Box[]::new));
    }

    @Override
    public void onPointer(PointerEvent event) {
        // The × consumes its own click before this sees it, so a dismiss is never
        // also a press -- `SelectChipRemove`'s rule, and the mistake it was
        // written to avoid.
        if (event.kind() == PointerEvent.Kind.CLICKED) {
            press();
            event.consume();
        }
    }

    /// `Space` and `Enter` press; `Delete` and `Backspace` dismiss.
    ///
    /// Both delete keys, unlike `tab`'s one. A tab lives in a strip a user is
    /// walking with the arrows and `Delete` is the forward-facing key there; a
    /// chip is commonly the last thing before a text field — a token in a
    /// recipient row — where `Backspace` is what a hand reaches for. Binding one
    /// and not the other would make it a coin toss.
    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED
                || event.isRepeat()
                || !event.modifiers().none()) {
            return;
        }
        if (event.key() == Key.SPACE || event.key() == Key.ENTER) {
            press();
            event.consume();
        } else if ((event.key() == Key.DELETE || event.key() == Key.BACKSPACE) && onDismiss != null) {
            dismiss();
            event.consume();
        }
    }

    private void press() {
        if (!disabled && onPress != null) {
            onPress.run();
        }
    }

    private void dismiss() {
        if (!disabled && onDismiss != null) {
            onDismiss.run();
        }
    }

    /// [Role#OPTION] — "one choice inside a list".
    ///
    /// Not [Role#BUTTON], which is the tempting one: a button makes something
    /// *happen* and reports nothing about itself afterwards, where a chip is a
    /// thing that is on or off and whose whole point is which. `OPTION` is the
    /// role a `select`'s rows and a `segmented`'s segments already carry, and a
    /// chip is the same fact in a different shape.
    ///
    /// A chip with no handlers still answers this. The role is what the node
    /// *is*, and a read-only chip in a row of pressable ones is one of the set
    /// whether or not this particular one responds — the same reading that keeps
    /// a disabled control's role rather than blanking it.
    @Override
    public Role role() {
        return Role.OPTION;
    }

    @Override
    public String accessibleName() {
        return label;
    }

    /// Builds a `chip` from markup.
    ///
    /// `press=` and `dismiss=` name actions, `icon=` names an icon,
    /// `dot-colour=`/`dot-color=` names the dot's colour, and `dot`,
    /// `selected` and `disabled` are flags — so every one of §11's three forms
    /// builds the same value, which is what the parity invariant asks for.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var colour = Wiring.colour(node, "dot-colour", "dot-color");
        return new Chip(
                Wiring.label(node),
                wiring.icon(node),
                // A colour is a dot: `dot-colour=` alone turns it on, which is the
                // one place markup would otherwise have to write two attributes to
                // say what `withDot(argb)` says in one ([ADR-0328]).
                node.booleanProperty("dot") || colour != 0,
                colour,
                node.booleanProperty("selected"),
                wiring.bound(node),
                wiring.action(node, "press"),
                wiring.action(node, "dismiss"),
                Wiring.disabled(node),
                Attributes.of(node));
    }
}
