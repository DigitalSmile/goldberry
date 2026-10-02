package dev.goldberry.example.ui;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.example.motion.Settle;
import dev.goldberry.example.motion.TileFloor;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.render.event.EventLoop;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.chip.Chip;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.canvas.Canvas;
import dev.goldberry.widgets.core.canvas.Input;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.panel.masonry.Masonry;
import dev.goldberry.widgets.text.Text;

/// The **Motion** screen: the three ways something on this toolkit moves by
/// itself, one card each.
///
/// 1. **A choreography on a canvas.** A floor of glazed tiles settles as a ripple
///    out from one tile and then re-glazes itself, one tile every 1.3 seconds.
///    That is [TileFloor], asking for frames through `Canvas.animating` only while
///    something moves, with a host timer for the wake-ups in between. Press a tile
///    to replay the settle from it.
/// 2. **`@keyframes`.** Five swatches breathing on a stagger, a mark turning, and
///    a plate cycling through three chart colours. All of it is `showcase.css`
///    and nothing here is Java.
/// 3. **`@starting-style`.** Each entry the button adds fades and drops into place
///    from its starting style. The entries already there stay put, because an
///    element enters once.
///
/// The screen is photographed at the offscreen renderer's 200 ms: one moment
/// of three animations, the same on every run because the clock is virtual. The
/// floor is part way through its ripple, which is the picture worth keeping.
///
/// Read more: [Motion](https://goldberry.dev/docs/guide/design-system.html#motion).
public record MotionScreen() implements Widget.Stateful {

    private static final String NOTE =
            "Three ways a thing moves by itself: a choreography painted on a canvas that asks for its own"
                    + " frames, a stylesheet's @keyframes, and an @starting-style that an element enters"
                    + " from. With reduced motion on, the floor is still, the keyframes stop and entries"
                    + " appear at once.";

    /// The floor's size in tiles.
    static final int COLUMNS = 14;

    static final int ROWS = 6;

    @Override
    public State<?> createState() {
        return new MotionState();
    }

    static final class MotionState extends State<MotionScreen> {

        private final TileFloor floor = new TileFloor(COLUMNS, ROWS, Settle.TILES);

        private @Nullable Host host;

        /// The next glaze swap, or null before there is a host to schedule on.
        private EventLoop.@Nullable Timer swapTimer;

        /// How many entries the starting-style card has added.
        private int entries;

        @Override
        public Widget build(BuildContext context) {
            if (host == null) {
                host = context.host().orElse(null);
                scheduleSwaps();
            }
            return new Wall(
                    "motion", "Motion", NOTE, 2, Masonry.UNSET, List.of(floorCard(), keyframesCard(), enteringCard()));
        }

        private Widget floorCard() {
            var canvas = new Canvas(
                            floor::paint,
                            new Input() {

                                @Override
                                public void onPointer(PointerEvent event) {
                                    if (event.kind() != PointerEvent.Kind.PRESSED) {
                                        return;
                                    }
                                    var at = event.content();
                                    floor.tileAt(at.x(), at.y(), LogicalSize.of(at.width(), at.height()))
                                            .ifPresent(place -> replay(place.column(), place.row()));
                                }

                                @Override
                                public boolean focusable() {
                                    return false;
                                }
                            },
                            attributes("motion-floor"))
                    .animating(style -> floor.at(style.nowMillis()).isMoving(style.nowMillis(), style.reducedMotion()));
            return captioned(
                    "A settle, painted",
                    "motion-floor-card",
                    canvas,
                    caption("Each tile drops 20 px and turns up to 4° before it lands, 45 ms later per"
                            + " place from the focus. Then one tile every 1.3 s takes a neighbouring"
                            + " band's glaze. Press a tile to settle the floor again from there."));
        }

        private Widget keyframesCard() {
            var swatches = new ArrayList<Widget>();
            for (var i = 0; i < 5; i++) {
                swatches.add(new Panel(List.of(), Attributes.NONE.classes("motion-swatch", "d" + i)));
            }
            swatches.add(new Panel(List.of(), Attributes.NONE.classes("motion-turn")));
            swatches.add(new Panel(List.of(), Attributes.NONE.classes("motion-glaze")));
            return captioned(
                    "@keyframes",
                    "motion-keyframes-card",
                    new Row(swatches, Attributes.NONE.id("motion-swatches")),
                    caption("A breath staggered by animation-delay, a turn that never ends, and a plate"
                            + " whose keyframes are var(--gb-chart-*), so it follows the theme."));
        }

        private Widget enteringCard() {
            var chips = new ArrayList<Widget>();
            for (var i = 1; i <= entries; i++) {
                chips.add(new Chip("Entry " + i)
                        .withAttributes(new Attributes(null, Set.of("motion-entry"), "entry-" + i)));
            }
            return captioned(
                    "@starting-style",
                    "motion-entering-card",
                    new Row(
                            List.of(
                                    new Button("Add an entry", () -> setState(() -> entries++)),
                                    new Button("Clear", () -> setState(() -> entries = 0))),
                            Attributes.NONE.id("motion-entering-actions")),
                    new Row(chips, Attributes.NONE.id("motion-entries")),
                    caption("A new entry enters from opacity 0 and 8 px above, over --gb-motion-base."
                            + " The entries already there stay where they are."));
        }

        private void replay(int column, int row) {
            setState(() -> floor.replay(column, row));
            scheduleSwaps();
        }

        /// The first swap once the settle has had time to finish, then one every
        /// [TileFloor#SWAP_EVERY_MILLIS].
        ///
        /// A wall-clock timer and a frame-clock settle can disagree by a frame or
        /// two, and a swap that starts while the last tile is still landing is
        /// invisible, so nothing checks for it.
        private void scheduleSwaps() {
            if (host == null) {
                return;
            }
            cancelSwaps();
            var first = (long) floor.settledAfter() + TileFloor.SWAP_EVERY_MILLIS;
            swapTimer = host.after(Duration.ofMillis(first), this::swapAndReschedule);
        }

        private void swapAndReschedule() {
            if (!isMounted() || host == null) {
                return;
            }
            setState(floor::swap);
            swapTimer = host.after(Duration.ofMillis(TileFloor.SWAP_EVERY_MILLIS), this::swapAndReschedule);
        }

        private void cancelSwaps() {
            if (swapTimer != null) {
                swapTimer.cancel();
                swapTimer = null;
            }
        }

        @Override
        protected void dispose() {
            cancelSwaps();
            super.dispose();
        }

        /// The floor, for a test.
        TileFloor floor() {
            return floor;
        }

        private static Attributes attributes(String id) {
            return new Attributes(id, Set.of(), id);
        }

        private static Widget caption(String text) {
            return new Text(text, Attributes.NONE.classes("caption"));
        }

        private static Widget captioned(String title, String id, Widget... parts) {
            var children = new ArrayList<Widget>(parts.length + 1);
            children.add(new Text(title, Attributes.NONE.classes("card-title")));
            children.addAll(List.of(parts));
            return new Card(List.copyOf(children), new Attributes(id, Set.of("wall-card"), id));
        }
    }
}
