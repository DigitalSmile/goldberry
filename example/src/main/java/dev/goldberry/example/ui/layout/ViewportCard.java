package dev.goldberry.example.ui.layout;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;
import dev.goldberry.widgets.core.scroll.ScrollController;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// A plain `scroll` on both axes, moved from Java through a [ScrollController],
/// with a line that says where it is.
///
/// The controller is the state's, created once, because whoever scrolls a
/// viewport is somewhere else and a controller made in `build` would be a new one
/// every frame. The line under the grid listens to it and rebuilds only when the
/// position actually moved.
///
/// Read more: [Scrolling from Java](https://goldberry.dev/docs/layout/scroll.html#scrolling-from-java).
///
/// @param actions where the scroll-bar switch goes, which is the window's choice
///                and not this card's
public record ViewportCard(ShowcaseModel.Actions actions) implements Widget.Stateful {

    /// The grid's rows, one letter each.
    static final List<String> ROWS = List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L");

    /// The grid's columns.
    static final int COLUMNS = 8;

    /// How far one press of Down or Right moves it, in logical pixels.
    static final int STEP = 120;

    /// The card the viewport is shown in.
    static final ShowcaseCard CARD = new ShowcaseCard(
            "scrolling-viewport",
            "A viewport",
            "A scroll clips what it holds and moves it by an offset that survives rebuilds. Wheel over the grid,"
                    + " drag a thumb, or Tab to it and use the arrows, Page Up, Page Down, Home and End.",
            DocLink.to("layout/scroll", "scroll"));

    @Override
    public State<?> createState() {
        return new ViewportState();
    }

    private static final class ViewportState extends State<ViewportCard> {

        private final ScrollController grid = new ScrollController();

        /// Where the viewport was when it last said, for the line under it.
        private ScrollController.Position position = ScrollController.Position.NONE;

        @Override
        protected void initState() {
            grid.onChange(() -> {
                var next = grid.position();
                if (isMounted() && !next.equals(position)) {
                    setState(() -> position = next);
                }
            });
        }

        @Override
        protected void dispose() {
            grid.onChange(null);
        }

        @Override
        public Widget build(BuildContext context) {
            var rows = new ArrayList<Widget>(ROWS.size());
            for (var letter : ROWS) {
                var cells = new ArrayList<Widget>(COLUMNS);
                for (var number = 1; number <= COLUMNS; number++) {
                    cells.add(new Panel(List.of(new Text(letter + number)), Attributes.NONE.classes("viewport-cell")));
                }
                rows.add(new Row(cells, Attributes.NONE.classes("viewport-row")));
            }
            return CARD.of(
                    new Row(
                                    new Button("Down", () -> grid.scrollBy(0, STEP))
                                            .withAttributes(Attributes.NONE.id("viewport-down")),
                                    new Button("Right", () -> grid.scrollBy(STEP, 0))
                                            .withAttributes(Attributes.NONE.id("viewport-right")),
                                    new Button("Back to A1", this::home)
                                            .withAttributes(Attributes.NONE.id("viewport-home")),
                                    new Button(
                                                    "Switch the bars",
                                                    () -> widget().actions().toggleScrollbars())
                                            .withAttributes(Attributes.NONE.id("viewport-bars")))
                            .withAttributes(Attributes.NONE.id("viewport-bar").classes("toolbar")),
                    new Panel(
                            List.of(new Scroll(
                                            List.of(new Column(rows, Attributes.NONE.classes("viewport-grid"))),
                                            ScrollAxis.BOTH,
                                            Attributes.NONE)
                                    .controlledBy(grid)),
                            Attributes.NONE.id("viewport-demo").classes("scroll-demo")),
                    new Text(
                            where(position),
                            Attributes.NONE.id("viewport-offset").classes("viewport-offset")));
        }

        /// Back to the top left corner, by exactly the distance the viewport is
        /// from it.
        private void home() {
            var now = grid.position();
            grid.scrollBy(-now.offsetX(), -now.offsetY());
        }
    }

    /// What the line under the grid says for `position`.
    static String where(ScrollController.Position position) {
        return "Scrolled %d across and %d down, of %d by %d"
                .formatted(
                        Math.round(position.offsetX()),
                        Math.round(position.offsetY()),
                        Math.round(position.overflowX()),
                        Math.round(position.overflowY()));
    }
}
