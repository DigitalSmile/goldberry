package dev.goldberry.example.ui.richinput;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkedText;

/// Marked text as a messenger stores it: the plain words, and an entity per
/// mark, an offset and a length in UTF-16 code units and a type. The offsets
/// are a mark's own, because a Java `String` is indexed in the same units.
public final class Entities {

    /// One entity.
    ///
    /// @param offset where it starts, in UTF-16 code units
    /// @param length how many code units it covers
    /// @param type   what it is, in a messenger's words
    /// @param value  what it carries, such as an address; empty for a style
    public record Entity(int offset, int length, String type, String value) {

        public Entity {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(value, "value");
        }

        @Override
        public String toString() {
            return offset + "+" + length + " " + type + (value.isEmpty() ? "" : " " + value);
        }
    }

    private Entities() {}

    /// `text`'s marks as entities, in its order.
    public static List<Entity> of(MarkedText text) {
        return text.marks().stream().map(Entities::of).toList();
    }

    /// One mark as an entity.
    static Entity of(Mark mark) {
        var length = mark.end() - mark.start();
        return switch (mark.kind()) {
            case MarkKind.Style style ->
                new Entity(
                        mark.start(),
                        length,
                        switch (style) {
                            case BOLD -> "bold";
                            case ITALIC -> "italic";
                            case UNDERLINE -> "underline";
                            case STRIKE -> "strikethrough";
                            case CODE -> "code";
                            case SPOILER -> "spoiler";
                        },
                        "");
            case MarkKind.Link(var uri) -> new Entity(mark.start(), length, "text_link", uri.toString());
            case MarkKind.Mention(var id) -> new Entity(mark.start(), length, "mention_name", id);
            case MarkKind.Custom(var name, var value, var _) -> new Entity(mark.start(), length, name, value);
        };
    }

    /// `text`'s entities, one a line.
    public static String describe(MarkedText text) {
        var entities = of(text);
        return entities.isEmpty()
                ? "(no entities)"
                : entities.stream().map(Entity::toString).collect(Collectors.joining("\n"));
    }
}
