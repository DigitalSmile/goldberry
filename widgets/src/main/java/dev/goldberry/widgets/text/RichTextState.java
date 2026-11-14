package dev.goldberry.widgets.text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.TextLayout;
import dev.goldberry.text.edit.TextGeometry;
import dev.goldberry.text.edit.keys.EditCommand;
import dev.goldberry.text.edit.keys.EditKeys;
import dev.goldberry.text.edit.keys.EditSurface;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;

/// What a [RichText] remembers: which spoilers are revealed, what is selected,
/// which run the pointer is over, and the paragraph the last frame laid out.
///
/// The paragraph is what turns a pointer into a place in the text: the event
/// says where it landed in the content box, and [TextGeometry] lays the same
/// paragraph out at that box's width and answers the offset, the geometry
/// `text-area` reads. Each run's stretch of that text says which run it is.
final class RichTextState extends State<RichText> implements RichTextBox.Input {

    /// The runs revealed, by index.
    private final Set<Integer> revealed = new HashSet<>();

    /// Where a selection was started and where it has been dragged to, or -1.
    private int anchor = -1;

    private int caret = -1;

    /// Whether the primary button went down here and has not come up.
    private boolean dragging;

    /// The run under the pointer, or -1.
    private int hovered = -1;

    /// The last frame's paragraph, its flow and where each run ends in it.
    private @Nullable Paragraph paragraph;

    private TextFlow flow = TextFlow.NORMAL;

    private int[] runEnds = new int[0];

