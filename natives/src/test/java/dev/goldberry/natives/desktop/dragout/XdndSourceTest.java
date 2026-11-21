package dev.goldberry.natives.desktop.dragout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// XDND's source side, driven by a fake X server: where the pointer is, what
/// is under it, and every message and property the source writes.
@DisplayName("the XDND source")
class XdndSourceTest {

    private static final long SOURCE = 0x100;
    private static final long FOREIGN = 0x200;
    private static final long OTHER = 0x300;
    private static final long OWN = 0x400;

    /// A server with atoms numbered from 1000, a pointer the test moves and a
    /// map of root points to what is under them.
    private static final class FakeX implements XdndTransport {

        final Map<String, Long> atoms = new HashMap<>();
        final List<String> sent = new ArrayList<>();
        final List<long[]> payloads = new ArrayList<>();
        final Map<Long, byte[]> properties = new LinkedHashMap<>();
        final Map<Long, Long> propertyTypes = new HashMap<>();
        final List<String> notified = new ArrayList<>();
        final Map<Integer, Target> under = new HashMap<>();
        long[] typeList = new long[0];

        @Nullable
        Long owned;

        Pointer pointer = new Pointer(0, 0, true, false, false);
        long maxBytes = 1 << 20;

        @Override
        public long sourceWindow() {
            return SOURCE;
        }

        @Override
        public long atom(String name) {
            return atoms.computeIfAbsent(name, _ -> 1000L + atoms.size());
        }

        @Override
        public @Nullable String atomName(long atom) {
            return atoms.entrySet().stream()
                    .filter(entry -> entry.getValue() == atom)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Pointer pointer() {
            return pointer;
        }

        @Override
        public @Nullable Target targetAt(int rootX, int rootY) {
            return under.get(rootX);
        }

        @Override
        public void send(long window, long type, long[] data) {
            sent.add(atomName(type) + "->" + Long.toHexString(window));
            payloads.add(data.clone());
        }

        @Override
        public void typeList(long[] list) {
            typeList = list.clone();
        }

        @Override
        public void ownSelection(long selection) {
            owned = selection;
        }

        @Override
        public void writeProperty(long window, long property, long type, int format, byte[] data) {
            properties.put(property, data.clone());
            propertyTypes.put(property, type);
        }

        @Override
        public void notifySelection(long requestor, long selection, long target, long property, long time) {
            notified.add(atomName(target) + ":" + (property == 0 ? "refused" : "done"));
        }

        @Override
        public long maxPropertyBytes() {
            return maxBytes;
        }

        @Override
        public int escapeKeycode() {
            return 9;
        }

        @Override
        public void flush() {}

        /// Moves the pointer to `x` on the root, the button still held.
        void moveTo(int x) {
            pointer = new Pointer(x, 5, true, false, false);
        }

        void release() {
            pointer = new Pointer(pointer.rootX(), pointer.rootY(), false, false, false);
        }

        long[] last() {
            return payloads.getLast();
        }
    }

    /// Records what the source says, in order.
    private static final class Heard implements DragSourceListener {

        final List<String> log = new ArrayList<>();

        @Override
        public void over(long window, int x, int y) {
            log.add("over:" + Long.toHexString(window) + "@" + x + "," + y);
        }

        @Override
        public void outside() {
            log.add("outside");
        }

        @Override
        public void delivered(@Nullable DragAction action) {
            log.add("delivered:" + action);
        }

        @Override
        public void releasedOver(long window, int x, int y) {
            log.add("released:" + Long.toHexString(window) + "@" + x + "," + y);
        }

        @Override
        public void cancelled() {
            log.add("cancelled");
        }
    }

    private record Data(Map<String, byte[]> byType) implements DragData {

        @Override
        public List<String> types() {
            return List.copyOf(byType.keySet());
        }

        @Override
        public byte @Nullable [] bytes(String mime) {
            return byType.get(mime);
        }
    }

    private final FakeX x = new FakeX();
    private final Heard heard = new Heard();

    private XdndSource source(Set<DragAction> actions, String... types) {
        var byType = new LinkedHashMap<String, byte[]>();
        for (var type : types) {
            byType.put(type, ("bytes of " + type).getBytes(StandardCharsets.UTF_8));
        }
        x.under.put(10, new XdndTransport.Target(FOREIGN, 5, false, 0, 0));
        x.under.put(20, new XdndTransport.Target(OTHER, 4, false, 0, 0));
        x.under.put(30, new XdndTransport.Target(OWN, 0, true, 7, 8));
        return new XdndSource(x, new Data(byType), actions, heard);
    }

