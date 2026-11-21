package dev.goldberry.example.ui.richinput.suggest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkKind.Behaviour;

/// What the showcase's fields can suggest, one list per trigger, each putting
/// its choice in under a different kind of mark.
///
/// | Trigger | Suggests | Goes in as |
/// |---------|----------|------------|
/// | `@` | people | a [MarkKind.Mention]: an atom |
/// | `#` | topics | the custom kind `topic`, edited like a link |
/// | `:` | emoji by shortcode | plain text: the emoji itself |
/// | `/` | commands | the custom kind `command`, an atom |
public final class Directories {

    /// The kind a topic goes in under: a reference, so typing after it does not
    /// extend it and a letter inside it is ordinary editing.
    public static final String TOPIC = "topic";

    /// The kind a command goes in under: an atom, as a mention is.
    public static final String COMMAND = "command";

    /// Everybody in [Contacts#ALL].
    public static final List<Suggestion> PEOPLE =
            Contacts.ALL.stream().map(Contact::suggestion).toList();

    /// The topics `#` finds.
    public static final List<Suggestion> TOPICS = List.of(
            topic("release", "Release planning"),
            topic("design", "Design reviews"),
            topic("random", "Anything at all"),
            topic("rust-port", "The port nobody asked for"));

    /// The emoji `:` finds, by shortcode.
    public static final List<Suggestion> EMOJI = List.of(
            emoji("smile", "😄"),
            emoji("sparkles", "✨"),
            emoji("rocket", "🚀"),
            emoji("tada", "🎉"),
            emoji("thumbsup", "👍"),
            emoji("heart", "❤️"));

    /// The commands `/` finds.
    public static final List<Suggestion> COMMANDS = List.of(
            command("remind", "Remind me about this later"),
            command("poll", "Ask everybody a question"),
            command("giphy", "Find a moving picture"),
            command("shrug", "Append a shrug"));

    /// Each trigger and what it looks up, in the order a field lists them.
    public static final SequencedMap<String, Suggester> ALL = all();

    private Directories() {}

    /// The custom kind a topic is marked with.
    public static MarkKind.Custom topicKind(String tag) {
        return new MarkKind.Custom(TOPIC, tag, Behaviour.REFERENCE);
    }

    /// The custom kind a command is marked with.
    public static MarkKind.Custom commandKind(String name) {
        return new MarkKind.Custom(COMMAND, name, Behaviour.ATOM);
    }

    private static SequencedMap<String, Suggester> all() {
        var all = new LinkedHashMap<String, Suggester>();
        all.put("@", Suggester.of(PEOPLE));
        all.put("#", Suggester.of(TOPICS));
        all.put(":", Suggester.of(EMOJI));
        all.put("/", Suggester.of(COMMANDS));
        return Collections.unmodifiableSequencedMap(all);
    }

    private static Suggestion topic(String tag, String about) {
        return new Suggestion("topic-" + tag, "#" + tag, about, "#" + tag, topicKind(tag), List.of(tag));
    }

    private static Suggestion emoji(String code, String emoji) {
        return new Suggestion("emoji-" + code, emoji + "  :" + code + ":", "", emoji, null, List.of(code));
    }

    private static Suggestion command(String name, String about) {
        return new Suggestion("command-" + name, "/" + name, about, "/" + name, commandKind(name), List.of(name));
    }
}
