package dev.goldberry.example.ui.forms.mention;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import dev.goldberry.text.mark.MarkKind;

/// Somebody the composer can mention.
///
/// @param handle what follows the `@`, and the mention's id
/// @param name   what the mention reads as once it is in
public record Contact(String handle, String name) {

    private static final Pattern SPACES = Pattern.compile("\\s+");

    public Contact {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(name, "name");
        if (handle.isBlank() || name.isBlank()) {
            throw new IllegalArgumentException("a contact has a handle and a name");
        }
    }

    /// The text the mention is put in as: `@` and the name.
    public String mentionText() {
        return "@" + name;
    }

    /// The mark the mention carries: an atom naming this contact.
    public MarkKind.Mention kind() {
        return new MarkKind.Mention("user:" + handle);
    }

    /// Whether `query` starts the handle or any word of the name, ignoring
    /// case. An empty query matches everybody.
    public boolean matches(String query) {
        var wanted = query.toLowerCase(Locale.ROOT);
        return handle.toLowerCase(Locale.ROOT).startsWith(wanted)
                || SPACES.splitAsStream(name.toLowerCase(Locale.ROOT)).anyMatch(word -> word.startsWith(wanted));
    }
}
