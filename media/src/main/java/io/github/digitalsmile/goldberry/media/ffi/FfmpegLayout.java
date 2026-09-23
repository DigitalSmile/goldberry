package io.github.digitalsmile.goldberry.media.ffi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.SequencedMap;

/// What the layout probe (`media/src/main/cmake/ffmpeg_layout.c`) reported about
/// the FFmpeg it was compiled against.
///
/// The superbuild runs the probe on every target and packages its output beside
/// the libraries, as `ffmpeg-layout.properties`. Four kinds of key:
///
/// | key | value |
/// |-----|-------|
/// | `version.<library>.major` | the library's major |
/// | `struct.<Name>.sizeof` / `.alignof` | a struct's size and alignment |
/// | `field.<Name>.<field>.offset` / `.sizeof` | a field's offset and size |
/// | `const.<NAME>` | a constant's value |
///
/// Parsed by hand rather than through [java.util.Properties] for two reasons. A
/// field name may contain a dot (`u.mask`), so a key is split on its *last* dot.
/// And a malformed line is an error that names the line, not a key that silently
/// goes missing.
///
/// @param majors    library stem to major
/// @param structs   struct name to size and alignment, in probe order
/// @param fields    `Struct.field` to offset and size, in probe order
/// @param constants constant name to value, in probe order
public record FfmpegLayout(
        Map<String, Integer> majors,
        SequencedMap<String, Extent> structs,
        SequencedMap<String, Extent> fields,
        SequencedMap<String, Long> constants) {

    /// The name the probe output is packaged under.
    public static final String FILE_NAME = "ffmpeg-layout.properties";

    /// Two numbers the probe reports together: a struct's size and alignment, or a
    /// field's offset and size.
    ///
    /// @param first  `sizeof` for a struct, `offset` for a field
    /// @param second `alignof` for a struct, `sizeof` for a field
    public record Extent(long first, long second) {}

    public FfmpegLayout {
        majors = Map.copyOf(majors);
        structs = unmodifiable(structs);
        fields = unmodifiable(fields);
        constants = unmodifiable(constants);
    }

    /// Reads a probe's output.
    ///
    /// @throws IOException          when `in` cannot be read
    /// @throws IllegalArgumentException when a line is not one of the four kinds,
    ///                              or a struct or field is missing half of its
    ///                              pair
    public static FfmpegLayout parse(Reader in) throws IOException {
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
            var kind = key.substring(0, Math.max(key.indexOf('.'), 0));
            var rest = key.substring(kind.length() + 1);
            switch (kind) {
                case "version" -> {
                    if (!rest.endsWith(".major")) {
                        throw malformed(number, line, "a version key ends in .major");
                    }
                    majors.put(rest.substring(0, rest.length() - ".major".length()), Math.toIntExact(value));
                }
                case "const" -> constants.put(rest, value);
                case "struct" -> half(structHalves, rest, "sizeof", "alignof", value, number, line);
                case "field" -> half(fieldHalves, rest, "offset", "sizeof", value, number, line);
                default -> throw malformed(number, line, "unknown kind '" + kind + "'");
            }
        }
        return new FfmpegLayout(majors, whole(structHalves), whole(fieldHalves), constants);
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

    /// A library's major, if the probe reported it.
    public Optional<Integer> major(FfmpegLibrary library) {
        return Optional.ofNullable(majors.get(library.stem()));
    }

    private static void half(
            Map<String, long[]> halves, String rest, String first, String second, long value, int number, String line) {
        // Split on the LAST dot: the name before it may contain dots of its own.
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
        var result = new LinkedHashMap<String, Extent>();
        halves.forEach((name, pair) -> {
            if (pair[0] < 0 || pair[1] < 0) {
                throw new IllegalArgumentException("the layout reports only half of " + name);
            }
            result.put(name, new Extent(pair[0], pair[1]));
        });
        return result;
    }

    private static IllegalArgumentException malformed(int number, String line, String why) {
        return new IllegalArgumentException(FILE_NAME + " line " + number + ": " + why + ": " + line);
    }

    private static <V> SequencedMap<String, V> unmodifiable(SequencedMap<String, V> map) {
        Objects.requireNonNull(map, "map");
        return Collections.unmodifiableSequencedMap(new LinkedHashMap<>(map));
    }
}
