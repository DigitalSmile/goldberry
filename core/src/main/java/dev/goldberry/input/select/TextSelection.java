package dev.goldberry.input.select;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.layout.Presence.UserSelect;
import dev.goldberry.paint.Box;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.SpanPaint;
import dev.goldberry.text.TextLayout;
import dev.goldberry.text.edit.TextGeometry;
import dev.goldberry.text.flow.TextFlow;

/// The one stretch of selected words in a tree, and the boxes that could hold it.
///
/// ## Two halves, one frame apart
///
/// The **renderer** tells it, box by box, which text boxes this frame drew with
/// `user-select: text` or `all` ([#rendered]), and is handed back the box with
/// the selection's highlight in it. The **router** asks it, on a press nothing
/// took, whether the press landed on one of them ([#selectable]), and drives the
/// gesture from there. What the router reads is the last finished frame, which
/// is what is on the screen.
///
/// The holder is an element rather than its widget, so a rebuild that hands the
/// same element a new `text` keeps the selection; a selection whose holder was
/// not drawn by the last frame, because it went away or stopped being
/// selectable, is dropped.
///
/// @param <E> the element type: the tree's own in the toolkit, anything with
///            identity in a test
public final class TextSelection<E> {

    /// What one selectable box drew: the words, how they flowed, and whether a
    /// press selects at a point or selects the lot.
    record Drawn(Paragraph paragraph, TextFlow flow, UserSelect mode) {}

    /// The boxes the last finished frame drew selectable, by element identity.
    private IdentityHashMap<E, Drawn> drawn = new IdentityHashMap<>();

    /// The boxes the frame being rendered has drawn so far.
    private IdentityHashMap<E, Drawn> drawing = new IdentityHashMap<>();

    private @Nullable E holder;

    private int anchor;

    private int caret;

    private boolean dragging;

    /// An empty selection over no boxes: what each tree starts with, and what a
    /// test drives by hand.
    public TextSelection() {}

    // --- the renderer's half ---------------------------------------------------

    /// A frame's render walk is starting.
    public void beginFrame() {
        drawing = new IdentityHashMap<>();
    }

    /// The walk is over: what it drew is what presses are tested against from
    /// now on, and a holder it did not draw selectable loses the selection.
    public void endFrame() {
        drawn = drawing;
        if (holder != null && !drawn.containsKey(holder)) {
            clear();
        }
    }

    /// `text`, which `element` just drew under `mode`, with the selection's
    /// highlight in it when `element` holds the selection.
    ///
    /// A mode that does not select ([#selects]) registers nothing and hands
    /// `text` straight back.
    ///
    /// @param highlight the selection's background, `0xAARRGGBB`
    public Box.Text rendered(E element, Box.Text text, UserSelect mode, int highlight) {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(text, "text");
        if (!selects(mode)) {
            return text;
        }
        drawing.put(element, new Drawn(text.paragraph(), text.flow(), mode));
        if (element != holder || anchor == caret || highlight == 0) {
            return text;
        }
        var length = text.paragraph().text().length();
        var from = Math.clamp(Math.min(anchor, caret), 0, length);
        var to = Math.clamp(Math.max(anchor, caret), 0, length);
        if (from >= to) {
            return text;
        }
        var spans = new ArrayList<SpanPaint>(text.spans().size() + 1);
        spans.addAll(text.spans());
        // Last, so it is painted over whatever background a span had: the
        // selection is what a reader is looking at.
        spans.add(new SpanPaint(from, to, text.argb(), text.flow().decorations(), highlight));
        return new Box.Text(text.paragraph(), text.argb(), text.flow(), spans, text.shadows());
    }

    /// Whether a box under `mode` can be selected: `text` and `all` can, and
    /// `none` cannot. `auto` is the widget's own default, and a plain text box's
    /// is not to be: a label on a button or a heading over a card is pressed,
    /// not selected, which is what a desktop application expects.
    public static boolean selects(UserSelect mode) {
        return mode == UserSelect.TEXT || mode == UserSelect.ALL;
    }

    // --- the router's half -----------------------------------------------------

