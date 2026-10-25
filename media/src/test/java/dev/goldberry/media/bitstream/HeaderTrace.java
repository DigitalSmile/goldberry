package dev.goldberry.media.bitstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/// FFmpeg's `trace_headers` of a fixture, as `make-traces.sh` wrote it: the
/// headings of the units it parsed, each with the syntax elements read from it,
/// in order. What the bitstream readers are held to, element by element.
public final class HeaderTrace {

    private static final Pattern ELEMENT = Pattern.compile("^(\\d+)\\s+(\\S+)\\s+\\S*\\s*=\\s*(-?\\d+)$");

    /// One element as FFmpeg read it.
    ///
    /// @param bit   its bit position in the unit
    /// @param name  its name, with array indices as FFmpeg writes them
    /// @param value its value
    public record Element(long bit, String name, long value) {}

    /// One unit FFmpeg read: its heading, and each element's bit position and
    /// value, by name, with array indices as FFmpeg writes them (`flag[0]`). A
    /// name read twice keeps its first value and position in the maps, and
    /// every reading in `elements`.
    ///
    /// @param heading  the line that opened it, as `Sequence Parameter Set`
    /// @param values   each element's value
    /// @param bits     each element's bit position in the unit
    /// @param elements every element, in the order read
    public record Section(String heading, Map<String, Long> values, Map<String, Long> bits, List<Element> elements) {

        /// The value of `name`, which must have been read.
        public long value(String name) {
            var value = values.get(name);
            if (value == null) {
                throw new AssertionError(heading + " has no " + name + "; it has " + values.keySet());
            }
            return value;
        }

        /// The value of `name`, if it was read.
        public OptionalLong find(String name) {
            var value = values.get(name);
            return value == null ? OptionalLong.empty() : OptionalLong.of(value);
        }

        /// The bit position `name` was read at.
        public long bit(String name) {
            var at = bits.get(name);
            if (at == null) {
                throw new AssertionError(heading + " has no " + name);
            }
            return at;
        }
    }

    private final List<Section> sections;
    private final List<Section> every;

    private HeaderTrace(List<Section> every) {
        this.every = List.copyOf(every);
        this.sections = every.stream().filter(s -> !s.values().isEmpty()).toList();
    }

    /// The trace `name`, from `/dev/goldberry/media/bitstream/`.
    public static HeaderTrace load(String name) {
        try (InputStream in = HeaderTrace.class.getResourceAsStream("/dev/goldberry/media/bitstream/" + name)) {
            Objects.requireNonNull(in, name);
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static HeaderTrace parse(String text) {
        var sections = new ArrayList<Section>();
        String heading = null;
        Map<String, Long> values = null;
        Map<String, Long> bits = null;
        List<Element> elements = null;
        for (var line : text.lines().toList()) {
            var element = ELEMENT.matcher(line.strip());
            if (element.matches()) {
                if (values != null) {
                    var name = element.group(2);
                    var value = Long.parseLong(element.group(3));
                    var bit = Long.parseLong(element.group(1));
                    values.putIfAbsent(name, value);
                    bits.putIfAbsent(name, bit);
                    elements.add(new Element(bit, name, value));
                }
                continue;
            }
            if (heading != null) {
                sections.add(new Section(heading, values, bits, List.copyOf(elements)));
            }
            heading = line.strip();
            values = new LinkedHashMap<>();
            bits = new LinkedHashMap<>();
            elements = new ArrayList<>();
        }
        if (heading != null) {
            sections.add(new Section(heading, values, bits, List.copyOf(elements)));
        }
        return new HeaderTrace(sections);
    }

    /// Every section, in order, with the headings FFmpeg printed with no
    /// element after them: `Temporal Delimiter`, a packet's size, a summary.
    public List<Section> sections() {
        return every;
    }

    /// Every section headed `heading`, in order.
    public List<Section> all(String heading) {
        return sections.stream().filter(s -> s.heading().equals(heading)).toList();
    }

    /// The first section headed `heading`.
    public Section first(String heading) {
        return all(heading).stream().findFirst().orElseThrow(() -> new AssertionError("no " + heading));
    }
}
