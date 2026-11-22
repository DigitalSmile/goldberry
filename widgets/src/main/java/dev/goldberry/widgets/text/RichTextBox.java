package dev.goldberry.widgets.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.background.Background;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.layout.Presence.UserSelect;
import dev.goldberry.paint.Box;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.SpanPaint;
import dev.goldberry.text.flow.TextDecorations;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The node a stylesheet calls `rich-text`: the paragraph its [RichText] draws,
/// the runs as the children the cascade styles, and the selection's part.
///
/// [RichText] is stateful and styles nothing, so this carries the CSS type and
/// the application's `id` and classes, the arrangement every stateful widget in
/// this catalog uses.
///
/// @param runs       the runs, as the state wants them drawn: a revealed
///                   spoiler carries `revealed`
/// @param text       the words, for a screen reader
/// @param selFrom    where the selected stretch begins, or -1 for none
/// @param selTo      one past where it ends
/// @param hovered    the run the pointer is over, or -1
/// @param focusable  whether it takes the focus, which a selectable one does
/// @param handler    what hears its input and is told what each frame laid out
/// @param attributes the paragraph's id, classes and key
record RichTextBox(
        List<Run> runs,
        String text,
        int selFrom,
        int selTo,
        int hovered,
        boolean focusable,
        @Nullable Input handler,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Semantics, Handles {

    /// What the state hears: the pointer, the keys, the focus, and the
    /// paragraph each frame lays out.
    interface Input {

        void pointer(PointerEvent event);

        void key(KeyEvent event);

        void focus(boolean focused);

        /// The paragraph as it was rendered, its flow, where each run ends in
        /// its text, and what `user-select` said about it.
        void laidOut(Paragraph paragraph, TextFlow flow, int[] runEnds, UserSelect userSelect);

        /// Whether the words can be selected now: the widget's own flag, unless
        /// the last frame's `user-select` said otherwise.
        boolean selectable();
    }

    @Override
    public String cssType() {
        return "rich-text";
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

    /// The runs, as the nodes the cascade styles, and the selection's part
    /// after them.
    @Override
    public List<Widget> children() {
        var children = new ArrayList<Widget>(runs.size() + 1);
        children.addAll(runs);
        children.add(new RichTextSelection());
        return children;
    }

    /// Focusable when selectable, by the widget's flag or by `user-select`,
    /// because `Ctrl+C` goes to whatever has the focus.
    @Override
    public boolean isFocusable() {
        return focusable || (handler != null && handler.selectable());
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (handler != null) {
            handler.pointer(event);
        }
    }

    @Override
    public void onKey(KeyEvent event) {
        if (handler != null) {
            handler.key(event);
        }
    }

    @Override
    public void onFocusChanged(boolean focused, boolean fromKeyboard) {
        if (handler != null) {
            handler.focus(focused);
        }
    }

    @Override
    public Role role() {
        return Role.TEXT;
    }

    /// The words: a reader hears a sentence, not its styles.
    @Override
    public String accessibleName() {
        return text;
    }

    /// One measured leaf, as a `text` is.
    ///
    /// The runs have rendered already, each to a box holding its words shaped in
    /// its own font and its resolved colour, rules and background. Those boxes
    /// are read here and not drawn: their paragraphs are joined into one, and a
    /// run that looks different from the paragraph becomes a [SpanPaint] over its
    /// stretch, cut where the selection begins and ends so the selected part has
    /// the selection's background. Runs that look like the paragraph add
    /// nothing, so a `rich-text` whose runs are all unstyled draws what a `text`
    /// with the same words draws.
    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var spans = new ArrayList<Paragraph>(runs.size());
        var paints = new ArrayList<SpanPaint>();
        var decorations = style.textFlow().textDecorations();
        var runEnds = new int[runs.size()];
        var highlight = children.isEmpty() ? 0 : backgroundOf(children.getLast());
        var offset = 0;
        for (var i = 0; i < runs.size() && i < children.size(); i++) {
            var child = children.get(i);
            var run = child.text();
            if (run == null) {
                runEnds[i] = offset;
                continue;
            }
            var end = offset + run.paragraph().text().length();
            var ruled = run.flow().textDecorations();
            var behind = backgroundOf(child);
            var differs = run.argb() != style.color() || !ruled.equals(decorations) || behind != 0;
            addPieces(paints, offset, end, differs, run.argb(), style.color(), ruled, decorations, behind, highlight);
            spans.add(run.paragraph());
            runEnds[i] = end;
            offset = end;
        }
        var paragraph = spans.isEmpty() ? context.paragraph(style, "") : context.join(spans);
        if (handler != null) {
            handler.laidOut(
                    paragraph, style.textFlow(), runEnds, style.presence().userSelect());
        }
        var box = Box.of()
                .text(new Box.Text(paragraph, style.color(), style.textFlow(), paints))
                .style(style);
        if (hovered >= 0 && hovered < runs.size() && hovered < children.size()) {
            box = box.cursor(children.get(hovered).cursor());
        }
        return box;
    }

    /// The spans for one run from `from` to `to`: none when it looks like the
    /// paragraph and is not selected, and otherwise one per stretch on either
    /// side of the selection's ends.
    private void addPieces(
            List<SpanPaint> into,
            int from,
            int to,
            boolean differs,
            int argb,
            int paragraphArgb,
            TextDecorations ruled,
            TextDecorations paragraphRules,
            int behind,
            int highlight) {
        var start = selFrom >= 0 ? Math.clamp(selFrom, from, to) : to;
        var end = selFrom >= 0 ? Math.clamp(selTo, from, to) : to;
        var colour = differs ? argb : paragraphArgb;
        var rules = differs ? ruled : paragraphRules;
        if (start >= end || highlight == 0) {
            if (differs) {
                into.add(span(from, to, colour, rules, behind));
            }
            return;
        }
        if (from < start && differs) {
            into.add(span(from, start, colour, rules, behind));
        }
        into.add(span(start, end, colour, rules, highlight));
        if (end < to && differs) {
            into.add(span(end, to, colour, rules, behind));
        }
    }

    private static SpanPaint span(int from, int to, int argb, TextDecorations rules, int background) {
        return new SpanPaint(from, to, argb, rules.lines(), background, rules.paint());
    }

    /// A box's solid background colour, or 0 for none or for anything that is
    /// not one colour.
    private static int backgroundOf(Box box) {
        return box.fill() instanceof Background.Colour(var colour) ? colour : 0;
    }

    @Override
    public String toString() {
        return "RichTextBox[" + text + "]";
    }
}
