package dev.goldberry.natives.desktop.dragout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.wayland.WaylandArguments;
import dev.goldberry.natives.wayland.WaylandEvent;

/// A `wl_data_source`'s events, read into the reports the drag acts on after
/// the queue is dispatched.
@DisplayName("a Wayland drag source's events")
class WaylandSourceEventsTest {

    private final WaylandSourceEvents events = new WaylandSourceEvents();

    @Test
    @DisplayName("reads a drag that is taken: target, the data asked for, the drop and the end")
    void delivered() {
        try (var arena = Arena.ofConfined()) {
            events.onEvent(event(arena, WaylandSourceEvents.TARGET, a -> a.string("text/uri-list")));
            events.onEvent(event(arena, WaylandSourceEvents.ACTION, a -> a.unsigned(WaylandSourceEvents.DND_MOVE)));
            events.onEvent(event(arena, WaylandSourceEvents.DROP_PERFORMED, a -> {}));
            events.onEvent(event(
                    arena,
                    WaylandSourceEvents.SEND,
                    a -> a.string("text/uri-list").integer(42)));
            events.onEvent(event(arena, WaylandSourceEvents.FINISHED, a -> {}));
        }

        assertEquals(
                List.of(
                        new WaylandSourceEvents.Report.Target("text/uri-list"),
                        new WaylandSourceEvents.Report.DropPerformed(),
                        new WaylandSourceEvents.Report.Send("text/uri-list", 42),
                        new WaylandSourceEvents.Report.Finished(DragAction.MOVE)),
                events.drain());
        assertEquals(List.of(), events.drain());
    }

    @Test
    @DisplayName("reads a target that took nothing, and a cancel")
    void cancelled() {
        try (var arena = Arena.ofConfined()) {
            events.onEvent(event(arena, WaylandSourceEvents.TARGET, a -> a.object(MemorySegment.NULL)));
            events.onEvent(event(arena, WaylandSourceEvents.CANCELLED, a -> {}));
        }

        assertEquals(
                List.of(new WaylandSourceEvents.Report.Target(null), new WaylandSourceEvents.Report.Cancelled()),
                events.drain());
    }

    @Test
    @DisplayName("finishes with no action when the compositor never named one")
    void noAction() {
        try (var arena = Arena.ofConfined()) {
            events.onEvent(event(arena, WaylandSourceEvents.FINISHED, a -> {}));
        }

        assertEquals(List.of(new WaylandSourceEvents.Report.Finished(null)), events.drain());
    }

    @Test
    @DisplayName("offers copy and move as Wayland's bits, and copy for link alone")
    void masks() {
        assertEquals(
                WaylandSourceEvents.DND_COPY | WaylandSourceEvents.DND_MOVE,
                WaylandSourceEvents.maskOf(EnumSet.of(DragAction.COPY, DragAction.MOVE, DragAction.LINK)));
        assertEquals(WaylandSourceEvents.DND_COPY, WaylandSourceEvents.maskOf(EnumSet.of(DragAction.LINK)));
        assertEquals(WaylandSourceEvents.DND_MOVE, WaylandSourceEvents.maskOf(EnumSet.of(DragAction.MOVE)));
    }

    @Test
    @DisplayName("reads the compositor's action back, ask and none as not said")
    void actions() {
        assertEquals(DragAction.COPY, WaylandSourceEvents.actionOf(1));
        assertEquals(DragAction.MOVE, WaylandSourceEvents.actionOf(2));
        assertNull(WaylandSourceEvents.actionOf(4));
        assertNull(WaylandSourceEvents.actionOf(0));
    }

    private static WaylandEvent event(Arena arena, int opcode, Consumer<WaylandArguments> fill) {
        var arguments = new WaylandArguments(arena, 2);
        fill.accept(arguments);
        return new WaylandEvent(opcode, arguments.segment());
    }
}
