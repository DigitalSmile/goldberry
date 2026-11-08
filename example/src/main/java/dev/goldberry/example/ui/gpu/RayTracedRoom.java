package dev.goldberry.example.ui.gpu;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.Window;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.gpu.scene.RoomRenderer;
import dev.goldberry.example.gpu.scene.SceneSettings;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.example.ui.gallery.WideCard;
import dev.goldberry.gpu.raytrace.RayTracer;
import dev.goldberry.gpu.raytrace.RayTracing;
import dev.goldberry.gpu.raytrace.RayTracingMode;
import dev.goldberry.gpu.upscale.RenderScale;
import dev.goldberry.gpu.upscale.UpscaleFilter;
import dev.goldberry.gpu.view.Canvas3d;
import dev.goldberry.render.window.PresentMode;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.segmented.Segmented;
import dev.goldberry.widgets.controls.select.Select;
import dev.goldberry.widgets.controls.toggle.Toggle;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.form.field.Field;
import dev.goldberry.widgets.overlay.message.Message;

/// The ray-traced room and its options panel: what a game's graphics settings
/// change, live, on a `canvas3d` drawn by the showcase's [RoomRenderer]. A
/// [WideCard], so the wall gives it a wide column and the room room to show.
///
/// Ray tracing starts off, so the screen is the same picture on every machine.
/// It traces in software on any GPU, or in hardware where there is a device for
/// it: choosing hardware asks [RayTracer#availability()], and where there is
/// none, or where either tier refuses, the choice is disabled and the reason is
/// shown. Anti-aliasing and anisotropy
/// are offered at fixed counts, and the renderer takes the most the device has
/// up to the one chosen. The render scale is the canvas's, and the present
/// mode and the frame cap are the window's.
///
/// Read more: [`canvas3d`](https://goldberry.dev/docs/components/gpu.html#canvas3d).
record RayTracedRoom() implements Widget.Stateful, WideCard {

    /// The render scales offered: native, and FSR 1's four quality modes.
    static final List<Option> SCALES = List.of(
            new Option("1.0", "Native"),
            new Option("0.77", "Ultra quality (77%)"),
            new Option("0.67", "Quality (67%)"),
            new Option("0.59", "Balanced (59%)"),
            new Option("0.5", "Performance (50%)"));

    /// The frame caps offered, in frames a second; 0 is none.
    static final List<Option> CAPS = List.of(
            new Option("0", "None"),
            new Option("30", "30 fps"),
            new Option("60", "60 fps"),
            new Option("120", "120 fps"),
            new Option("144", "144 fps"));

    @Override
    public State<?> createState() {
        return new RoomState();
    }

    static final class RoomState extends State<RayTracedRoom> {

        private SceneSettings settings = SceneSettings.DEFAULT;
        private RenderScale scale = RenderScale.NATIVE;
        private PresentMode presentMode = PresentMode.VSYNC;
        private double frameCap;

        /// Why a tier cannot trace here, for each tier something asked of
        /// and was refused.
        private final Map<RayTracingMode, String> unavailable = new EnumMap<>(RayTracingMode.class);

        private @Nullable Host host;

        private final RoomRenderer renderer = new RoomRenderer(() -> settings, this::refused);

        /// The renderer's refusal, said from inside a paint: the tree is
        /// rebuilt on the next turn of the loop, as `canvas3d`'s notice is.
        private void refused(RayTracingMode mode, String reason) {
            var current = host;
            if (current == null) {
                return;
            }
            current.after(Duration.ZERO, () -> {
                if (isMounted()) {
                    setState(() -> {
                        unavailable.put(mode, reason);
                        settings = settings.withRayTracing(RayTracingMode.OFF);
                    });
                }
            });
        }