    private XdndSource started(String... types) {
        var source = source(EnumSet.of(DragAction.COPY), types);
        source.start();
        return source;
    }

    private void status(XdndSource source, long from, boolean accept, String action) {
        source.handle(new XdndEvent.ClientMessage(
                SOURCE, x.atom("XdndStatus"), new long[] {from, accept ? 1 : 0, 0, 0, x.atom(action)}));
    }

    private void finished(XdndSource source, long from, boolean taken, String action) {
        source.handle(new XdndEvent.ClientMessage(
                SOURCE, x.atom("XdndFinished"), new long[] {from, taken ? 1 : 0, x.atom(action), 0, 0}));
    }

    @Test
    @DisplayName("takes XdndSelection at the start, and writes the type list only past three types")
    void selection() {
        started("text/uri-list", "text/plain");
        assertEquals(x.atom("XdndSelection"), x.owned);
        assertEquals(0, x.typeList.length);

        var x2 = new FakeX();
        var many = new XdndSource(
                x2,
                new Data(Map.of("a/1", new byte[0], "a/2", new byte[0], "a/3", new byte[0], "a/4", new byte[0])),
                EnumSet.of(DragAction.COPY),
                heard);
        many.start();
        assertEquals(4, x2.typeList.length);
    }

    @Nested
    @DisplayName("over another application")
    class Foreign {

        @Test
        @DisplayName("enters with its version and the first three types, then sends a position")
        void enters() {
            var source = started("text/uri-list", "text/plain");
            x.moveTo(10);
            source.poll();

            assertEquals(List.of("XdndEnter->200", "XdndPosition->200"), x.sent);
            var enter = x.payloads.getFirst();
            assertEquals(SOURCE, enter[0]);
            assertEquals(5L << 24, enter[1], "version 5, no more than three types");
            assertEquals(x.atom("text/uri-list"), enter[2]);
            assertEquals(x.atom("text/plain"), enter[3]);
            assertEquals(0, enter[4]);
            var position = x.last();
            assertEquals((10L << 16) | 5, position[2], "root x and y packed");
            assertEquals(x.atom("XdndActionCopy"), position[4]);
        }

        @Test
        @DisplayName("never has two positions unanswered: a move while waiting is sent when the status comes")
        void onePositionAtATime() {
            var source = started("text/plain");
            x.moveTo(10);
            source.poll();
            x.pointer = new XdndTransport.Pointer(10, 6, true, false, false);
            source.poll();
            assertEquals(
                    1, x.sent.stream().filter(s -> s.startsWith("XdndPosition")).count());

            status(source, FOREIGN, true, "XdndActionCopy");
            assertEquals(
                    2, x.sent.stream().filter(s -> s.startsWith("XdndPosition")).count());
            assertEquals((10L << 16) | 6, x.last()[2]);
        }

        @Test
        @DisplayName("leaves one target for the next, speaking the older one's version to it")
        void retargets() {
            var source = started("text/plain");
            x.moveTo(10);
            source.poll();
            x.moveTo(20);
            source.poll();

            assertEquals(
                    List.of(
                            "XdndEnter->200",
                            "XdndPosition->200",
                            "XdndLeave->200",
                            "XdndEnter->300",
                            "XdndPosition->300"),
                    x.sent);
            assertEquals(4L << 24, x.payloads.get(3)[1], "version 4 to a version-4 target");
        }

        @Test
        @DisplayName("released over a target that accepted, drops, and is delivered when it finishes")
        void drops() {
            var source = started("text/plain");
            x.moveTo(10);
            source.poll();
            status(source, FOREIGN, true, "XdndActionCopy");
            x.release();
            source.poll();

            assertEquals("XdndDrop->200", x.sent.getLast());
            assertTrue(source.isWaiting());
            assertEquals(List.of(), heard.log, "nothing is said before the finish");

            finished(source, FOREIGN, true, "XdndActionMove");
            assertEquals(List.of("delivered:MOVE"), heard.log);
            assertTrue(source.isEnded());
        }

