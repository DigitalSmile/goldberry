package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.core.Spacer;
import io.github.digitalsmile.goldberry.widgets.core.affix.Affix;
import io.github.digitalsmile.goldberry.widgets.core.affix.Edge;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController;
import io.github.digitalsmile.goldberry.widgets.panel.Panel;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.ArrayList;
import java.util.List;

/// The sixth gallery screen: `scroll`, `affix` and `tour`, which are the three
/// widgets the scroll work produced and the three that are hardest to see in a
/// still picture.
///
/// It is the one screen that is **not** wrapped in the gallery's own viewport.
/// Every other screen is, because a screen taller than the window should scroll
/// ([ADR-0110](../../../../../../../book/src/adr/0110-the-showcase-is-a-gallery-of-screens.md));
/// this one owns a viewport of its own with sticky headers in it, and nesting
/// two same-axis scrollers is banned in the canon (`docs/design-system.md` §2.4)
/// — so the screen that demonstrates the rule is the one place the gallery has
/// to honour it.
///
/// ## What each part shows
///
/// - **`scroll`** — the list itself: a wheel, a keyboard, a thumb that fades, and
///   an edge it stops at.
/// - **`affix`** — the four section headers, which lift and stick as their
///   sections pass. `:affixed` gives each a surface the moment it lifts, so the
///   rows travelling underneath are not read through it.
/// - **`scrollIntoView`** — the buttons above the list, which ask the controller
///   to bring a section back. One is deliberately near the end, so pressing it
///   from the top is a scroll of most of the document.
/// - **`tour`** — the button that starts one, which then points at the three
///   above in turn.
///
/// @param startTour what to run when the tour button is pressed — the application's,
///                  because starting a tour needs a `Host` and a widget has none
public record Scrolling(Runnable startTour) implements Widget.Stateful {

    /// How many rows each section has. Enough that a section is taller than the
    /// viewport at any reasonable window size, which is what makes a sticky
    /// header stick for long enough to be seen doing it.
    public static final int ROWS_PER_SECTION = 12;

    public static final List<String> SECTIONS =
            List.of("Beginnings", "Middles", "Complications", "Endings");

    @Override
    public State<?> createState() {
        return new ScrollingState();
    }

    /// Holds the controller, which has to outlive the builds that use it.
    private static final class ScrollingState extends State<Scrolling> {

        /// The handle the "jump to" buttons scroll with. A field and not a local:
        /// a controller made in `build` would have a new identity every frame and
        /// would be attached to a viewport that had already let go of the last one
        /// ([ADR-0120](../../../../../../../book/src/adr/0120-a-widget-scrolls-itself-into-view.md)).
        private final ScrollController list = new ScrollController();

        /// Which section the buttons last asked for, so exactly one `affix` is
        /// asked where it is. Cleared as soon as it has been acted on, because a
        /// standing request would take the scrollbar away from the user.
        private String wanted;

        @Override
        public Widget build(BuildContext context) {
            var rows = new ArrayList<Widget>();
            for (var section : Scrolling.SECTIONS) {
                rows.add(new Affix(
                        List.of(new SectionHeader(section, list, section.equals(wanted),
                                this::revealed)),
                        Edge.TOP, 0,
                        Attributes.NONE.id("section-" + section.toLowerCase())
                                .classes("section")));
                for (var i = 1; i <= Scrolling.ROWS_PER_SECTION; i++) {
                    rows.add(new Text(section + " — line " + i,
                            Attributes.NONE.classes("scroll-row")));
                }
            }

            var jumps = new ArrayList<Widget>();
            jumps.add(new Text("Jump to", Attributes.NONE.classes("caption")));
            for (var section : Scrolling.SECTIONS) {
                jumps.add(new Button(section, () -> ask(section))
                        .withAttributes(Attributes.NONE.id("jump-" + section.toLowerCase())));
            }
            jumps.add(new Spacer());
            jumps.add(new Button("Take the tour", widget().startTour())
                    .withAttributes(Attributes.NONE.id("tour-button")
                            .classes("primary")));

            return new Column(List.of(
                    new Text("Scrolling, sticking and touring",
                            Attributes.NONE.classes("screen-title")),
                    new Text("The list below owns its own viewport. Its four headers are"
                            + " `affix`, so each one lifts and stays put while its section"
                            + " passes underneath.",
                            Attributes.NONE.classes("prose")),
                    new Row(jumps.toArray(Widget[]::new))
                            .withAttributes(Attributes.NONE.id("jump-bar")
                                    .classes("toolbar")),
                    new Panel(List.of(
                            new Scroll(List.of(new Column(rows.toArray(Widget[]::new))),
                                    ScrollAxis.VERTICAL, Attributes.NONE)
                                    .controlledBy(list)),
                            Attributes.NONE.id("scroll-demo").classes("scroll-demo"))),
                    Attributes.NONE.id("screen-scrolling"));
        }

        private void ask(String section) {
            setState(() -> wanted = section);
        }

        private void revealed() {
            setState(() -> wanted = null);
        }
    }
}
