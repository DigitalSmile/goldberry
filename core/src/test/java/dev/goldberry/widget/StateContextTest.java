package dev.goldberry.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.Host;
import dev.goldberry.RendererRequirement;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.layout.Length;
import dev.goldberry.offscreen.Offscreen;
import dev.goldberry.paint.Box;
import dev.goldberry.render.event.EventLoop;
import dev.goldberry.widget.style.Paints;

/// A state reaches where it is in the tree, the host included, before its
/// first build: what a screen that starts something timed when it appears
/// needs from `initState`.
///
/// Read more:
/// [Writing a widget](https://goldberry.dev/docs/guide/writing-a-widget.html#the-three-shapes).
@DisplayName("a state's context")
class StateContextTest {

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    /// Something to paint, so a tree of one stateful widget is a picture.
    private record Square() implements Widget.Leaf, Paints {

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.filled(0xFF000000).size(Length.points(10), Length.points(10));
        }
    }

    /// Writes down what its context answered in `initState`, and sets a timer
    /// there on the host it found.
    private record Opening(List<String> log) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new OpeningState();
        }
    }

    private static final class OpeningState extends State<Opening> {

        private @Nullable BuildContext initial;
        private @Nullable BuildContext built;
        private Optional<EventLoop.Timer> opening = Optional.empty();

        @Override
        protected void initState() {
            initial = context();
            var log = widget().log();
            opening = context().host().map(host -> {
                log.add("host in initState");
                return host.after(
                        Duration.ofMillis(5),
                        () -> log.add("opened@" + (long) host.clock().nowMillis()));
            });
        }

        @Override
        public Widget build(BuildContext context) {
            built = context;
            return new Square();
        }

        @Override
        protected void dispose() {
            opening.ifPresent(EventLoop.Timer::cancel);
        }
    }

    @Test
    @DisplayName("answers in initState with the host the tree is built into, and a timer set there fires on time")
    void hostInInitState() {
        var log = new ArrayList<String>();
        try (var session = Offscreen.of(40, 40).session(new Opening(log))) {
            assertEquals(List.of("host in initState"), log);
            session.advance(Duration.ofMillis(100));
            assertEquals(List.of("host in initState", "opened@5"), log);
        }
    }

    @Test
    @DisplayName("is the context build is handed")
    void sameAsBuild() {
        var tree = new ElementTree(new Opening(new ArrayList<>()));
        var state = (OpeningState) tree.root().state().orElseThrow();
        assertSame(state.initial, state.built);
        assertSame(tree.root(), state.context());
        tree.unmount();
    }

    @Test
    @DisplayName("has no host in a tree with no window, which is an answer and not a failure")
    void noHost() {
        var log = new ArrayList<String>();
        var tree = new ElementTree(new Opening(log));
        var state = (OpeningState) tree.root().state().orElseThrow();
        assertEquals(Optional.<Host>empty(), state.context().host());
        assertTrue(log.isEmpty(), "nothing was scheduled without a host");
        tree.unmount();
    }

    @Test
    @DisplayName("is refused before mount and after dispose")
    void outsideTheLifetime() {
        var unmounted = new OpeningState();
        assertThrows(IllegalStateException.class, unmounted::context);

        var tree = new ElementTree(new Opening(new ArrayList<>()));
        var state = (OpeningState) tree.root().state().orElseThrow();
        tree.unmount();
        assertThrows(IllegalStateException.class, state::context);
    }
}