        @Test
        @DisplayName("a version-4 target's finish means taken, with the action its last status named")
        void versionFourFinish() {
            var source = started("text/plain");
            x.moveTo(20);
            source.poll();
            status(source, OTHER, true, "XdndActionLink");
            x.release();
            source.poll();
            finished(source, OTHER, false, "XdndActionCopy");

            assertEquals(List.of("delivered:LINK"), heard.log);
        }

        @Test
        @DisplayName("a version-5 finish that says not taken is a cancel")
        void notTaken() {
            var source = started("text/plain");
            x.moveTo(10);
            source.poll();
            status(source, FOREIGN, true, "XdndActionCopy");
            x.release();
            source.poll();
            finished(source, FOREIGN, false, "XdndActionCopy");

            assertEquals(List.of("cancelled"), heard.log);
        }

        @Test
        @DisplayName("released over a target that refused, leaves it and is cancelled")
        void refused() {
            var source = started("text/plain");
            x.moveTo(10);
            source.poll();
            status(source, FOREIGN, false, "XdndActionCopy");
            x.release();
            source.poll();

            assertEquals("XdndLeave->200", x.sent.getLast());
            assertEquals(List.of("cancelled"), heard.log);
        }

        @Test
        @DisplayName("released before the target answered, waits for the answer and goes by it")
        void waitsForTheAnswer() {
            var source = started("text/plain");
            x.moveTo(10);
            source.poll();
            x.release();
            source.poll();
            assertTrue(source.isWaiting());
            assertFalse(x.sent.getLast().startsWith("XdndDrop"));

            status(source, FOREIGN, true, "XdndActionCopy");
            assertEquals("XdndDrop->200", x.sent.getLast());
            finished(source, FOREIGN, true, "XdndActionCopy");
            assertEquals(List.of("delivered:COPY"), heard.log);
        }

        @Test
        @DisplayName("a target that never finishes times out as delivered, saying no action")
        void timesOut() {
            var source = started("text/plain");
            x.moveTo(10);
            source.poll();
            status(source, FOREIGN, true, "XdndActionCopy");
            x.release();
            source.poll();
            source.timeOut();

            assertEquals(List.of("delivered:null"), heard.log);
        }

        @Test
        @DisplayName("ignores a status from a window it is not over")
        void strayStatus() {
            var source = started("text/plain");
            x.moveTo(10);
            source.poll();
            status(source, OTHER, true, "XdndActionCopy");
            x.release();
            source.poll();

            assertTrue(source.isWaiting(), "still waiting for its own target's answer");
        }
    }

    @Nested
    @DisplayName("over the application's own window")
    class Own {

        @Test
        @DisplayName("speaks no XDND to it, and says where the pointer is in it")
        void reports() {
            var source = started("text/plain");
            x.moveTo(30);
            source.poll();

            assertEquals(List.of(), x.sent);
            assertEquals(List.of("over:400@7,8"), heard.log);
        }

        @Test
        @DisplayName("leaving an other application's window for an own one sends the leave first")
        void leavesForeignFirst() {
            var source = started("text/plain");
            x.moveTo(10);
            source.poll();
            x.moveTo(30);
            source.poll();

            assertEquals("XdndLeave->200", x.sent.getLast());
            assertEquals(List.of("over:400@7,8"), heard.log);
        }

        @Test
        @DisplayName("says outside when it moves off onto another application")
        void outside() {
            var source = started("text/plain");
            x.moveTo(30);
            source.poll();
            x.moveTo(10);
            source.poll();

            assertEquals(List.of("over:400@7,8", "outside"), heard.log);
            assertEquals("XdndEnter->200", x.sent.getFirst());
        }

        @Test
        @DisplayName("released over it, it is that window's")
        void released() {
            var source = started("text/plain");
            x.moveTo(30);
            source.poll();
            x.release();
            source.poll();

            assertEquals(List.of("over:400@7,8", "released:400@7,8"), heard.log);
            assertTrue(source.isEnded());
        }
    }

    @Test
    @DisplayName("released over nothing, it is cancelled")
    void releasedOverNothing() {
        var source = started("text/plain");
        x.moveTo(99);
        source.poll();
        x.release();
        source.poll();

        assertEquals(List.of("cancelled"), heard.log);
    }

