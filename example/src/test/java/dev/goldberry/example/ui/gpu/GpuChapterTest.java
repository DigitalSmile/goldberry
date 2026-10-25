package dev.goldberry.example.ui.gpu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseScene;
import dev.goldberry.example.ShowcaseStyles;
import dev.goldberry.example.docs.CardShape;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.gpu.raytrace.RayTracingMode;
import dev.goldberry.gpu.upscale.RenderScale;
import dev.goldberry.gpu.upscale.UpscaleFilter;
import dev.goldberry.gpu.view.Canvas3d;
import dev.goldberry.input.key.Key;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.offscreen.Session;
import dev.goldberry.widget.Element;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.segmented.Segmented;
import dev.goldberry.widgets.overlay.message.Message;
import dev.goldberry.widgets.panel.card.Card;

/// The GPU screen: a card for each section of the chapter, an on-demand canvas
/// that is drawn again when its slider moves, and the ray-traced room's options
/// panel, which needs no device to answer.
class GpuChapterTest {

    private static final Map<String, DocLink> CARDS = new LinkedHashMap<>();

    static {
        CARDS.put("gpu-frames-card", DocLink.to("components/gpu", "what-the-module-does-to-a-window"));
        CARDS.put("gpu-spinning-card", DocLink.to("components/gpu", "canvas3d"));
        CARDS.put("gpu-turned-card", DocLink.to("components/gpu", "canvas3d"));
        CARDS.put("gpu-room-card", DocLink.to("components/gpu", "canvas3d"));
        CARDS.put("gpu-measured-card", DocLink.to("components/gpu", "what-it-costs"));
    }

    @BeforeEach
    void renderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("the screen holds a card for each section, linking that section")
    void everyCardLinksItsSection() {
        try (var scene = new ShowcaseScene();
                var session = Offscreen.of(1280, 900)
                        .stylesheets(scene.stylesheets(Theme.NORD_DARK))
                        .session(scene.root("gpu"))) {
            assertTrue(session.byId("screen-gpu").isPresent(), "the screen's root is #screen-gpu");
            CARDS.forEach((id, link) -> {
                var card = session.byId(id).orElseThrow(() -> new AssertionError("no card #" + id));
                assertEquals(Optional.of(link), CardShape.link((Card) card.widget()), id);
                assertEquals(List.of(), CardShape.problems((Card) card.widget()), id);
            });
        }
    }

    @Test
    @DisplayName("moving the slider gives the on-demand canvas a new revision, and only then")
    void theSliderRedraws() {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.addAll(ShowcaseStyles.sheets());
        try (var session = Offscreen.of(560, 520).stylesheets(sheets).session(new TurnedCube())) {
            var first = revision(session);
            session.frame();
            assertEquals(first, revision(session), "a frame is not a move");

            assertTrue(session.focus("gpu-turn"), "the slider takes the keyboard");
            session.key(Key.RIGHT);

            assertEquals(first + 1, revision(session));
        }
    }

    /// A session of the ray-traced room's card alone.
    private static Session room() {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK));
        sheets.addAll(ShowcaseStyles.sheets());
        return Offscreen.of(900, 1000).stylesheets(sheets).session(new RayTracedRoom());
    }

    /// The canvas the room's card holds.
    private static Canvas3d roomCanvas(Session session) {
        return canvasUnder(session.byId("gpu-room-card").orElseThrow())
                .orElseThrow(() -> new AssertionError("the card holds no canvas3d"));
    }

    private static Segmented segmented(Session session, String id) {
        return (Segmented) session.byId(id)
                .orElseThrow(() -> new AssertionError("no #" + id))
                .widget();
    }

    @Test
    @DisplayName("the room starts untraced at its box's size, and the upscaler is the canvas's from the next frame")
    void theRoomScales() {
        try (var session = room()) {
            assertEquals(RenderScale.NATIVE, roomCanvas(session).renderScale());
            assertEquals("off", segmented(session, "gpu-room-rt").value(), "the same picture on every machine");
            assertTrue(session.focus("gpu-room-upscaler"));
            session.key(Key.RIGHT);
            assertEquals(UpscaleFilter.FSR1, roomCanvas(session).renderScale().filter());
            assertEquals("fsr1", segmented(session, "gpu-room-upscaler").value());
        }
    }

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    @DisplayName("asking for ray tracing where there is none says why, disables the choice and leaves it off")
    void theRoomSaysWhyThereIsNoTracing() {
        var previous = System.getProperty(RayTracingMode.PROPERTY);
        System.setProperty(RayTracingMode.PROPERTY, "off");
        try (var session = room()) {
            assertTrue(session.byId("gpu-room-unavailable").isEmpty(), "nothing said before anything is asked");
            assertTrue(session.focus("gpu-room-rt"));
            session.key(Key.RIGHT);
            assertEquals("off", segmented(session, "gpu-room-rt").value());
            var options = optionsUnder(session.byId("gpu-room-rt").orElseThrow());
            assertEquals(
                    List.of("off", "hardware"),
                    options.stream().map(Option::value).toList());
            assertTrue(options.get(1).disabled(), "hardware is disabled");
            assertTrue(session.byId("gpu-room-unavailable").isPresent(), "the reason is shown");
            var message = under(session.byId("gpu-room-card").orElseThrow(), Message.class)
                    .getFirst();
            assertTrue(message.text().contains(RayTracingMode.PROPERTY + " is off"), message.text());
        } finally {
            if (previous == null) {
                System.clearProperty(RayTracingMode.PROPERTY);
            } else {
                System.setProperty(RayTracingMode.PROPERTY, previous);
            }
        }
    }

    @Test
    @DisplayName("the room's anti-aliasing and present mode follow their bars, with no window to apply them to")
    void theRoomKeepsItsChoices() {
        try (var session = room()) {
            assertTrue(session.focus("gpu-room-msaa"));
            session.key(Key.RIGHT);
            assertEquals("2", segmented(session, "gpu-room-msaa").value());
            assertTrue(session.focus("gpu-room-present"));
            session.key(Key.RIGHT);
            assertEquals("MAILBOX", segmented(session, "gpu-room-present").value());
            assertTrue(session.focus("gpu-room-anisotropy"));
            session.key(Key.END);
            assertEquals("16", segmented(session, "gpu-room-anisotropy").value());
        }
    }

    /// The revision of the on-demand canvas, read off the widget the card built.
    private static long revision(Session session) {
        return canvasUnder(session.byId("gpu-turned-card").orElseThrow())
                .orElseThrow(() -> new AssertionError("the card holds no canvas3d"))
                .revision();
    }

    /// The options under `element`, in order: a bar's segments.
    private static List<Option> optionsUnder(Element element) {
        return under(element, Option.class);
    }

    /// The widgets of `type` at and under `element`, in order.
    private static <T> List<T> under(Element element, Class<T> type) {
        if (type.isInstance(element.widget())) {
            return List.of(type.cast(element.widget()));
        }
        return element.children().stream()
                .flatMap(child -> under(child, type).stream())
                .toList();
    }

    private static Optional<Canvas3d> canvasUnder(Element element) {
        if (element.widget() instanceof Canvas3d canvas) {
            return Optional.of(canvas);
        }
        return element.children().stream()
                .map(GpuChapterTest::canvasUnder)
                .flatMap(Optional::stream)
                .findFirst();
    }
}
