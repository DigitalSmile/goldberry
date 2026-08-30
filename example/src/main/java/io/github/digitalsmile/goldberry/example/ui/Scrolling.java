package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.affix.Affix;
import io.github.digitalsmile.goldberry.widgets.core.affix.Edge;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController;
import io.github.digitalsmile.goldberry.widgets.panel.Panel;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// A viewport of its own, with headers that stick — §2.4's `scroll`, §5's `affix`
/// and the `reveal` that puts a section back on screen.
///
/// **A card and not a screen**, which is what changed when the gallery went to
/// seven tabs: this used to be the one screen the gallery did not wrap in a
/// viewport, because §2.4 bans nested same-axis scrollers and a screen inside the
/// gallery's `scroll` that owned another one would be exactly that. As a card in
/// a masonry it keeps its own fixed height and the ban keeps holding — the
/// Navigation screen is not scrolled either, and the wall is what makes that
/// possible: two columns of cards are half as tall as one
/// ([ADR-0222](../../../../../../../book/src/adr/0222-a-showcase-is-a-window-a-bar-and-seven-screens.md)).
///
/// Jumping is a **request**, not a scroll: pressing a button records which section
/// is wanted, the affix for that section is built with a `revealedBy` callback,
/// and the callback hands the controller the two rectangles only a laid-out frame
/// knows. Nothing here computes an offset ([ADR-0116]).
public record Scrolling() implements Widget.Stateful {

    public static final int ROWS_PER_SECTION = 12;

    /// The chapters the list is divided into. Four, because the interesting case
    /// is a header lifting while the *next* one pushes it off, and that needs at
    /// least three to be seen happening twice.
    /// One word each, and that is a constraint rather than a coincidence: a
    /// section's name becomes its `#section-<name>` and its button's
    /// `#jump-<name>`, and `#jump-bag end` is not a selector. Lower-casing is the
    /// whole of the transformation, which is what keeps the ids something a
    /// stylesheet and a test can both write down without asking this class how.
    public static final List<String> SECTIONS = List.of("Hobbiton", "Bree", "Rivendell", "Moria");

    @Override
    public State<?> createState() {
        return new ScrollingState();
    }

    private static final class ScrollingState extends State<Scrolling> {

        private final ScrollController list = new ScrollController();

        /// Which section has been asked for, until the frame that reveals it.
        /// Null the rest of the time, which is what keeps the callback from
        /// firing on every frame after the first jump.
        private String wanted;

        @Override
        public Widget build(BuildContext context) {
            var rows = new ArrayList<Widget>();
            for (var section : Scrolling.SECTIONS) {
                var affix = new Affix(
                        List.of(new SectionHeader(section)),
                        Edge.TOP,
                        0,
                        Attributes.NONE
                                .id("section-" + section.toLowerCase(Locale.ROOT))
                                .classes("section"));
                rows.add(section.equals(wanted) ? affix.revealedBy(this::revealed) : affix);
                for (var i = 1; i <= Scrolling.ROWS_PER_SECTION; i++) {
                    rows.add(new Text(section + " — line " + i, Attributes.NONE.classes("scroll-row")));
                }
            }

            var jumps = new ArrayList<Widget>();
            jumps.add(new Text("Jump to", Attributes.NONE.classes("jump-label")));
            for (var section : Scrolling.SECTIONS) {
                jumps.add(new Button(section, () -> ask(section))
                        .withAttributes(Attributes.NONE.id("jump-" + section.toLowerCase(Locale.ROOT))));
            }

            return Notifications.card(
                    "scroll-card",
                    "A viewport of its own",
                    List.of(
                            new Text(
                                    "Its four headers are `affix`, so each one lifts and stays put"
                                            + " while its section passes underneath. The buttons ask the list"
                                            + " to bring a section into view, and it moves the least it can.",
                                    Attributes.NONE.classes("caption")),
                            new Row(jumps.toArray(Widget[]::new))
                                    .withAttributes(
                                            Attributes.NONE.id("jump-bar").classes("toolbar")),
                            new Panel(
                                    List.of(new Scroll(
                                                    List.of(new Column(rows.toArray(Widget[]::new))),
                                                    ScrollAxis.VERTICAL,
                                                    Attributes.NONE)
                                            .controlledBy(list)),
                                    Attributes.NONE.id("scroll-demo").classes("scroll-demo"))));
        }

        private void ask(String section) {
            setState(() -> wanted = section);
        }

        private void revealed(LogicalRect self, LogicalRect clip) {
            list.reveal(self, clip);
            setState(() -> wanted = null);
        }
    }
}
