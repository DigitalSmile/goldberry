package dev.goldberry.text.mark;

import java.net.URI;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// What a [Mark] says about the text under it: bold, a link, a mention.
///
/// ```java
/// MarkKind bold = MarkKind.Style.BOLD;
/// MarkKind link = new MarkKind.Link(URI.create("https://goldberry.dev"));
/// MarkKind ann  = new MarkKind.Mention("user:42");
/// MarkKind tag  = new MarkKind.Custom("hashtag", "goldberry", MarkKind.Behaviour.REFERENCE);
/// ```
///
/// Sealed, so an editor's `switch` over the kinds is exhaustive. The six
/// [Style]s, a [Link] and a [Mention] are what a message composer formats with;
/// [Custom] is everything an application adds of its own, a hashtag or a
/// command, with the [Behaviour] it needs spelled out.
///
/// A kind says **how it behaves while text is edited**, which is what an editor
/// needs and a stylesheet cannot say. How it **looks** is the stylesheet's:
/// every kind has a [#className()], and `rich-input mark.bold` styles it.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
public sealed interface MarkKind {

    /// How a kind behaves while the text under it is edited.
    ///
    /// @param growsAtEnd whether text typed where the mark ends joins it: type
    ///                   after a bold word and the letters are bold, type after a
    ///                   link and they are not
    /// @param atomic     whether the marked text is one unit: the caret steps over
    ///                   it, a deletion that touches it removes all of it, and
    ///                   nothing can be typed inside it. A mention is
    /// @param exclusive  whether no other kind may overlap it. Every atomic kind
    ///                   is exclusive
    record Behaviour(boolean growsAtEnd, boolean atomic, boolean exclusive) {

        /// Formatting: grows when typed at its end, overlaps other formatting.
        public static final Behaviour STYLE = new Behaviour(true, false, false);

        /// A reference over ordinary text, such as a link: does not grow, and
        /// can be edited inside.
        public static final Behaviour REFERENCE = new Behaviour(false, false, false);

        /// An atom, such as a mention: one unit, alone.
        public static final Behaviour ATOM = new Behaviour(false, true, true);

        public Behaviour {
            if (atomic && !exclusive) {
                throw new IllegalArgumentException("an atom is one unit, so no other kind may overlap it:"
                        + " an atomic behaviour is exclusive too");
            }
        }
    }

    /// How text under this kind behaves while it is edited.
    Behaviour behaviour();

    /// The class the stylesheet styles this kind by: `bold`, `link`,
    /// `mention`, or a custom kind's name.
    String className();

    /// Whether this kind is one unit the caret cannot enter.
    default boolean isAtomic() {
        return behaviour().atomic();
    }

    /// The formatting a composer toggles.
    enum Style implements MarkKind {

        /// Heavier type. `Ctrl+B`.
        BOLD,

        /// Slanted type. `Ctrl+I`.
        ITALIC,

        /// A rule under the text. `Ctrl+U`.
        UNDERLINE,

        /// A rule through it. `Ctrl+Shift+X`.
        STRIKE,

        /// Code, in the stylesheet's monospace. Overlaps nothing but a spoiler,
        /// as a messenger's inline code does.
        CODE,

        /// Covered until the reader reveals it.
        SPOILER;

        @Override
        public Behaviour behaviour() {
            return Behaviour.STYLE;
        }

        @Override
        public String className() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /// A link to `uri`. A reference: edited like ordinary text, and typing
    /// after it does not extend it.
    record Link(URI uri) implements MarkKind {

        public Link {
            Objects.requireNonNull(uri, "uri");
        }

        @Override
        public Behaviour behaviour() {
            return Behaviour.REFERENCE;
        }

        @Override
        public String className() {
            return "link";
        }
    }

    /// A mention of whatever `id` names to the application: a person, a room.
    /// An atom: the text it was inserted with stays whole or goes whole.
    record Mention(String id) implements MarkKind {

        public Mention {
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("a mention names somebody, and a blank id names nobody");
            }
        }

        @Override
        public Behaviour behaviour() {
            return Behaviour.ATOM;
        }

        @Override
        public String className() {
            return "mention";
        }
    }

    /// A kind of the application's own: a hashtag, a command, a custom emoji.
    ///
    /// @param name      the class it is styled by, a lowercase CSS identifier, and
    ///                  none of the names the built-in kinds use
    /// @param value     what it carries, such as the tag; may be empty
    /// @param behaviour how it behaves while edited
    record Custom(String name, String value, Behaviour behaviour) implements MarkKind {

        private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]*");

        private static final Set<String> RESERVED =
                Set.of("bold", "italic", "underline", "strike", "code", "spoiler", "link", "mention");

        public Custom {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(behaviour, "behaviour");
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("a custom mark's name is the class it is styled by, so it is"
                        + " a lowercase identifier such as `hashtag`, and `" + name + "` is not");
            }
            if (RESERVED.contains(name)) {
                throw new IllegalArgumentException(
                        "`" + name + "` is a built-in kind's class; a custom kind needs a name of its own");
            }
        }

        @Override
        public String className() {
            return name;
        }
    }

    /// Whether `a` and `b` may not cover the same text.
    ///
    /// Two marks of one kind with one value never conflict: they merge. Anything
    /// overlapping an atom conflicts, another atom included, and so does anything
    /// overlapping an exclusive kind. Code overlaps nothing but a spoiler. Two
    /// links to different addresses conflict, and so do two custom kinds of one
    /// name with different values: a word goes to one place.
    static boolean conflict(MarkKind a, MarkKind b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.isAtomic() || b.isAtomic()) {
            return true;
        }
        if (a.equals(b)) {
            return false;
        }
        if (a.behaviour().exclusive() || b.behaviour().exclusive()) {
            return true;
        }
        if (a == Style.CODE || b == Style.CODE) {
            // One of them is code and the other is not.
            return a != Style.SPOILER && b != Style.SPOILER;
        }
        return switch (a) {
            case Style _ -> false;
            case Link _ -> b instanceof Link;
            case Custom(var name, var _, var _) ->
                b instanceof Custom other && other.name().equals(name);
            case Mention _ -> true;
        };
    }

    /// The order marks of one range are listed in: the styles in declaration
    /// order, then links, mentions and custom kinds, each by its value. Only
    /// there to make a list of marks one canonical list.
    Comparator<MarkKind> ORDER = Comparator.comparingInt(MarkKind::rank).thenComparing(MarkKind::orderKey);

    private static int rank(MarkKind kind) {
        return switch (kind) {
            case Style style ->
                switch (style) {
                    case BOLD -> 0;
                    case ITALIC -> 1;
                    case UNDERLINE -> 2;
                    case STRIKE -> 3;
                    case CODE -> 4;
                    case SPOILER -> 5;
                };
            case Link _ -> 100;
            case Mention _ -> 101;
            case Custom _ -> 102;
        };
    }

    private static String orderKey(MarkKind kind) {
        return switch (kind) {
            case Style _ -> "";
            case Link(var uri) -> uri.toString();
            case Mention(var id) -> id;
            case Custom(var name, var value, var _) -> name + '\0' + value;
        };
    }
}
