package dev.goldberry.vector.robustness;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The kinds of damage a document of the corpus is put through: what a
/// truncated download, a bit rot, a hand edit gone wrong or a hostile mail
/// makes of an SVG.
///
/// Half work on bytes and know nothing of XML. The other half know the
/// grammar's shape (numbers, path data, attributes, elements, references)
/// and damage it where a parser's assumptions live, which random bytes reach
/// only by luck. Those read the bytes as ISO-8859-1, so that one character is
/// one byte and a damaged document is still the bytes it was, plus the damage.
enum Damage {

    /// One to eight bits flipped anywhere.
    BIT_FLIPS {
        @Override
        byte[] apply(byte[] document, SplittableRandom random) {
            var copy = document.clone();
            for (var i = random.nextInt(1, 9); i > 0; i--) {
                var at = random.nextInt(copy.length);
                copy[at] ^= (byte) (1 << random.nextInt(8));
            }
            return copy;
        }
    },

    /// A run of up to 32 bytes overwritten with markup's own characters, bytes
    /// a parser treats specially, or noise.
    OVERWRITE {
        @Override
        byte[] apply(byte[] document, SplittableRandom random) {
            var copy = document.clone();
            var at = random.nextInt(copy.length);
            var length = Math.min(copy.length - at, random.nextInt(1, 33));
            for (var i = 0; i < length; i++) {
                copy[at + i] = noise(random);
            }
            return copy;
        }
    },

    /// Up to 64 bytes inserted.
    INSERT {
        @Override
        byte[] apply(byte[] document, SplittableRandom random) {
            var inserted = new byte[random.nextInt(1, 65)];
            for (var i = 0; i < inserted.length; i++) {
                inserted[i] = noise(random);
            }
            return splice(document, random.nextInt(document.length + 1), 0, inserted);
        }
    },

    /// A run of up to a quarter of the document removed.
    DELETE {
        @Override
        byte[] apply(byte[] document, SplittableRandom random) {
            var at = random.nextInt(document.length);
            var length = random.nextInt(1, Math.max(2, Math.min(document.length - at, document.length / 4) + 1));
            return splice(document, at, Math.min(length, document.length - at), new byte[0]);
        }
    },

    /// The document cut short.
    TRUNCATE {
        @Override
        byte[] apply(byte[] document, SplittableRandom random) {
            return Arrays.copyOf(document, random.nextInt(1, document.length));
        }
    },

    /// Numbers replaced with the values a number parser chokes on: zero, the
    /// ends of a float, not-a-number, the spellings that are not numbers.
    NUMBERS {
        @Override
        byte[] apply(byte[] document, SplittableRandom random) {
            return replaceSome(document, NUMBER, random, (_, r) -> pick(r, HOSTILE_NUMBERS));
        }
    },

    /// Path data replaced with a soup of commands and numbers: a command with
    /// too few arguments, arcs with zero radii, numbers run together.
    PATH_DATA {
        @Override
        byte[] apply(byte[] document, SplittableRandom random) {
            return replaceSome(document, PATH, random, (_, r) -> "d=\"" + pathSoup(r) + "\"");
        }
    },

    /// An attribute's value replaced with nothing, a keyword where a length
    /// belongs, a reference to nothing, a colour that is not one.
    ATTRIBUTES {
        @Override
        byte[] apply(byte[] document, SplittableRandom random) {
            return replaceSome(
                    document, ATTRIBUTE, random, (match, r) -> match.group(1) + "=\"" + pick(r, HOSTILE_VALUES) + "\"");
        }
    },

    /// An element duplicated, dropped, left open, or closed twice.
    ELEMENTS {
        @Override
        byte[] apply(byte[] document, SplittableRandom random) {
            var text = latin1(document);
            var tags = all(TAG, text);
            if (tags.isEmpty()) {
                return BIT_FLIPS.apply(document, random);
            }
            var tag = tags.get(random.nextInt(tags.size()));
            var body = text.substring(tag[0], tag[1]);
            var replacement = switch (random.nextInt(5)) {
                case 0 -> body + body;
                case 1 -> "";
                case 2 -> body.endsWith("/>") ? body.substring(0, body.length() - 2) + ">" : body;
                case 3 -> body + "</g></svg></defs>";
                default -> body.repeat(random.nextInt(2, 40));
            };
            return bytes(text.substring(0, tag[0]) + replacement + text.substring(tag[1]));
        }
    },

