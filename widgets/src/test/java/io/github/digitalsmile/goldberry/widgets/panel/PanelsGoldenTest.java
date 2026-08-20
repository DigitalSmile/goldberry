package io.github.digitalsmile.goldberry.widgets.panel;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.layout.BoxPainter;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.panel.card.Card;
import io.github.digitalsmile.goldberry.widgets.panel.collapse.Collapse;
import io.github.digitalsmile.goldberry.widgets.panel.groupbox.GroupBox;
import io.github.digitalsmile.goldberry.widgets.panel.skeleton.Skeleton;
import io.github.digitalsmile.goldberry.widgets.panel.statistic.Statistic;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What §5's containers look like ([ADR-0164]).
///
/// The behaviour is in each widget's own package; these are the images, and each
/// of the five is here for something an assertion cannot reach:
///
///   - a **card** has to read as raised with no shadow to raise it with, which is
///     the whole question ADR-0164 answers;
///   - a **group box**'s title has to look attached to the frame under it rather
///     than like a stray heading;
///   - a **statistic**'s three ranks have to be three ranks;
///   - a **skeleton** has to look like the text it stands in for, which is the
///     one thing "sized from a typography token" is worth anything for;
///   - a **collapse** has to point its chevron along when shut and down when
///     open, which is one rotation and easy to get backwards.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class PanelsGoldenTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static Attributes id(String value) {
        return new Attributes(value, Set.of(), value);
    }

    private void paint(String name, Theme theme, int width, int height, Widget subject) {
        paint(name, theme, width, height, subject, null);
    }

    /// `hover` names an element to put `:hover` on, by walking to it — set by
    /// hand for `ButtonGoldenTest`'s reason: an image is about what a state looks
    /// like, and the router's tests are about whether input reaches it.
    private void paint(String name, Theme theme, int width, int height, Widget subject,
            java.util.function.Function<Element, Element> hover) {

        var scene = new Column(List.of(subject), id("scene"));
        var tree = new ElementTree(scene);
        if (hover != null) {
            hover.apply(tree.root()).setPseudoClass(Selector.PseudoClass.HOVER, true);
        }
        var renderer = new WidgetRenderer(
                List.of(
                        Controls.baseStylesheet(),
                        theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, """
                                #scene { padding: 12px; background: var(--gb-bg); gap: 12px }
                                statistic { flex-grow: 1 }
                                card.grow { flex-grow: 1 }
                                #avatar > column { flex-grow: 1 }
                                #cards, #stats, #avatar, #placeholder { gap: 12px }
                                """)),
                TestFont.get())
                // **A virtual clock, not the wall one.** A `skeleton` pulses from
                // the frame clock, so a golden taken against `Clock.system()`
                // draws a different opacity every run and can never match --
                // which is exactly how these two failed the first time. Pinned at
                // the brightest point of the pulse, which is the placeholder a
                // reviewer should be looking at. `ProgressGoldenTest` does the
                // same for the same reason.
                .clock(Clock.virtual().set(PULSE_PEAK));

        GoldenImage.assertMatches(name, width, height, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    /// The fold in `Skeleton`'s 1000ms pulse — half way, where it is brightest.
    ///
    /// Written here rather than read off the widget so that a change to the
    /// period shows up as a golden that moved, in a diff, rather than as a
    /// picture that silently followed it. (250 was the first guess and it is a
    /// quarter, not the peak: the wave folds in the middle, not at the quarter.)
    private static final double PULSE_PEAK = 500;

    // --- card ---------------------------------------------------------------

    /// The question ADR-0164 exists to answer: a card is raised by contrast,
    /// because there is no shadow to raise it with. If this reads flat against
    /// the page behind it, the decision was wrong.
    @Test
    @DisplayName("a card is raised off the page by its surface and its edge")
    void cardDark() {
        paint("card-dark", Theme.NORD_DARK, 300, 110, new Card(
                new Text("Disk usage"),
                new Text("72% of 500 GB")));
    }

    @Test
    @DisplayName("the same card on the light theme, where the contrast runs the other way")
    void cardLight() {
        paint("card-light", Theme.NORD_LIGHT, 300, 110, new Card(
                new Text("Disk usage"),
                new Text("72% of 500 GB")));
    }

    /// Two cards side by side, one under the pointer: §5's optional
    /// hover-elevation, which is opt-in because most cards do not do anything.
    @Test
    @DisplayName("only an interactive card answers the pointer")
    void cardHover() {
        paint("card-hover", Theme.NORD_DARK, 340, 90,
                new Row(List.of(
                        new Card(List.of(new Text("Plain")), Attributes.NONE.classes("grow")),
                        new Card(List.of(new Text("Interactive")),
                                Attributes.NONE.classes("interactive", "grow"))),
                        id("cards")),
                root -> root.children().getFirst()   // the Row
                        .children().get(1));         // the interactive Card
    }

    // --- group-box ----------------------------------------------------------

    /// The title above the frame rather than through it, which is the other half
    /// of ADR-0164. It has to read as a heading *for* the box under it.
    @Test
    @DisplayName("a titled settings cluster")
    void groupBox() {
        paint("group-box-dark", Theme.NORD_DARK, 320, 130, new GroupBox("Appearance",
                new Text("Theme"),
                new Text("Density")));
    }

    /// A frame with no heading is still a frame, and must not leave a gap where
    /// the title would have been.
    @Test
    @DisplayName("an untitled group box has no space where the title is not")
    void groupBoxUntitled() {
        paint("group-box-untitled", Theme.NORD_DARK, 320, 110, new GroupBox(null,
                new Text("Just the frame.")));
    }

    // --- statistic ----------------------------------------------------------

    /// Three ranks of §1.2's hierarchy in one block, and a delta in each of the
    /// two directions — the whole widget in one image.
    @Test
    @DisplayName("statistics with a unit and a delta in each direction")
    void statistics() {
        paint("statistic-dark", Theme.NORD_DARK, 460, 120, new Row(List.of(
                new Statistic("Active users", "12,480", null, "+4.2%",
                        Statistic.Direction.UP, Attributes.NONE.classes("grow")),
                new Statistic("Latency", "128", "ms", "-11 ms",
                        Statistic.Direction.DOWN, Attributes.NONE.classes("grow")),
                new Statistic("Errors", "0", null, "no change",
                        Statistic.Direction.NONE, Attributes.NONE.classes("grow"))),
                id("stats")));
    }

    // --- skeleton -----------------------------------------------------------

    /// The claim §5 makes for a skeleton is that it is the *shape* of what is
    /// coming. So: a title bar over three text bars with a short last line, which
    /// is what a heading and a paragraph look like.
    @Test
    @DisplayName("a title and a paragraph, in placeholder")
    void skeletonText() {
        paint("skeleton-text", Theme.NORD_DARK, 320, 130, new Column(List.of(
                new Skeleton(Skeleton.Shape.TITLE),
                new Skeleton(Skeleton.Shape.TEXT, 3, Attributes.NONE)),
                id("placeholder")));
    }

    /// The other two shapes, and the row an avatar-and-name placeholder actually
    /// is.
    @Test
    @DisplayName("a circle and a rectangle")
    void skeletonShapes() {
        paint("skeleton-shapes", Theme.NORD_DARK, 320, 130, new Row(List.of(
                new Skeleton(Skeleton.Shape.CIRCLE),
                new Column(List.of(
                        new Skeleton(Skeleton.Shape.TITLE),
                        new Skeleton(Skeleton.Shape.TEXT, 2, Attributes.NONE)),
                        Attributes.NONE.classes("grow"))),
                id("avatar")));
    }

    // --- collapse -----------------------------------------------------------

    /// Shut: the chevron points along, and there is nothing under the header —
    /// not a hidden body, no body at all.
    @Test
    @DisplayName("a closed section, with its chevron pointing along")
    void collapseClosed() {
        paint("collapse-closed", Theme.NORD_DARK, 320, 90, new Collapse("Advanced",
                new Text("You should not be able to see this.")));
    }

    /// Open: the chevron has turned a quarter and the body is indented under it.
    @Test
    @DisplayName("an open section, with its chevron turned and its body indented")
    void collapseOpen() {
        paint("collapse-open", Theme.NORD_DARK, 320, 110,
                new Collapse("Advanced", true, open -> { },
                        new Text("Timeout"),
                        new Text("Retries")));
    }
}