    /// Whether the last frame drew `element` as a selectable text box.
    public boolean selectable(E element) {
        return drawn.containsKey(element);
    }

    /// A press on `element`, at `(x, y)` from the corner of its content box,
    /// which is `width` wide.
    ///
    /// One click puts the anchor down and starts a drag, two select the word,
    /// three select everything; under `user-select: all` any click selects
    /// everything.
    ///
    /// @return whether the press started or changed a selection, which is
    ///         whether it was this class's to handle
    public boolean press(E element, double x, double y, double width, int clickCount) {
        var box = drawn.get(element);
        if (box == null) {
            return false;
        }
        holder = element;
        var length = box.paragraph().text().length();
        if (box.mode() == UserSelect.ALL || clickCount >= 3) {
            anchor = 0;
            caret = length;
            dragging = false;
            return true;
        }
        var at = offsetAt(box, x, y, width);
        if (clickCount == 2) {
            var word = wordAround(box.paragraph().text(), at);
            anchor = word[0];
            caret = word[1];
            dragging = false;
            return true;
        }
        anchor = at;
        caret = at;
        dragging = true;
        return true;
    }

    /// The pointer moved with the button held: the caret follows it, measured
    /// against the holder's content box wherever the pointer is.
    ///
    /// @return whether the selection changed
    public boolean drag(double x, double y, double width) {
        if (!dragging || holder == null) {
            return false;
        }
        var box = drawn.get(holder);
        if (box == null) {
            return false;
        }
        var at = offsetAt(box, x, y, width);
        if (at == caret) {
            return false;
        }
        caret = at;
        return true;
    }

    /// The button came up.
    ///
    /// @return whether a drag was in progress, which is when the selection
    ///         has just been finished
    public boolean release() {
        var was = dragging;
        dragging = false;
        return was;
    }

    /// Whether a press here is still held.
    public boolean isDragging() {
        return dragging;
    }

    /// The element holding the selection, or null when there is none.
    public @Nullable E holder() {
        return holder;
    }

    /// Whether nothing is selected.
    public boolean isEmpty() {
        return holder == null || anchor == caret;
    }

    /// The selected words, or `""`.
    public String selectedText() {
        if (isEmpty()) {
            return "";
        }
        var box = drawn.get(holder);
        if (box == null) {
            return "";
        }
        var text = box.paragraph().text();
        var from = Math.clamp(Math.min(anchor, caret), 0, text.length());
        var to = Math.clamp(Math.max(anchor, caret), 0, text.length());
        return text.substring(from, to);
    }

    /// Where the selection starts and ends, in the holder's text: `[-1, -1]`
    /// for none.
    public int[] range() {
        return isEmpty() ? new int[] {-1, -1} : new int[] {Math.min(anchor, caret), Math.max(anchor, caret)};
    }

    /// Drops the selection.
    ///
    /// @return whether there was one, which is whether a repaint is owed
    public boolean clear() {
        var had = !isEmpty();
        holder = null;
        anchor = 0;
        caret = 0;
        dragging = false;
        return had;
    }

    private static int offsetAt(Drawn box, double x, double y, double width) {
        var paragraph = box.paragraph();
        var flow = box.flow();
        TextLayout layout = paragraph.layout(flow.wraps() ? width : Paragraph.UNCONSTRAINED, flow);
        return TextGeometry.offsetAt(paragraph, layout, x, y, width, flow.textAlign());
    }

    /// The word `offset` is in or just after, as `[start, end]`; an empty
    /// stretch at `offset` between words.
    static int[] wordAround(String text, int offset) {
        if (text.isEmpty()) {
            return new int[] {0, 0};
        }
        var at = Math.clamp(offset, 0, text.length());
        var words = BreakIterator.getWordInstance();
        words.setText(text);
        var end = words.following(Math.min(at, text.length() - 1));
        if (end == BreakIterator.DONE) {
            end = text.length();
        }
        var start = words.previous();
        if (start == BreakIterator.DONE) {
            start = 0;
        }
        return new int[] {start, end};
    }
}
