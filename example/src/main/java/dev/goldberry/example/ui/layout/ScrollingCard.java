package dev.goldberry.example.ui.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.SectionHeader;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.affix.Affix;
import dev.goldberry.widgets.core.affix.Edge;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;
import dev.goldberry.widgets.core.scroll.ScrollController;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// A viewport whose section headers stick: `scroll`, `affix`, and the reveal
/// that puts a section back on screen.
///
/// Jumping is a **request**, not a scroll: pressing a button records which section
/// is wanted, the affix for that section is built with a `revealedBy` callback,
/// and the callback hands the controller the two rectangles only a laid-out frame
/// knows. Nothing here computes an offset.
///
/// Read more: [Revealing one](https://goldberry.dev/docs/layout/affix.html#revealing-one).
///
/// @param frame what the jump bar and the list are put in: the gallery's card,
///              unless a test wants them in another
public record ScrollingCard(Function<List<Widget>, Widget> frame) implements Widget.Stateful {

    /// How many rows each section has under its header.
    public static final int ROWS_PER_SECTION = 12;

    /// The chapters the list is divided into. Four, because the interesting case
    /// is a header lifting while the *next* one pushes it off, and that needs at
    /// least three to be seen happening twice.
    ///
    /// One word each: a section's name becomes its `#section-<name>` and its
    /// button's `#jump-<name>`, lower-cased and nothing else.
    public static final List<String> SECTIONS = List.of("Hobbiton", "Bree", "Rivendell", "Moria");

    /// The card the list is shown in on the Scrolling screen.
    static final ShowcaseCard CARD = new ShowcaseCard(
            "scroll-card",
            "Sticky headers",
            "Each header is an affix: it lifts and stays at the top while its section passes under it,"
                    + " then the next one pushes it off. The buttons bring a section into view, moving the least"
                    + " they can.",
            DocLink.to("layout/affix", "affix"));

    /// One function for every build, so a rebuild of the screen hands this card an
    /// equal widget rather than a new lambda each time.
    private static final Function<List<Widget>, Widget> IN_A_CARD = CARD::of;

    /// The card as the Scrolling screen shows it.
    public ScrollingCard() {
        this(IN_A_CARD);
    }

    @Override
    public State<?> createState() {
        return new ScrollingState();
    }

    private static final class ScrollingState extends State<ScrollingCard> {

        private final ScrollController list = new ScrollController();

        /// Which section has been asked for, until the frame that reveals it.
        /// Null the rest of the time, which is what keeps the callback from
        /// firing on every frame after the first jump.
        private @Nullable String wanted;

        @Override
        public Widget build(BuildContext context) {
            var rows = new ArrayList<Widget>();
            for (var section : SECTIONS) {
                var affix = new Affix(
                        List.of(new SectionHeader(section)),
                        Edge.TOP,
                        0,
                        Attributes.NONE
                                .id("section-" + section.toLowerCase(Locale.ROOT))
                                .classes("section"));
                rows.add(section.equals(wanted) ? affix.revealedBy(this::revealed) : affix);
                for (var i = 1; i <= ROWS_PER_SECTION; i++) {
                    rows.add(new Text(section + " — line " + i, Attributes.NONE.classes("scroll-row")));
                }
            }

            var jumps = new ArrayList<Widget>();
            jumps.add(new Text("Jump to", Attributes.NONE.classes("jump-label")));
            for (var section : SECTIONS) {
                jumps.add(new Button(section, () -> ask(section))
                        .withAttributes(Attributes.NONE.id("jump-" + section.toLowerCase(Locale.ROOT))));
            }

            return widget().frame()
                    .apply(List.of(
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