    private @Nullable Host host;

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var rich = widget();
        var runs = new ArrayList<Run>(rich.runs().size());
        var interactive = rich.selectable();
        for (var i = 0; i < rich.runs().size(); i++) {
            var run = rich.runs().get(i);
            interactive |= run.onPress() != null || run.spoiler();
            runs.add(run.spoiler() && revealed.contains(i) ? revealedOf(run) : run);
        }
        var from = Math.min(anchor, caret);
        var to = Math.max(anchor, caret);
        return new RichTextBox(
                runs,
                rich.text(),
                from < to ? from : -1,
                from < to ? to : -1,
                hovered,
                rich.selectable(),
                interactive ? this : null,
                rich.attributes());
    }

    /// `run`, with the class that uncovers it.
    private static Run revealedOf(Run run) {
        var classes = new LinkedHashSet<>(run.attributes().classes());
        classes.add(Run.REVEALED);
        return run.withAttributes(run.attributes().classes(classes.toArray(String[]::new)));
    }

    @Override
    public void laidOut(Paragraph shaped, TextFlow shapedFlow, int[] ends) {
        paragraph = shaped;
        flow = shapedFlow;
        runEnds = ends;
    }

    @Override
    public void pointer(PointerEvent event) {
        switch (event.kind()) {
            case MOVED -> moved(event);
            case EXITED -> hover(-1);
            case PRESSED -> pressed(event);
            case RELEASED -> dragging = false;
            case CLICKED -> clicked(event);
            default -> {}
        }
    }

    private void moved(PointerEvent event) {
        if (dragging) {
            var offset = offsetAt(event);
            if (offset >= 0 && offset != caret) {
                setState(() -> caret = offset);
            }
            event.consume();
            return;
        }
        hover(runAt(event));
    }

    private void hover(int run) {
        if (run != hovered) {
            setState(() -> hovered = run);
        }
    }

    private void pressed(PointerEvent event) {
        if (!widget().selectable() || event.button() != PointerEvent.Button.PRIMARY) {
            return;
        }
        var offset = offsetAt(event);
        if (offset < 0) {
            return;
        }
        dragging = true;
        setState(() -> {
            anchor = offset;
            caret = offset;
        });
    }

    /// A press and a release on the same spot: a spoiler is revealed, and a
    /// pressable run is pressed. Not after a drag that selected something,
    /// which was a selection and not a press.
    private void clicked(PointerEvent event) {
        if (event.button() != PointerEvent.Button.PRIMARY || anchor != caret) {
            return;
        }
        var index = runAt(event);
        if (index < 0) {
            return;
        }
        var run = widget().runs().get(index);
        if (run.spoiler() && !revealed.contains(index)) {
            setState(() -> revealed.add(index));
            event.consume();
            return;
        }
        if (run.onPress() != null) {
            run.onPress().run();
            event.consume();
        }
    }

    @Override
    public void key(KeyEvent event) {
        if (!widget().selectable()) {
            return;
        }
        if (!(EditKeys.of(event, EditSurface.WRAPPED) instanceof EditCommand.Simple command)) {
            return;
        }
        switch (command) {
            case SELECT_ALL -> {
                var length = widget().text().length();
                var shaped = paragraph;
                var end = shaped == null ? length : shaped.text().length();
                setState(() -> {
                    anchor = 0;
                    caret = end;
                });
                event.consume();
            }
            case COPY -> {
                var selected = selectedText();
                if (!selected.isEmpty() && host != null) {
                    host.clipboard().text(selected);
                    event.consume();
                }
            }
            default -> {}
        }
    }

    @Override
    public void focus(boolean focused) {
        if (!focused && anchor != caret) {
            setState(() -> {
                anchor = -1;
                caret = -1;
            });
        }
    }

    /// What is selected, or empty.
    String selectedText() {
        var shaped = paragraph;
        if (shaped == null || anchor == caret || anchor < 0) {
            return "";
        }
        var text = shaped.text();
        var from = Math.clamp(Math.min(anchor, caret), 0, text.length());
        var to = Math.clamp(Math.max(anchor, caret), 0, text.length());
        return text.substring(from, to);
    }

    /// The offset the pointer is at in the last frame's paragraph, or -1 before
    /// there is one.
    private int offsetAt(PointerEvent event) {
        var shaped = paragraph;
        if (shaped == null) {
            return -1;
        }
        var content = event.content();
        return TextGeometry.offsetAt(
                shaped, layoutOf(shaped, content.width()), content.x(), content.y(), content.width(), flow.textAlign());
    }

    /// The run whose words are under the pointer, or -1 for the space beside
    /// or below them.
    ///
    /// The character under the pointer rather than the caret position nearest
    /// it: the caret lands after a character whose right half was pressed, and
    /// the last character of a mention is still the mention.
    private int runAt(PointerEvent event) {
        var shaped = paragraph;
        if (shaped == null || runEnds.length == 0) {
            return -1;
        }
        var content = event.content();
        var width = content.width();
        var layout = layoutOf(shaped, width);
        var align = flow.textAlign();
        var offset = TextGeometry.offsetAt(shaped, layout, content.x(), content.y(), width, align);
        var length = shaped.text().length();
        var at = TextGeometry.caretAt(shaped, layout, offset, width, align);
        var index = at.x() > content.x() && offset > 0 ? offset - 1 : offset;
        if (index < 0 || index >= length) {
            return -1;
        }
        var left = TextGeometry.caretAt(shaped, layout, index, width, align);
        var right = TextGeometry.caretAt(shaped, layout, index + 1, width, align);
        var near = Math.min(left.x(), right.x());
        var far = Math.max(left.x(), right.x());
        if (left.line() != right.line()) {
            far = Math.max(far, near + 1);
        }
        if (content.x() < near
                || content.x() > far
                || content.y() < left.top()
                || content.y() > left.top() + left.height()) {
            return -1;
        }
        for (var i = 0; i < runEnds.length; i++) {
            if (index < runEnds[i]) {
                return i;
            }
        }
        return -1;
    }

    private TextLayout layoutOf(Paragraph shaped, double width) {
        return shaped.layout(flow.wraps() ? width : Paragraph.UNCONSTRAINED, flow);
    }

    /// For a test: the paragraph the last frame laid out, or null before one.
    @Nullable
    Paragraph paragraph() {
        return paragraph;
    }

    /// For a test: the selected stretch, or `[-1, -1]`.
    List<Integer> selection() {
        return anchor == caret ? List.of(-1, -1) : List.of(Math.min(anchor, caret), Math.max(anchor, caret));
    }
}