        @Override
        public Widget build(BuildContext context) {
            host = context.host().orElse(null);
            var canvas = new Canvas3d(renderer)
                    .continuous(true)
                    .depth(Canvas3d.Depth.D32)
                    .renderScale(scale)
                    .withAttributes(Attributes.NONE.id("gpu-room"));
            // Two columns under the canvas: how the picture is lit and made,
            // then how it is drawn and shown.
            var panel = new ArrayList<Widget>();
            var display = new ArrayList<Widget>();
            panel.add(new Field("Ray tracing", rayTracing()));
            panel.add(new Field("Traced effects", effects()));
            panel.add(new Field(
                    "Render scale",
                    new Select(
                                    Double.toString(scale.factor()),
                                    value -> setState(() -> scale = scale.withFactor(Double.parseDouble(value))),
                                    SCALES.toArray(Option[]::new))
                            .withAttributes(Attributes.NONE.id("gpu-room-scale"))));
            panel.add(new Field(
                    "Upscaler",
                    segmented(
                            "gpu-room-upscaler",
                            scale.filter().toString(),
                            value -> setState(() -> scale = scale.withFilter(UpscaleFilter.named(value))),
                            new Option("linear", "Bilinear"),
                            new Option("fsr1", "FSR 1"))));
            display.add(new Field(
                    "Anti-aliasing",
                    segmented(
                            "gpu-room-msaa",
                            Integer.toString(settings.samples()),
                            value -> setState(() -> settings = settings.withSamples(Integer.parseInt(value))),
                            counts(SceneSettings.SAMPLE_COUNTS, "Off"))));
            display.add(new Field(
                    "Anisotropy",
                    segmented(
                            "gpu-room-anisotropy",
                            Integer.toString(settings.anisotropy()),
                            value -> setState(() -> settings = settings.withAnisotropy(Integer.parseInt(value))),
                            counts(SceneSettings.ANISOTROPY, "Off"))));
            display.add(new Field(
                    "Present mode",
                    segmented(
                            "gpu-room-present",
                            presentMode.name(),
                            value -> present(PresentMode.valueOf(value)),
                            new Option(PresentMode.VSYNC.name(), "VSync"),
                            new Option(PresentMode.MAILBOX.name(), "Mailbox"),
                            new Option(PresentMode.IMMEDIATE.name(), "Immediate"))));
            display.add(new Field(
                    "Frame cap",
                    new Select(
                                    Integer.toString((int) frameCap),
                                    value -> cap(Double.parseDouble(value)),
                                    CAPS.toArray(Option[]::new))
                            .withAttributes(Attributes.NONE.id("gpu-room-cap"))));
            var content = new ArrayList<Widget>();
            content.add(canvas);
            content.add(new Row(
                    List.of(
                            new Column(panel, Attributes.NONE.classes("gpu-room-settings")),
                            new Column(display, Attributes.NONE.classes("gpu-room-settings"))),
                    Attributes.NONE.classes("gpu-room-panel")));
            var noHardware = unavailable.get(RayTracingMode.HARDWARE);
            if (noHardware != null) {
                content.add(new Message(Message.Kind.WARNING, "No hardware ray tracing here: " + noHardware)
                        .withAttributes(Attributes.NONE.id("gpu-room-unavailable")));
            }
            var noSoftware = unavailable.get(RayTracingMode.SOFTWARE);
            if (noSoftware != null) {
                content.add(new Message(Message.Kind.WARNING, "No software ray tracing here: " + noSoftware)
                        .withAttributes(Attributes.NONE.id("gpu-room-software-unavailable")));
            }
            return new ShowcaseCard(
                            "gpu-room-card",
                            "Ray tracing and settings",
                            "A room lit by the ray tracer, in software on any GPU or in hardware where there is"
                                    + " one: shadows, occlusion and reflections traced at the size the canvas draws"
                                    + " at, then drawn by the showcase's own shaders. Every setting applies to the"
                                    + " next frame.",
                            DocLink.to("components/gpu", "canvas3d"))
                    .wide()
                    .of(content);
        }

        /// Off, software and hardware, a tier disabled once it is known to
        /// refuse.
        private Widget rayTracing() {
            return new Segmented(
                    settings.rayTracing().name().toLowerCase(Locale.ROOT),
                    List.of(
                            new Option("off", "Off"),
                            tier(RayTracingMode.SOFTWARE, "Software"),
                            tier(RayTracingMode.HARDWARE, "Hardware")),
                    null,
                    value -> chooseRayTracing(RayTracingMode.parse(value)),
                    false,
                    Attributes.NONE.id("gpu-room-rt"));
        }

        private Option tier(RayTracingMode mode, String label) {
            return new Option(
                    mode.name().toLowerCase(Locale.ROOT),
                    label,
                    null,
                    false,
                    null,
                    unavailable.containsKey(mode),
                    Attributes.NONE);
        }

        /// Traces by `mode`'s tier: in software at once, the renderer saying
        /// so if it cannot; in hardware where there is a device for it, and
        /// otherwise says why and leaves tracing as it was.
        void chooseRayTracing(RayTracingMode mode) {
            if (mode != RayTracingMode.HARDWARE) {
                setState(() -> settings = settings.withRayTracing(mode));
                return;
            }
            switch (RayTracer.availability()) {
                case RayTracing.Available _ -> setState(() -> settings = settings.withRayTracing(mode));
                case RayTracing.Unavailable(var reason) -> setState(() -> unavailable.put(mode, reason));
            }
        }

        private Widget effects() {
            var off = !settings.traced();
            return new Column(
                    List.of(
                            new Toggle(
                                    "Shadows",
                                    settings.shadows(),
                                    null,
                                    value -> setState(() -> settings = settings.withShadows(value)),
                                    off,
                                    Attributes.NONE.id("gpu-room-shadows")),
                            new Toggle(
                                    "Ambient occlusion",
                                    settings.occlusion(),
                                    null,
                                    value -> setState(() -> settings = settings.withOcclusion(value)),
                                    off,
                                    Attributes.NONE.id("gpu-room-occlusion")),
                            new Toggle(
                                    "Reflections",
                                    settings.reflections(),
                                    null,
                                    value -> setState(() -> settings = settings.withReflections(value)),
                                    off,
                                    Attributes.NONE.id("gpu-room-reflections"))),
                    Attributes.NONE);
        }

        private void present(PresentMode mode) {
            setState(() -> presentMode = mode);
            window().ifPresent(window -> window.presentMode(mode));
        }

        private void cap(double hertz) {
            setState(() -> frameCap = hertz);
            window().ifPresent(window -> window.frameCap(hertz));
        }

        /// The window the screen is in, or empty where there is none: an
        /// offscreen picture's host has no window, and the choice is kept for
        /// the panel alone.
        private Optional<Window> window() {
            var current = host;
            if (current == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(current.window());
            } catch (UnsupportedOperationException e) {
                return Optional.empty();
            }
        }

        private static Widget segmented(String id, String value, Consumer<String> onChange, Option... options) {
            return new Segmented(value, List.of(options), null, onChange, false, Attributes.NONE.id(id));
        }

        /// A segment a count, `1` named `one`.
        private static Option[] counts(List<Integer> counts, String one) {
            return counts.stream()
                    .map(count -> new Option(
                            Integer.toString(count), count == 1 ? one : String.format(Locale.ROOT, "%d×", count)))
                    .toArray(Option[]::new);
        }
    }
}