    @Test
    @DisplayName("Escape while dragging leaves the target and cancels")
    void escape() {
        var source = started("text/plain");
        x.moveTo(10);
        source.poll();

        assertTrue(source.handle(new XdndEvent.KeyPress(9)));
        assertEquals("XdndLeave->200", x.sent.getLast());
        assertEquals(List.of("cancelled"), heard.log);
        assertFalse(source.handle(new XdndEvent.KeyPress(10)), "other keys are SDL's");
    }

    @Test
    @DisplayName("Shift asks for a move and Shift with Control for a link, where the offer allows them")
    void modifiers() {
        var source = source(EnumSet.allOf(DragAction.class), "text/plain");
        source.start();
        x.pointer = new XdndTransport.Pointer(10, 5, true, true, false);
        source.poll();
        assertEquals(x.atom("XdndActionMove"), x.last()[4]);

        status(source, FOREIGN, true, "XdndActionMove");
        x.pointer = new XdndTransport.Pointer(10, 6, true, true, true);
        source.poll();
        assertEquals(x.atom("XdndActionLink"), x.last()[4]);

        var copyOnly = new FakeX();
        var limited = new XdndSource(
                copyOnly, new Data(Map.of("text/plain", new byte[0])), EnumSet.of(DragAction.COPY), heard);
        copyOnly.under.put(10, new XdndTransport.Target(FOREIGN, 5, false, 0, 0));
        limited.start();
        copyOnly.pointer = new XdndTransport.Pointer(10, 5, true, true, false);
        limited.poll();
        assertEquals(copyOnly.atom("XdndActionCopy"), copyOnly.last()[4], "a copy-only offer stays a copy");
    }

    @Nested
    @DisplayName("asked for its data")
    class Data_ {

        private XdndEvent.SelectionRequest request(String target, long property) {
            return new XdndEvent.SelectionRequest(
                    SOURCE, FOREIGN, x.atom("XdndSelection"), x.atom(target), property, 0);
        }

        @Test
        @DisplayName("answers a type it offers with its bytes, as 8-bit data of that type")
        void answers() {
            var source = started("text/plain");
            assertTrue(source.handle(request("text/plain", 77)));

            assertArrayEquals("bytes of text/plain".getBytes(StandardCharsets.UTF_8), x.properties.get(77L));
            assertEquals(x.atom("text/plain"), x.propertyTypes.get(77L));
            assertEquals(List.of("text/plain:done"), x.notified);
        }

        @Test
        @DisplayName("answers TARGETS with every type and TARGETS itself, as atoms")
        void targets() {
            var source = started("text/uri-list", "text/plain");
            source.handle(request("TARGETS", 78));

            var atoms = ByteBuffer.wrap(x.properties.get(78L)).order(ByteOrder.nativeOrder());
            assertEquals(x.atom("text/uri-list"), atoms.getLong());
            assertEquals(x.atom("text/plain"), atoms.getLong());
            assertEquals(x.atom("TARGETS"), atoms.getLong());
            assertEquals(XdndSource.XA_ATOM, x.propertyTypes.get(78L));
        }

        @Test
        @DisplayName("refuses a type it does not offer, and one too large for a request")
        void refuses() {
            var source = started("text/plain");
            source.handle(request("image/png", 79));
            x.maxBytes = 4;
            source.handle(request("text/plain", 80));

            assertEquals(List.of("image/png:refused", "text/plain:refused"), x.notified);
            assertTrue(x.properties.isEmpty());
        }

        @Test
        @DisplayName("writes to the target's own name when the requestor gives no property")
        void obsoleteRequestor() {
            var source = started("text/plain");
            source.handle(request("text/plain", 0));

            assertTrue(x.properties.containsKey(x.atom("text/plain")));
        }

        @Test
        @DisplayName("leaves a request for another selection, or another window, to SDL")
        void notOurs() {
            var source = started("text/plain");
            assertFalse(source.handle(
                    new XdndEvent.SelectionRequest(SOURCE, FOREIGN, x.atom("CLIPBOARD"), x.atom("text/plain"), 1, 0)));
            assertFalse(source.handle(new XdndEvent.SelectionRequest(
                    0x999, FOREIGN, x.atom("XdndSelection"), x.atom("text/plain"), 1, 0)));
            assertFalse(source.handle(new XdndEvent.ClientMessage(0x999, x.atom("XdndStatus"), new long[5])));
        }
    }
}