    /// Every reference pointed at one name, and that name given to several
    /// elements: a `<use>` of itself, a gradient inheriting from itself, a
    /// clip that clips with itself.
    REFERENCES {
        @Override
        byte[] apply(byte[] document, SplittableRandom random) {
            var text = latin1(document);
            var name = pick(random, List.of("a", "self", "", " ", "a b", "#"));
            text = ID.matcher(text).replaceAll(_ -> "id=\"" + Matcher.quoteReplacement(name) + "\"");
            text = HREF.matcher(text)
                    .replaceAll(match -> Matcher.quoteReplacement(match.group(1) + "=\"#" + name + "\""));
            text = URL.matcher(text).replaceAll(_ -> Matcher.quoteReplacement("url(#" + name + ")"));
            // And one element that refers to whatever holds the name, inside it.
            var tags = all(OPEN_TAG, text);
            if (!tags.isEmpty()) {
                var tag = tags.get(random.nextInt(tags.size()));
                text = text.substring(0, tag[1]) + "<use href=\"#" + name + "\"/>" + text.substring(tag[1]);
            }
            return bytes(text);
        }
    },

    /// The first part of the document followed by the last part of another:
    /// two parsers' worth of state that never belonged together.
    SPLICE {
        @Override
        byte[] apply(byte[] document, SplittableRandom random) {
            var other = Corpus.documents()
                    .get(random.nextInt(Corpus.documents().size()))
                    .bytes();
            var head = random.nextInt(1, document.length);
            var tail = random.nextInt(other.length);
            return splice(Arrays.copyOf(document, head), head, 0, Arrays.copyOfRange(other, tail, other.length));
        }
    },

    /// Two to four of the others, one after another.
    MIXED {
        @Override
        byte[] apply(byte[] document, SplittableRandom random) {
            var damaged = document;
            var kinds = values();
            for (var i = random.nextInt(2, 5); i > 0 && damaged.length > 1; i--) {
                var kind = kinds[random.nextInt(kinds.length - 1)];
                damaged = kind.apply(damaged, random);
            }
            return damaged.length == 0 ? document : damaged;
        }
    };

    /// `document` with this damage done to it, never empty: an empty document
    /// is refused before it reaches the library, and is its own test.
    abstract byte[] apply(byte[] document, SplittableRandom random);

    private static final Pattern NUMBER = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");
    private static final Pattern PATH = Pattern.compile("\\bd=\"[^\"]*\"");
    private static final Pattern ATTRIBUTE = Pattern.compile("([A-Za-z:-]+)=\"[^\"]*\"");
    private static final Pattern TAG = Pattern.compile("<[A-Za-z][^>]*>");
    private static final Pattern OPEN_TAG =
            Pattern.compile("<(?:g|svg|defs|symbol|clipPath|mask|linearGradient)\\b[^>]*[^/]>");
    private static final Pattern ID = Pattern.compile("\\bid=\"[^\"]*\"");
    private static final Pattern HREF = Pattern.compile("\\b((?:xlink:)?href)=\"[^\"]*\"");
    private static final Pattern URL = Pattern.compile("url\\(#[^)]*\\)");

    private static final List<String> HOSTILE_NUMBERS = List.of(
            "0",
            "-0",
            "1",
            "-1",
            "1e38",
            "-1e38",
            "3.5e38",
            "1e308",
            "-1e308",
            "1e309",
            "1e-45",
            "1e-320",
            "NaN",
            "nan",
            "inf",
            "-inf",
            "Infinity",
            "2147483647",
            "2147483648",
            "-2147483649",
            "4294967296",
            "99999999999999999999",
            "0.0000000001",
            "1e",
            "1e+",
            ".",
            "-",
            "+",
            "1.2.3",
            "0x10",
            "",
            "--1",
            "1e99999",
            "000000000000000000001",
            "1%",
            "%");

