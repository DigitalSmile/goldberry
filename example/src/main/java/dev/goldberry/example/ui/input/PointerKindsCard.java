package dev.goldberry.example.ui.input;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.event.PointerEvent.Kind;
import dev.goldberry.paint.CanvasStyle;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Path;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.text.Paragraph;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.canvas.Canvas;
import dev.goldberry.widgets.core.canvas.Input;
import dev.goldberry.widgets.text.Text;

/// A pad that counts the pointer events it is sent, kind by kind.
///
/// Read more: [Kinds](https://goldberry.dev/docs/guide/input.html#kinds).
public record PointerKindsCard() implements Widget.Stateful {

    /// The card's id.
    public static final String ID = "input-kinds";

    @Override
    public State<?> createState() {
        return new KindsState();
    }

    static final class KindsState extends State<PointerKindsCard> {

        private final Map<Kind, Integer> counts = new EnumMap<>(Kind.class);

        private @Nullable Kind last;

        @Override
        public Widget build(BuildContext context) {
            var tallies = Arrays.stream(Kind.values())
                    .<Widget>map(kind -> new Text(
                            kind + " " + counts.getOrDefault(kind, 0),
                            Attributes.NONE
                                    .id("kind-" + kind.name().toLowerCase(Locale.ROOT))
                                    .classes(
                                            kind == last
                                                    ? new String[] {"kind-tally", "kind-last"}
                                                    : new String[] {"kind-tally"})))
                    .toList();
            return new ShowcaseCard(
                            ID,
                            "Kinds of pointer event",
                            "Moved, pressed, released, entered, exited, clicked and wheel. A click is a press and its"
                                    + " release on the same node: press here, drag off and let go, and no click"
                                    + " arrives.",
                            DocLink.to("guide/input", "kinds"))
                    .of(
                            new Canvas(this::paint, new Input() {
                                        @Override
                                        public void onPointer(PointerEvent event) {
                                            var kind = event.kind();
                                            setState(() -> {
                                                counts.merge(kind, 1, Integer::sum);
                                                last = kind;
                                            });
                                        }

                                        @Override
                                        public boolean focusable() {
                                            return false;
                                        }

                                        @Override
                                        public String accessibleName() {
                                            return "A pad that counts pointer events";
                                        }
                                    })
                                    .id("kinds-pad"),
                            new Row(tallies, Attributes.NONE.id("kind-tallies").classes("kind-tallies")));
        }

        private void paint(Frame frame, LogicalSize size, CanvasStyle style) {
            var label = last == null ? "Move, press, click and scroll here." : "Last: " + last;
            Paragraph.of(style.font(), label).paint(frame, 12, 12, size.width() - 24, style.ink());
            if (last != null) {
                frame.fillPath(Path.circle(size.width() - 18, 18, 6), InputColors.ACCENT);
            }
        }
    }
}
