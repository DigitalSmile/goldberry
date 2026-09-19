package io.github.digitalsmile.goldberry.widgets.overlay.message;

import static io.github.digitalsmile.goldberry.widgets.TestAttributes.id;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.panel.Described;

/// What a `message` looks like — the four kinds, in both themes, and one caught
/// halfway through arriving.
///
/// The pictures are the point for this widget more than for most. §1.2 forbids
/// colour as the only carrier of meaning, and the whole of the compliance is
/// **four glyphs that are four different drawings at 20 logical pixels** — which
/// no assertion about a `Mark.Kind` can check, because the enum being different
/// is not the drawing being different. `EnclosedMarkTest` in `:core` measures
/// where the ink lands; this is where somebody can see it.
///
/// `./gradlew :widgets:test -Dgoldberry.golden.update=true` rewrites them.
class MessageGoldenTest {

    private Clock.Virtual clock;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        clock = Clock.virtual();
    }

    private static final String SCENE = """
            #scene { padding: 16px; gap: 12px; background: var(--gb-bg); width: 420px }
            """;

    /// The four kinds, one under another, with the middle two carrying the two
    /// things §7 makes optional.
    private static Widget banners() {
        return new Column(
                List.of(
                        new Message(Message.Kind.INFO, "Two devices are signed in to this account."),
                        new Message(Message.Kind.SUCCESS, "Your changes were published.").dismiss(() -> {}),
                        new Message(Message.Kind.WARNING, "This session ends in five minutes.")
                                .actions(new Button("Stay signed in").styled("ghost")),
                        new Message(Message.Kind.DANGER, "Could not save: port 80 is already in use.")),
                id("scene"));
    }

    private WidgetRenderer rendererFor(Theme theme) {
        return new WidgetRenderer(
                        List.of(
                                Controls.baseStylesheet(),
                                theme.load(),
                                Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                        TestFont.get())
                .clock(clock);
    }

    /// One frame to start every banner's arrival, then past the end of it, so the
    /// image is of banners that are **there** rather than of four that are one
    /// frame into fading up.
    ///
    /// This is not golden-test bookkeeping: it is the arrangement §3's entrance
    /// forces on anything that paints a single frame, and it is worth seeing
    /// written down before `toast` and `dialog` inherit it.
    private void paintSettled(String name, Theme theme, int width, int height) {
        var tree = new ElementTree(banners());
        var renderer = rendererFor(theme);

        renderer.render(tree);
        clock.advance(200);
        var settled = renderer.render(tree);

        // A third frame, and the assertion is on that one. A phase settles
        // *inside* `render`, and the renderer asks whether a node is animating
        // before it draws it -- so the frame that finishes an arrival still
        // reports one more, and the frame after it is the one that goes quiet.
        // That is one wasted frame per arrival and it is the shape of every
        // clock-driven animation in the toolkit, not something about banners.
        renderer.render(tree);
        assertFalse(renderer.isAnimating(), "a banner that has been on screen for 200ms is still asking for frames");
        GoldenImage.assertMatches(name, width, height, 1.0f, frame -> BoxPainter.paint(frame, settled));
    }

    @Test
    @DisplayName("the four kinds, dark")
    void dark() {
        paintSettled("message-dark", Theme.NORD_DARK, 420, 260);
    }

    /// Both themes, because the tint and the border are the two things a theme
    /// is allowed to disagree about and a 4% wash is where a light theme goes
    /// wrong quietly.
    @Test
    @DisplayName("the four kinds, light")
    void light() {
        paintSettled("message-light", Theme.NORD_LIGHT, 420, 260);
    }

    /// §3: "out: `opacity` fast", caught in the middle.
    ///
    /// The picture that says the exit exists at all. A banner has no owner
    /// holding it, so the × fades it **while it is still described** and tells the
    /// application when the fade is over — which is why there is anything here to
    /// photograph ([ADR-0175]).
    ///
    /// Only the second banner is going. The other three are at rest beside it, so
    /// the image is a comparison rather than a claim about one box's alpha.
    @Test
    @DisplayName("a banner caught halfway out")
    void departing() {
        var host = new TestHost();
        var tree = new ElementTree(banners(), host);
        var renderer = rendererFor(Theme.NORD_DARK);

        renderer.render(tree);
        clock.advance(200);
        renderer.render(tree);

        // The success banner is the one with a ×, and this is the press.
        Described.of(tree, MessageDismiss.class)
                .getFirst()
                .onPointer(new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0, PointerEvent.Button.PRIMARY, 1, null));
        tree.flush();

        // The frame that starts the fade, then half of §1.7's `fast`.
        renderer.render(tree);
        clock.advance(50);
        var midway = renderer.render(tree);
        assertTrue(renderer.isAnimating(), "the banner is not fading");

        GoldenImage.assertMatches("message-departing", 420, 260, 1.0f, frame -> BoxPainter.paint(frame, midway));
    }

    /// §3: "in: `opacity` + 2px rise, base". A picture no wall clock can take —
    /// the banners are exactly half way up and half way in.
    @Test
    @DisplayName("a banner caught halfway through arriving")
    void arriving() {
        var tree = new ElementTree(banners());
        var renderer = rendererFor(Theme.NORD_DARK);

        // The first frame is what stamps the start: a `Phase` reads the clock in
        // `render`, because that is the only place a widget has one.
        renderer.render(tree);
        assertTrue(renderer.isAnimating(), "the banners did not start arriving");

        clock.advance(80);
        var midway = renderer.render(tree);

        GoldenImage.assertMatches("message-arriving", 420, 260, 1.0f, frame -> BoxPainter.paint(frame, midway));
    }
}
