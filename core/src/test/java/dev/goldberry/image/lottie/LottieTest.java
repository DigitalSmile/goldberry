package dev.goldberry.image.lottie;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.image.ImageDecodeException;

/// The parts of the Lottie reader that are arithmetic: the JSON underneath, the
/// easing curve, keyframes, and the trim, each checked by number rather than by
/// picture.
class LottieTest {

    static byte[] fixture(String name) {
        try (var in = LottieTest.class.getResourceAsStream(name)) {
            return Objects.requireNonNull(in, name).readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Composition read(String name) {
        return LottieReader.read(ByteBuffer.wrap(fixture(name)));
    }

    @Nested
    @DisplayName("the JSON underneath")
    class Reading {

        @Test
        @DisplayName("reads the six kinds of value")
        void values() {
            var value = Json.parse("{\"a\": [1, -2.5e2, true, false, null], \"b\": \"x\\u00e9\\n\"}");
            var object = assertInstanceOf(Json.Obj.class, value);
            var list = assertInstanceOf(Json.Arr.class, object.get("a"));
            assertEquals(
                    List.of(
                            new Json.Num(1),
                            new Json.Num(-250),
                            new Json.Bool(true),
                            new Json.Bool(false),
                            new Json.Null()),
                    list.items());
            assertEquals(new Json.Str("xé\n"), object.get("b"));
        }

        @Test
        @DisplayName("refuses what is not JSON rather than guessing")
        void strict() {
            for (var text : List.of("{\"a\":1,}", "[1 2]", "{'a':1}", "01", "[1]x", "\"open", "{\"a\" 1}", "-")) {
                assertThrows(Json.JsonException.class, () -> Json.parse(text), text);
            }
        }

        @Test
        @DisplayName("refuses nesting deeper than its bound instead of overflowing the stack")
        void deep() {
            var text = "[".repeat(Json.MAX_DEPTH + 2) + "]".repeat(Json.MAX_DEPTH + 2);
            assertThrows(Json.JsonException.class, () -> Json.parse(text));
        }
    }

    @Nested
    @DisplayName("easing")
    class Eased {

        @Test
        @DisplayName("a linear curve is the identity")
        void linear() {
            for (var x = 0.0; x <= 1; x += 0.125) {
                assertEquals(x, Easing.LINEAR.apply(x), 1e-12);
            }
        }

        /// CSS's `ease-in-out`, whose values a browser reports. Read as if the
        /// parameter were the time, 0.25 would come out at 0.15625; solved for
        /// the time, it is 0.129.
        @Test
        @DisplayName("ease-in-out is solved for x before y is read")
        void easeInOut() {
            var curve = new Easing(0.42, 0, 0.58, 1);
            assertEquals(0.5, curve.apply(0.5), 1e-6, "symmetric about the middle");
            assertEquals(0.129, curve.apply(0.25), 1e-3);
            assertEquals(0.871, curve.apply(0.75), 1e-3);
            assertEquals(0, curve.apply(0), 0);
            assertEquals(1, curve.apply(1), 0);
        }

        @Test
        @DisplayName("the solved parameter really lands on x, even where the curve is flat")
        void solvesFlatCurves() {
            var curve = new Easing(1, 0, 1, 1);
            for (var x = 0.05; x < 1; x += 0.05) {
                var t = curve.solve(x);
                var u = 1 - t;
                var back = 3 * u * u * t * 1 + 3 * u * t * t * 1 + t * t * t;
                assertEquals(x, back, 1e-6);
            }
        }

        @Test
        @DisplayName("an overshooting curve overshoots")
        void overshoot() {
            var back = new Easing(0.34, 1.56, 0.64, 1);
            var peak = 0.0;
            for (var x = 0.0; x <= 1; x += 0.01) {
                peak = Math.max(peak, back.apply(x));
            }
            assertTrue(peak > 1.05, "rises past its end: " + peak);
        }
    }

    @Nested
    @DisplayName("keyframes")
    class Keyframes {

        @Test
        @DisplayName("a held keyframe jumps, and an eased one is eased")
        void holdThenEase() {
            var composition = read("hold-and-ease.json");
            var position = Objects.requireNonNull(
                    composition.layers().getFirst().transform().position());
            assertArrayEquals(new double[] {20, 50}, position.at(0), 1e-9);
            assertArrayEquals(new double[] {20, 50}, position.at(9.99), 1e-9, "held until the next key");
            assertArrayEquals(new double[] {20, 80}, position.at(10), 1e-9, "and there at once");
            assertArrayEquals(new double[] {50, 80}, position.at(20), 1e-6, "the middle of a symmetric ease");
            var quarter = position.at(15)[0];
            assertEquals(20 + 60 * 0.129, quarter, 0.1, "a quarter of the way in time is 13% of the way");
            assertArrayEquals(new double[] {80, 80}, position.at(30), 1e-9);
            assertArrayEquals(new double[] {80, 80}, position.at(100), 1e-9, "rests at the last value");
        }

        @Test
        @DisplayName("an old-shape keyframe list with end values reads the same")
        void oldShape() {
            var property = property("""
                    {"a":1,"k":[{"t":0,"s":[0],"e":[10],"o":{"x":[0],"y":[0]},"i":{"x":[1],"y":[1]}},{"t":10}]}""");
            assertEquals(5, property.scalar(5), 1e-9);
            assertEquals(10, property.scalar(20), 1e-9);
        }

        @Test
        @DisplayName("a position with tangents travels along its curve at even speed")
        void spatial() {
            // A quarter circle's worth of curve from (0,0) to (100,100).
            var property = property("""
                    {"a":1,"k":[{"t":0,"s":[0,0],"to":[55,0],"ti":[0,-55],
                      "o":{"x":0,"y":0},"i":{"x":1,"y":1}},{"t":10,"s":[100,100]}]}""");
            var middle = property.at(5);
            // Off the straight line, near the arc's midpoint.
            assertTrue(middle[0] > 60 && middle[1] < 40, "bulges: " + middle[0] + "," + middle[1]);
            assertEquals(middle[0], 100 - middle[1], 1.5, "and halfway along it, by symmetry");
        }

        private static Property property(String json) {
            var shape = "{\"v\":\"5\",\"fr\":30,\"ip\":0,\"op\":30,\"w\":10,\"h\":10,\"layers\":[{\"ty\":3,\"ind\":1,"
                    + "\"ks\":{\"p\":" + json + "}}]}";
            var composition = LottieReader.read(ByteBuffer.wrap(shape.getBytes(StandardCharsets.UTF_8)));
            return Objects.requireNonNull(
                    composition.layers().getFirst().transform().position());
        }

        @Test
        @DisplayName("a path changes shape vertex by vertex")
        void shapeKeyframes() {
            var start = new Bezier(new double[] {0, 0, 10, 0}, new double[4], new double[4], false);
            var end = new Bezier(new double[] {0, 10, 10, 20}, new double[4], new double[4], false);
            var path = new ShapeProperty.Keyed(List.of(
                    new ShapeProperty.Key(0, start, end, false, Easing.LINEAR),
                    new ShapeProperty.Key(10, end, end, false, Easing.LINEAR)));
            var halfway = path.at(5);
            assertEquals(5, halfway.y(0), 1e-9);
            assertEquals(10, halfway.y(1), 1e-9);
        }
    }

    @Nested
    @DisplayName("trims")
    class Trims {

        private static Contour line() {
            return Contour.at(0, 0).lineTo(100, 0);
        }

        @Test
        @DisplayName("keep the stretch between start and end")
        void range() {
            var kept = Trimming.each(List.of(line()), 0.25, 0.5, 0);
            assertEquals(1, kept.size());
            var lengths = kept.getFirst().segmentLengths();
            assertEquals(25, lengths[0], 0.1);
        }

        @Test
        @DisplayName("a whole range keeps the path, and an empty one keeps nothing")
        void wholeAndEmpty() {
            var path = List.of(line());
            assertEquals(path, Trimming.each(path, 0, 1, 0));
            assertTrue(Trimming.each(path, 0.4, 0.4, 0).isEmpty());
            assertNull(Trimming.range(0, 1, 0.3), "a whole range, however offset, is everything");
        }

        @Test
        @DisplayName("wraps past the end of a closed path as one run")
        void wraps() {
            var square = Contour.at(0, 0)
                    .lineTo(100, 0)
                    .lineTo(100, 100)
                    .lineTo(0, 100)
                    .lineTo(0, 0)
                    .close();
            var kept = Trimming.each(List.of(square), 0.875, 1, 0.25);
            assertEquals(1, kept.size(), "the two pieces either side of the start are joined");
            var total = 0.0;
            for (var length : kept.getFirst().segmentLengths()) {
                total += length;
            }
            assertEquals(50, total, 0.01);
        }

        @Test
        @DisplayName("laid end to end, each path keeps its own share")
        void together() {
            var range = Objects.requireNonNull(Trimming.range(0.25, 0.75, 0));
            var shares = Trimming.together(List.of(line(), line()), range[0], range[1]);
            assertEquals(2, shares.size());
            assertEquals(50, shares.get(0).getFirst().segmentLengths()[0], 0.01);
            assertEquals(50, shares.get(1).getFirst().segmentLengths()[0], 0.01);
        }
    }

    @Nested
    @DisplayName("documents")
    class Documents {

        @Test
        @DisplayName("a tgs is the same document gzipped")
        void gzipped() {
            var plain = read("static-rect.json");
            var packed = read("static-rect.tgs");
            assertEquals(plain.width(), packed.width());
            assertEquals(plain.layers().size(), packed.layers().size());
        }

        @Test
        @DisplayName("an expression is refused and says so")
        void expression() {
            var thrown = assertThrows(ImageDecodeException.class, () -> read("expression.json"));
            assertTrue(thrown.getMessage().contains("expression"), thrown.getMessage());
        }

        @Test
        @DisplayName("an image layer is refused and says so")
        void image() {
            var thrown = assertThrows(ImageDecodeException.class, () -> read("image.json"));
            assertTrue(thrown.getMessage().contains("image layer"), thrown.getMessage());
        }

        @Test
        @DisplayName("bytes that are not a Lottie document are a decode failure")
        void notLottie() {
            for (var text : List.of("not json", "[1,2]", "{\"w\":10,\"h\":10,\"fr\":30,\"op\":10}", "{}")) {
                assertThrows(
                        ImageDecodeException.class,
                        () -> LottieReader.read(ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8))),
                        text);
            }
        }

        @Test
        @DisplayName("an unknown shape type is passed over and the rest is read")
        void unknownShape() {
            var text = """
                    {"v":"5","fr":30,"ip":0,"op":30,"w":10,"h":10,"layers":[{"ty":4,"ind":1,"ks":{},"shapes":[
                      {"ty":"rp","c":{"a":0,"k":3}},{"ty":"mm","mm":1},
                      {"ty":"rc","p":{"a":0,"k":[5,5]},"s":{"a":0,"k":[4,4]},"r":{"a":0,"k":0}}]},
                      {"ty":5,"ind":2,"ks":{},"t":{}}]}""";
            var composition = LottieReader.read(ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8)));
            var shapes = assertInstanceOf(
                    Layer.Shapes.class, composition.layers().getFirst().content());
            assertEquals(1, shapes.items().size(), "the repeater and the merge are passed over");
            assertInstanceOf(Layer.Nothing.class, composition.layers().get(1).content(), "a text layer draws nothing");
        }
    }
}
