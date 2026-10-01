package dev.goldberry.media.platform.macos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.media.codec.Frame;

@DisplayName("ReorderBuffer")
class ReorderBufferTest {

    /// A picture: its name and presentation time.
    private record Picture(String name, long pts) {}

    private static ReorderBuffer<Picture> buffer(int depth) {
        return new ReorderBuffer<>(depth, Picture::pts);
    }

    /// Adds `pictures` in decoding order, polling after each as the decoder does,
    /// then drains.
    private static List<String> run(ReorderBuffer<Picture> buffer, List<Picture> pictures) {
        var out = new ArrayList<String>();
        for (var picture : pictures) {
            buffer.add(picture);
            for (var next = buffer.poll(); next != null; next = buffer.poll()) {
                out.add(next.name());
            }
        }
        for (var next = buffer.drain(); next != null; next = buffer.drain()) {
            out.add(next.name());
        }
        return out;
    }

    @Test
    @DisplayName("a B-pyramid in decoding order comes out in presentation order")
    void pyramid() {
        // I0 P4 B2 b1 b3 P8 B6 b5 b7: the decoding order of bframes=3 with a pyramid.
        var decodingOrder = List.of(0, 4, 2, 1, 3, 8, 6, 5, 7).stream()
                .map(i -> new Picture("p" + i, i * 40L))
                .toList();
        assertEquals(List.of("p0", "p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8"), run(buffer(2), decodingOrder));
    }

    @Test
    @DisplayName("nothing leaves until more than the depth is held")
    void holdsTheDepth() {
        var buffer = buffer(2);
        buffer.add(new Picture("a", 0));
        buffer.add(new Picture("b", 80));
        assertNull(buffer.poll());
        assertEquals(2, buffer.size());
        buffer.add(new Picture("c", 40));
        assertEquals("a", buffer.poll().name());
        assertNull(buffer.poll());
    }

    @Test
    @DisplayName("depth 0 passes pictures straight through")
    void depthZero() {
        var pictures = List.of(new Picture("a", 0), new Picture("b", 40));
        assertEquals(List.of("a", "b"), run(buffer(0), pictures));
    }

    @Test
    @DisplayName("a picture with no time keeps its decoding position, and ties keep decoding order")
    void noTimeAndTies() {
        var pictures = List.of(
                new Picture("a", 0),
                new Picture("b", Frame.NO_PTS),
                new Picture("c", 40),
                new Picture("d", 40),
                new Picture("e", Frame.NO_PTS));
        assertEquals(List.of("a", "b", "c", "d", "e"), run(buffer(1), pictures));
    }

    @Test
    @DisplayName("clear hands back everything held, to be freed, and starts over")
    void clear() {
        var buffer = buffer(3);
        buffer.add(new Picture("a", 40));
        buffer.add(new Picture("b", 0));
        assertEquals(2, buffer.clear().size());
        assertEquals(0, buffer.size());
        assertNull(buffer.drain());
        buffer.add(new Picture("c", Frame.NO_PTS));
        assertEquals("c", buffer.drain().name());
    }

    @Test
    @DisplayName("a negative depth is refused")
    void negativeDepth() {
        assertThrows(IllegalArgumentException.class, () -> buffer(-1));
    }
}
