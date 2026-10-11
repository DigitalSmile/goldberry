package dev.goldberry.text.font.sfnt;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// The axes a variable face can be set along, read from its `fvar` table:
/// `wght` from 100 to 900, `wdth` from 75 to 100, and whatever else the
/// designer gave it.
///
/// ```java
/// List<VariationAxes.Axis> axes = VariationAxes.of(bytes);   // empty for a static face
/// ```
///
/// What the font book asks before it pins a face to a weight or a width: an
/// axis the face does not have is not written, and a value past an axis's
/// range is brought back inside it.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#faces-fonts-and-the-book).
public final class VariationAxes {

    private static final int FVAR = TableDirectory.tag('f', 'v', 'a', 'r');

    /// The size of one axis record, which `fvar` states and which version 1
    /// fixes at twenty bytes: a tag, three positions and two shorts.
    private static final int AXIS_RECORD = 20;

    private VariationAxes() {}

    /// One axis.
    ///
    /// @param tag          the four-letter tag, `wght`
    /// @param min          the lowest position the face draws
    /// @param defaultValue the position the face draws when nothing is set
    /// @param max          the highest position the face draws
    public record Axis(String tag, double min, double defaultValue, double max) {

        public Axis {
            Objects.requireNonNull(tag, "tag");
            if (!(min <= defaultValue && defaultValue <= max)) {
                throw new IllegalArgumentException(
                        "an axis runs from its minimum through its default to its maximum, not " + min + ", "
                                + defaultValue + ", " + max);
            }
        }

        /// `value` brought inside this axis's range.
        public double clamp(double value) {
            return Math.clamp(value, min, max);
        }
    }

    /// The axes of the face in `font`, in the order the face lists them; empty
    /// for a static face, a collection, or a table that cannot be read.
    public static List<Axis> of(byte[] font) {
        Objects.requireNonNull(font, "font");
        var table = TableDirectory.table(font, FVAR);
        if (table == null) {
            return List.of();
        }
        try {
            return read(table);
        } catch (RuntimeException e) {
            // A truncated table is a static face as far as anyone here can
            // tell: nothing is pinned, and the face draws as it is.
            return List.of();
        }
    }

    /// The axis tagged `tag` in `axes`, if there is one.
    public static Optional<Axis> find(List<Axis> axes, String tag) {
        return axes.stream().filter(axis -> axis.tag().equals(tag)).findFirst();
    }

    private static List<Axis> read(ByteBuffer table) {
        var offset = Short.toUnsignedInt(table.getShort(4));
        var count = Short.toUnsignedInt(table.getShort(8));
        var size = Short.toUnsignedInt(table.getShort(10));
        if (size < AXIS_RECORD) {
            return List.of();
        }
        var axes = new ArrayList<Axis>(count);
        for (var i = 0; i < count; i++) {
            var at = offset + i * size;
            var tag = table.getInt(at);
            var min = fixed(table.getInt(at + 4));
            var defaultValue = fixed(table.getInt(at + 8));
            var max = fixed(table.getInt(at + 12));
            if (min <= defaultValue && defaultValue <= max) {
                axes.add(new Axis(tagName(tag), min, defaultValue, max));
            }
        }
        return List.copyOf(axes);
    }

    /// A 16.16 fixed-point number.
    private static double fixed(int raw) {
        return raw / 65536.0;
    }

    private static String tagName(int tag) {
        return new String(new char[] {
            (char) (tag >>> 24), (char) ((tag >>> 16) & 0xFF), (char) ((tag >>> 8) & 0xFF), (char) (tag & 0xFF)
        });
    }
}
