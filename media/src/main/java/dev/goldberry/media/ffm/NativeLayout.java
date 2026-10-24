package dev.goldberry.media.ffm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.SequencedMap;

/// What the layout probe (`media_layout.c`) reported about the
/// `libgoldberry-media` actually built: the ABI number, each upstream's API
/// major, the size and alignment of every struct the Java side touches, the
/// offset and size of every field it reads, and the constants it hard-codes.
///
/// Packaged beside the library as `media-layout.properties`, one `key=value` a
/// line:
///
/// ```
/// abi=1
/// version.dav1d.api.major=7
/// struct.Dav1dPicture.sizeof=272
/// struct.Dav1dPicture.alignof=8
/// field.Dav1dPicture.stride.offset=40
/// field.Dav1dPicture.stride.sizeof=16
/// const.DAV1D_PIXEL_LAYOUT_I420=1
/// ```
///
/// The maps keep the probe's order, so a message that lists them reads as the
/// probe wrote them.
///
/// @param abi       the library's ABI number, or empty when the file has none
/// @param majors    each upstream's API major, by upstream name
/// @param structs   each struct's size and alignment
/// @param fields    each field's offset and size, keyed `Struct.field`
/// @param constants each constant's value
public record NativeLayout(
        OptionalInt abi,
        SequencedMap<String, Integer> majors,
        SequencedMap<String, Extent> structs,
        SequencedMap<String, Extent> fields,
        SequencedMap<String, Long> constants) {

    /// The file's name, beside the library.
    public static final String FILE_NAME = "media-layout.properties";

    /// A pair of sizes: `sizeof` and `alignof` for a struct, `offset` and
    /// `sizeof` for a field.
    ///
    /// @param first  the struct's size, or the field's offset
    /// @param second the struct's alignment, or the field's size
    public record Extent(long first, long second) {}

    public NativeLayout {
        majors = unmodifiable(majors);
        structs = unmodifiable(structs);
        fields = unmodifiable(fields);
        constants = unmodifiable(constants);
    }

    /// Reads a layout file.
    ///
    /// @throws IOException              when `in` fails
    /// @throws IllegalArgumentException when a line is not one of the five kinds,
    ///                                  or a struct or field is missing half of its
    ///                                  pair
    public static NativeLayout parse(Reader in) throws IOException {
        var abi = OptionalInt.empty();
        var majors = new LinkedHashMap<String, Integer>();
        var structHalves = new LinkedHashMap<String, long[]>();
        var fieldHalves = new LinkedHashMap<String, long[]>();
        var constants = new LinkedHashMap<String, Long>();

        var reader = new BufferedReader(in);
        var number = 0;
        for (var line = reader.readLine(); line != null; line = reader.readLine()) {
            number++;
            var text = line.strip();
            if (text.isEmpty() || text.startsWith("#")) {
                continue;
            }
            var equals = text.indexOf('=');
            if (equals <= 0) {
                throw malformed(number, line, "no key=value");
            }
            var key = text.substring(0, equals);
            long value;
            try {
                value = Long.parseLong(text.substring(equals + 1).strip());
            } catch (NumberFormatException e) {
                throw malformed(number, line, "the value is not a number");
            }
            if (key.equals("abi")) {
                abi = OptionalInt.of(Math.toIntExact(value));
                continue;
            }
            var kind = key.substring(0, Math.max(key.indexOf('.'), 0));
            var rest = key.substring(kind.length() + 1);
            switch (kind) {
                case "version" -> {
                    if (!rest.endsWith(".api.major")) {
                        throw malformed(number, line, "a version key ends in .api.major");
                    }
                    majors.put(rest.substring(0, rest.length() - ".api.major".length()), Math.toIntExact(value));
                }
                case "const" -> constants.put(rest, value);
                case "struct" -> half(structHalves, rest, "sizeof", "alignof", value, number, line);
                case "field" -> half(fieldHalves, rest, "offset", "sizeof", value, number, line);
                default -> throw malformed(number, line, "unknown kind '" + kind + "'");
            }
        }
        return new NativeLayout(abi, majors, whole(structHalves), whole(fieldHalves), constants);
    }

    /// A struct's size and alignment, if the probe reported it.
    public Optional<Extent> struct(String name) {
        return Optional.ofNullable(structs.get(name));
    }

    /// A field's offset and size, if the probe reported it.
    public Optional<Extent> field(String struct, String field) {
        return Optional.ofNullable(fields.get(struct + "." + field));
    }

    /// A constant's value, if the probe reported it.
    public OptionalLong constant(String name) {
        var value = constants.get(name);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    /// An upstream's API major, if the probe reported it.
    public OptionalInt major(String upstream) {
        var value = majors.get(upstream);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    /// A constant the bindings cannot do without.
    ///
    /// @throws IllegalArgumentException when the probe did not report it
    public long require(String name) {
        var value = constants.get(name);
        if (value == null) {
            throw new IllegalArgumentException("the layout file does not report const." + name
                    + "; was it written by this version's media_layout.c?");
        }
        return value;
    }

    private static void half(
            Map<String, long[]> halves, String rest, String first, String second, long value, int number, String line) {
        var dot = rest.lastIndexOf('.');
        if (dot <= 0) {
            throw malformed(number, line, "expected <name>." + first + " or <name>." + second);
        }
        var name = rest.substring(0, dot);
        var which = rest.substring(dot + 1);
        var pair = halves.computeIfAbsent(name, _ -> new long[] {-1, -1});
        if (which.equals(first)) {
            pair[0] = value;
        } else if (which.equals(second)) {
            pair[1] = value;
        } else {
            throw malformed(number, line, "expected ." + first + " or ." + second + ", not ." + which);
        }
    }

    private static SequencedMap<String, Extent> whole(Map<String, long[]> halves) {
        var map = new LinkedHashMap<String, Extent>();
        halves.forEach((name, pair) -> {
            if (pair[0] < 0 || pair[1] < 0) {
                throw new IllegalArgumentException(name + " has only half of its pair in the layout file");
            }
            map.put(name, new Extent(pair[0], pair[1]));
        });
        return map;
    }

    private static IllegalArgumentException malformed(int number, String line, String why) {
        return new IllegalArgumentException("layout file line " + number + ": " + why + ": " + line);
    }

    private static <V> SequencedMap<String, V> unmodifiable(SequencedMap<String, V> map) {
        return Collections.unmodifiableSequencedMap(new LinkedHashMap<>(map));
    }
}
