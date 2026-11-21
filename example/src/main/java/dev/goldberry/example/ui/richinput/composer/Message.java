package dev.goldberry.example.ui.richinput.composer;

import java.util.Objects;

import dev.goldberry.text.mark.MarkedText;

/// One message in the composer's conversation.
///
/// @param id     unique in the conversation: the list's row identity
/// @param day    the section it is listed under, such as `Today`
/// @param author who wrote it
/// @param body   what it says, with its marks
public record Message(String id, String day, String author, MarkedText body) {

    public Message {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(body, "body");
        if (id.isBlank() || day.isBlank()) {
            throw new IllegalArgumentException("a message has an id and a day");
        }
    }
}
