package dev.goldberry.widgets.form.richinput;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.text.mark.MentionQuery;

/// What a [RichInput] does about mentions: which characters open a query, who
/// hears about it, and who gets the keys while one is open.
///
/// ```java
/// new RichInput(draft, this::edited)
///         .mentions(Mentions.on(this::suggest, "@", "#")
///                 .keys(this::chooseWithKeys));
///
/// void suggest(Optional<MentionPrompt> prompt) {
///     prompt.ifPresentOrElse(open -> showContacts(open.query().query(), open.anchor()), this::hideContacts);
/// }
/// ```
///
/// The field **reports** and the application **suggests**: what is looked up,
/// how a suggestion looks and where the list goes are the application's, and the
/// field's part ends at saying what was typed and where. The chosen suggestion
/// goes back through [MentionPrompt#complete].
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#keyboard).
///
/// @param triggers the characters that open a query at a word start, such as
///                 `@`; checked by [MentionQuery#requireTriggers]
/// @param onQuery  told when a query opens, every time it changes, and once with
///                 nothing when it ends
/// @param onKey    offered `Up`, `Down`, `Enter`, `Tab` and `Escape` while a query
///                 is open, before the field acts on them; true takes the key.
///                 `Escape` that it leaves closes the query until the caret
///                 leaves the word
public record Mentions(Set<String> triggers, Consumer<Optional<MentionPrompt>> onQuery, Predicate<KeyEvent> onKey) {

    /// The keys a list of suggestions answers to, offered to [#onKey] first.
    public static final Set<Key> ROUTED = Set.of(Key.UP, Key.DOWN, Key.ENTER, Key.TAB, Key.ESCAPE);

    public Mentions {
        triggers = MentionQuery.requireTriggers(triggers);
        if (triggers.isEmpty()) {
            throw new IllegalArgumentException("mentions need at least one trigger, such as `@`");
        }
        Objects.requireNonNull(onQuery, "onQuery");
        Objects.requireNonNull(onKey, "onKey");
    }

    /// `triggers` opening queries reported to `onQuery`, with no keys taken:
    /// the list is chosen from with the pointer until [#keys] says otherwise.
    public static Mentions on(Consumer<Optional<MentionPrompt>> onQuery, String... triggers) {
        Objects.requireNonNull(triggers, "triggers");
        return new Mentions(new LinkedHashSet<>(Arrays.asList(triggers)), onQuery, _ -> false);
    }

    /// These mentions, with `handler` offered the keys of [#ROUTED] while a
    /// query is open.
    public Mentions keys(Predicate<KeyEvent> handler) {
        return new Mentions(triggers, onQuery, handler);
    }

    /// Whether `event` goes to [#onKey] first while a query is open: a press of
    /// one of [#ROUTED], held or not, with no modifier but `Shift`.
    static boolean routed(KeyEvent event) {
        var modifiers = event.modifiers();
        return event.kind() == KeyEvent.Kind.PRESSED
                && ROUTED.contains(event.key())
                && !modifiers.control()
                && !modifiers.alt()
                && !modifiers.meta();
    }
}
