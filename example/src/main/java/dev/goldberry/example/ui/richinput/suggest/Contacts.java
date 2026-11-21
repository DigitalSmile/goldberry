package dev.goldberry.example.ui.richinput.suggest;

import java.util.List;
import java.util.Objects;

/// The people the showcase's composer can mention.
public final class Contacts {

    /// The most suggestions shown at once.
    public static final int SHOWN = Suggester.SHOWN;

    /// Everybody, in the order they are suggested.
    public static final List<Contact> ALL = List.of(
            new Contact("ann", "Ann Lee"),
            new Contact("bea", "Bea Ortiz"),
            new Contact("cal", "Cal Novak"),
            new Contact("dee", "Dee Park"),
            new Contact("eli", "Eli Moss"),
            new Contact("ana", "Ana Silva"));

    private Contacts() {}

    /// Up to [#SHOWN] of `from` matching `query`, in their order.
    public static List<Contact> matching(List<Contact> from, String query) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(query, "query");
        return from.stream()
                .filter(contact -> contact.matches(query))
                .limit(SHOWN)
                .toList();
    }
}