    private static final List<String> HOSTILE_VALUES = List.of(
            "",
            " ",
            "none",
            "inherit",
            "currentColor",
            "url(#)",
            "url(#missing)",
            "url(",
            "url(#a",
            "#",
            "#12",
            "#1234567",
            "#gggggg",
            "rgb(",
            "rgb(1,2)",
            "rgb(999,-1,1e9)",
            "rgba(1,2,3,9)",
            "hsl(1e9,2,3)",
            "-1",
            "0",
            "1e38",
            "NaN",
            "100%",
            "-100%",
            "1e9%",
            "matrix(",
            "matrix(0 0 0 0 0 0)",
            "rotate(1e38)",
            "scale(0)",
            "scale(1e38,-1e38)",
            "translate(NaN)",
            "skewX(90)",
            "skewY(-90)",
            "0 0 0 0",
            "0 0 -1 -1",
            "1e38 1e38 1e-38 1e-38",
            "xMidYMid",
            "xMaxYMax slice extra",
            "M",
            "Z",
            "evenodd",
            "1 1 1 1 1 1 1 1 1 1 1 1 1 1 1 1",
            "0.001 0.001",
            "&amp;",
            "&#0;",
            "&#x110000;",
            "\u00ff\u00fe",
            "data:",
            "data:image/png;base64,",
            "data:image/png;base64,====",
            "data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=",
            "data:image/jpeg;base64,/9j/");

    /// Markup's own characters, the bytes a parser treats specially, a stray
    /// start of a multi-byte UTF-8 sequence, and noise.
    private static byte noise(SplittableRandom random) {
        return switch (random.nextInt(4)) {
            case 0 -> (byte) "<>/=\"'&;#!?-[] \t\n".charAt(random.nextInt(17));
            case 1 ->
                new byte[] {0, 0x7F, (byte) 0x80, (byte) 0xC3, (byte) 0xE2, (byte) 0xF0, (byte) 0xFF}
                        [random.nextInt(7)];
            case 2 -> (byte) "0123456789.-eE,".charAt(random.nextInt(15));
            default -> (byte) random.nextInt(256);
        };
    }

    private static String pathSoup(SplittableRandom random) {
        var soup = new StringBuilder();
        for (var i = random.nextInt(1, 60); i > 0; i--) {
            soup.append("MmLlHhVvCcSsQqTtAaZz".charAt(random.nextInt(20)));
            for (var j = random.nextInt(0, 8); j > 0; j--) {
                soup.append(random.nextBoolean() ? " " : random.nextBoolean() ? "," : "");
                soup.append(
                        random.nextInt(4) == 0
                                ? pick(random, HOSTILE_NUMBERS)
                                : Integer.toString(random.nextInt(-200, 200)));
            }
        }
        return soup.toString();
    }

    /// `document` with one to four matches of `pattern` replaced.
    private static byte[] replaceSome(
            byte[] document, Pattern pattern, SplittableRandom random, Replacement replacement) {
        var text = latin1(document);
        var matcher = pattern.matcher(text);
        var matches = new ArrayList<int[]>();
        var chosen = new ArrayList<String>();
        while (matcher.find()) {
            matches.add(new int[] {matcher.start(), matcher.end()});
            chosen.add(replacement.of(matcher, random));
        }
        if (matches.isEmpty()) {
            return BIT_FLIPS.apply(document, random);
        }
        var out = new StringBuilder(text);
        var picks = new ArrayList<Integer>();
        for (var i = random.nextInt(1, 5); i > 0; i--) {
            picks.add(random.nextInt(matches.size()));
        }
        picks.stream().distinct().sorted(Comparator.reverseOrder()).forEach(index -> {
            var range = matches.get(index);
            out.replace(range[0], range[1], chosen.get(index));
        });
        return bytes(out.toString());
    }

    /// How a match is replaced. Called for every match in order, so that a
    /// replacement draws from the generator the same way whatever is picked.
    @FunctionalInterface
    private interface Replacement {
        String of(Matcher match, SplittableRandom random);
    }

    private static List<int[]> all(Pattern pattern, String text) {
        var matcher = pattern.matcher(text);
        var found = new ArrayList<int[]>();
        while (matcher.find()) {
            found.add(new int[] {matcher.start(), matcher.end()});
        }
        return found;
    }

    private static <T> T pick(SplittableRandom random, List<T> values) {
        return values.get(random.nextInt(values.size()));
    }

    private static byte[] splice(byte[] document, int at, int removed, byte[] inserted) {
        var out = new byte[document.length - removed + inserted.length];
        System.arraycopy(document, 0, out, 0, at);
        System.arraycopy(inserted, 0, out, at, inserted.length);
        System.arraycopy(document, at + removed, out, at + inserted.length, document.length - at - removed);
        return out;
    }

    private static String latin1(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }

    private static byte[] bytes(String latin1) {
        return latin1.getBytes(StandardCharsets.ISO_8859_1);
    }
}
