package dev.goldberry.widgets.form.richinput;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.background.Background;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.event.PreeditEvent;
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.input.handler.ContextMenuOwner;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.handler.Located;
import dev.goldberry.input.handler.Measured;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.layout.Length;
import dev.goldberry.paint.Box;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.font.Font;
import dev.goldberry.text.mark.MarkStyle;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.form.parts.SpellingError;
import dev.goldberry.widgets.form.spelling.SpellingInk;

/// The node a stylesheet calls `rich-input`.
///
/// [RichInput] is stateful and styles nothing, so this carries the CSS type, the
/// `id` and the classes, the arrangement every stateful widget in this catalog
/// uses.
///
/// ## What it is made of
///
/// ```
/// rich-input        this node: clips, focuses, takes the keys and the pointer,
/// │                 and draws the text, the selection and the caret
/// └── mark × n      one per set of kinds in the text, styled and never drawn
/// ```
///
/// The text is drawn by the state's `Editor` through this node's painter, so
/// the caret, a click and the glyphs come out of one shaping. The `mark` nodes
/// are carriers: each renders an empty paragraph in its resolved font with its
/// colour and rules beside it, and its background as its fill, and this node
/// turns those into the [MarkStyle]s the editor draws with.
///
/// @param markSets    the class sets of the kinds in the text, one `mark` each
/// @param placeholder what an empty field shows, or `""`
/// @param label       what a screen reader hears: the placeholder, or the text
/// @param focusable   whether it takes the focus: not when disabled
/// @param editable    whether it asks for text input: not when read-only
/// @param handler     the state
/// @param attributes  the application's id, classes and key
/// @param spelling    whether it checks its spelling, which adds the
///                    `::spelling-error` part after the marks
record RichInputBox(
        List<Set<String>> markSets,
        String placeholder,
        String label,
        boolean focusable,
        boolean editable,
        Input handler,
        Attributes attributes,
        boolean spelling)
        implements Widget.Leaf, Styled, Paints, Handles, Measured, Located, ContextMenuOwner, Semantics {

    /// What the state hears, and what it is asked for while this node renders.
    interface Input {

        void pointer(PointerEvent event);

        void key(KeyEvent event);

        void text(TextEvent event);

        void preedit(PreeditEvent event);

        void focus(boolean focused);

        void measured(Extent bounds);

        /// Where the last frame drew the field, in the window: what the
        /// formatting bar and the link editor are placed against.
        void located(LogicalRect self);

        /// The text menu, at `anchor` in the window.
        boolean openContextMenu(LogicalRect anchor);

        Optional<LogicalRect> caretArea();

        double caretOffset();

        /// This frame's resolved looks, and the box to draw into.
        ///
        /// @param font        the field's own font
        /// @param looks       each class set's style
        /// @param colours     the text's, the placeholder's, the selection's
        /// @param placeholder the placeholder shaped in the field's font, or null
        /// @param style       the field's resolved style
        /// @param misspelled  how a misspelled word looks, or null when the
        ///                    field does not check its spelling
        /// @return the box: sized to the rows the text holds, painting the editor
        Box laidOut(
                Font font,
                Map<Set<String>, MarkStyle> looks,
                Colours colours,
                @Nullable Paragraph placeholder,
                ComputedStyle style,
                @Nullable SpellingInk misspelled);
    }

    /// The colours the field draws with that are not a mark's.
    ///
    /// @param text        the words and the caret
    /// @param placeholder an empty field's placeholder
    /// @param selection   the highlight behind selected words
    record Colours(int text, int placeholder, int selection) {}

    /// The custom property the selection's wash comes from, shared with every
    /// other field.
    static final String SELECTION_TOKEN = "--gb-selection";

    /// The custom property a placeholder's ink comes from.
    static final String PLACEHOLDER_TOKEN = "--gb-text-placeholder";

    @Override
    public String cssType() {
        return "rich-input";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public List<Widget> children() {
        var children = new ArrayList<Widget>(markSets.size());
        for (var classes : markSets) {
            children.add(new MarkNode(classes));
        }
        if (spelling) {
            children.add(new SpellingError());
        }
        return children;
    }

    @Override
    public boolean isFocusable() {
        return focusable;
    }

    @Override
    public boolean wantsTextInput() {
        return editable;
    }

    @Override
    public void onPointer(PointerEvent event) {
        handler.pointer(event);
    }

    @Override
    public void onKey(KeyEvent event) {
        handler.key(event);
    }

    @Override
    public void onText(TextEvent event) {
        handler.text(event);
    }

    @Override
    public void onPreedit(PreeditEvent event) {
        handler.preedit(event);
    }

    @Override
    public void onFocusChanged(boolean focused, boolean fromKeyboard) {
        handler.focus(focused);
    }

    @Override
    public Optional<LogicalRect> caretArea() {
        return handler.caretArea();
    }

    @Override
    public double caretOffsetIn(LogicalRect area) {
        return handler.caretOffset();
    }

    @Override
    public void measured(Extent bounds, Extent part) {
        handler.measured(bounds);
    }

    @Override
    public void located(LogicalRect self, LogicalRect clip) {
        handler.located(self);
    }

    @Override
    public boolean openContextMenu(LogicalRect anchor) {
        return handler.openContextMenu(anchor);
    }

    @Override
    public Role role() {
        return Role.TEXT_FIELD;
    }

    @Override
    public String accessibleName() {
        return label;
    }

    /// The carriers read into styles, then the box the state lays out.
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var font = context.font(style);
        var looks = new HashMap<Set<String>, MarkStyle>();
        for (var i = 0; i < markSets.size() && i < children.size(); i++) {
            var carried = children.get(i);
            var text = carried.text();
            if (text == null) {
                continue;
            }
            var fill = carried.fill() instanceof Background.Colour(var colour) ? colour : 0;
            var ink = text.argb() == style.color() ? MarkStyle.TEXT_COLOUR : text.argb();
            looks.put(
                    markSets.get(i),
                    new MarkStyle(
                            text.paragraph().font(),
                            ink,
                            text.flow().decorations(),
                            fill,
                            text.flow().decorationPaint()));
        }
        var colours = new Colours(
                style.color(),
                context.color(PLACEHOLDER_TOKEN, style.color()),
                context.color(SELECTION_TOKEN, 0x553B82F6));
        var hint = placeholder.isEmpty() ? null : context.paragraph(style, placeholder);
        var misspelled = spelling && children.size() > markSets.size()
                ? SpellingInk.of(children.get(markSets.size()), style.color())
                : null;
        return handler.laidOut(font, Map.copyOf(looks), colours, hint, style, misspelled)
                .cursor(focusable ? Cursor.TEXT : Cursor.DEFAULT);
    }

    /// The height of a field `lines` rows tall in `font`, padding and border
    /// included.
    static Length heightOf(int lines, Font font, Edges edges) {
        return Length.points((float) (lines * font.lineHeight() + edges.vertical()));
    }
}
