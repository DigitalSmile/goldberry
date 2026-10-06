package dev.goldberry.example.ui.styling;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.motion.Settle;
import dev.goldberry.example.motion.TileFloor;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
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

/// The three ways something on this toolkit moves by itself, one card each, all
/// of them under *Transition and animation*.
///
/// 1. **A choreography on a canvas.** A floor of glazed tiles settles as a ripple
///    out from one tile and then re-glazes itself, one tile every 1.3 seconds.
///    That is [TileFloor], asking for frames through `Canvas.animating` only while
///    something moves, with a host timer for the wake-ups in between.
/// 2. **`@keyframes`.** Five swatches breathing on a stagger, a mark turning, and
///    a plate cycling through three chart colours. All of it is the stylesheet.
/// 3. **`@starting-style`.** Each entry the button adds fades and drops into place
///    from its starting style. The entries already there stay put, because an
///    element enters once.
///
/// Under reduced motion the floor is still, the keyframes stop and entries
/// appear at once.
///
/// Read more: [Transition and animation](https://goldberry.dev/docs/guide/styling.html#transition-and-animation).
final class MotionCards {

    /// The section all three cards show.
    static final DocLink SECTION = DocLink.to("guide/styling", "transition-and-animation");

    /// The floor's size in tiles.
    static final int COLUMNS = 14;

    static final int ROWS = 6;

    private MotionCards() {}

    /// Keyframes the stylesheet names: a breath on a stagger, a turn, a glaze.
    static Widget keyframes() {
        var swatches = new ArrayList<Widget>();
        for (var i = 0; i < 5; i++) {
            swatches.add(new Panel(List.of(), Attributes.NONE.classes("motion-swatch", "d" + i)));
        }
        swatches.add(new Panel(List.of(), Attributes.NONE.classes("motion-turn")));
        swatches.add(new Panel(List.of(), Attributes.NONE.classes("motion-glaze")));
        return new ShowcaseCard(
                        "motion-keyframes-card",
                        "@keyframes",
                        "A breath staggered by animation-delay, a turn that never ends, and a plate whose keyframes"
                                + " are theme tokens, so it follows the light. Only opacity, colours, shadows,"
                                + " background-position and transform animate, so a frame never runs layout.",
                        SECTION)
                .of(new Row(swatches, Attributes.NONE.id("motion-swatches")));
    }

    /// A floor of tiles that settles from wherever it is pressed.
    record Settling() implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new SettlingState();
        }
    }

    static final class SettlingState extends State<Settling> {

        private final TileFloor floor = new TileFloor(COLUMNS, ROWS, Settle.TILES);

        private @Nullable Host host;

        /// The next glaze swap, or null before there is a host to schedule on.
        private EventLoop.@Nullable Timer swapTimer;

        @Override
        public Widget build(BuildContext context) {
            if (host == null) {
                host = context.host().orElse(null);
                scheduleSwaps();
            }
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
                            new Attributes("motion-floor", Set.of(), "motion-floor"))
                    .animating(style -> floor.at(style.nowMillis()).isMoving(style.nowMillis(), style.reducedMotion()));
            return new ShowcaseCard(
                            "motion-floor-card",
                            "A settle, painted",
                            "A canvas asks for frames only while something on it moves. Each tile drops and turns"
                                    + " before it lands, a little later per place from the one pressed, then a tile"
                                    + " every 1.3 s takes a neighbour's glaze. Press a tile.",
                            SECTION)
                    .of(canvas);
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
    }

    /// A row of chips, each entering from its starting style.
    record Entering() implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new EnteringState();
        }
    }

    static final class EnteringState extends State<Entering> {

        /// How many entries the card has added.
        private int entries;

        @Override
        public Widget build(BuildContext context) {
            var chips = new ArrayList<Widget>();
            for (var i = 1; i <= entries; i++) {
                chips.add(new Chip("Entry " + i)
                        .withAttributes(new Attributes(null, Set.of("motion-entry"), "entry-" + i)));
            }
            return new ShowcaseCard(
                            "motion-entering-card",
                            "@starting-style",
                            "An element's first frame transitions from its starting style. A new entry fades in"
                                    + " from 8 px above over --gb-motion-base; the ones already there stay put,"
                                    + " because an element enters once.",
                            SECTION)
                    .of(
                            new Row(
                                    List.of(
                                            new Button("Add an entry", () -> setState(() -> entries++))
                                                    .id("motion-add"),
                                            new Button("Clear", () -> setState(() -> entries = 0)).id("motion-clear")),
                                    Attributes.NONE.id("motion-entering-actions")),
                            new Row(chips, Attributes.NONE.id("motion-entries")));
        }
    }
}
