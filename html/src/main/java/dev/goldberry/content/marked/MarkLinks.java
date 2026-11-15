package dev.goldberry.content.marked;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;

import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkKind.Behaviour;
import dev.goldberry.text.mark.MarkKind.Custom;
import dev.goldberry.text.mark.MarkKind.Link;
import dev.goldberry.text.mark.MarkKind.Mention;
import dev.goldberry.text.mark.MarkKind.Style;

/// The address a reference kind is written as, where Markdown and HTML have
/// only links: a link's own address, `mention:` and the id for a mention, and
/// `mark:` for a kind of the application's own.
///
/// ```text
/// https://goldberry.dev              a link
/// mention:user%3A42                  MarkKind.Mention("user:42")
/// mark:hashtag/rel                   MarkKind.Custom("hashtag", "rel", REFERENCE)
/// mark:emoji/123?atomic&exclusive    MarkKind.Custom("emoji", "123", ATOM)
/// ```
///
/// A real link to an address in either scheme reads back as the kind; a page
/// that links to `mention:` meant something by it.
public final class MarkLinks {

    /// The scheme a mention is written in.
    public static final String MENTION = "mention:";

    /// The scheme a custom kind is written in.
    public static final String MARK = "mark:";

    private MarkLinks() {}

    /// The address `kind` is written as, or nothing for a style, which is not a
    /// link at all.
    public static Optional<String> href(MarkKind kind) {
        return switch (kind) {
            case Style _ -> Optional.empty();
            case Link(var uri) -> Optional.of(uri.toString());
            case Mention(var id) -> Optional.of(MENTION + encode(id));
            case Custom(var name, var value, var behaviour) ->
                Optional.of(MARK + name + "/" + encode(value) + flags(behaviour));
        };
    }

    /// The kind `href` is written for: a mention, a custom kind, or a link.
    /// Nothing for an address that is none of them, whose words are kept plain.
    public static Optional<MarkKind> kind(String href) {
        try {
            if (href.startsWith(MENTION)) {
                var id = decode(href.substring(MENTION.length()));
                return id.isBlank() ? Optional.empty() : Optional.of(new Mention(id));
            }
            if (href.startsWith(MARK)) {
                return custom(href.substring(MARK.length()));
            }
            var uri = new URI(href.strip());
            return uri.getScheme() == null && href.isBlank() ? Optional.empty() : Optional.of(new Link(uri));
        } catch (URISyntaxException | IllegalArgumentException notAnAddress) {
            return Optional.empty();
        }
    }

    private static Optional<MarkKind> custom(String rest) {
        var query = rest.indexOf('?');
        var path = query < 0 ? rest : rest.substring(0, query);
        var slash = path.indexOf('/');
        if (slash <= 0) {
            return Optional.empty();
        }
        var flags = query < 0 ? "" : rest.substring(query + 1);
        var grows = false;
        var atomic = false;
        var exclusive = false;
        for (var flag : flags.split("&", -1)) {
            switch (flag) {
                case "grows" -> grows = true;
                case "atomic" -> atomic = true;
                case "exclusive" -> exclusive = true;
                default -> {}
            }
        }
        return Optional.of(new Custom(
                path.substring(0, slash),
                decode(path.substring(slash + 1)),
                new Behaviour(grows, atomic, exclusive || atomic)));
    }

    private static String flags(Behaviour behaviour) {
        var flags = new ArrayList<String>(3);
        if (behaviour.growsAtEnd()) {
            flags.add("grows");
        }
        if (behaviour.atomic()) {
            flags.add("atomic");
        }
        if (behaviour.exclusive()) {
            flags.add("exclusive");
        }
        return flags.isEmpty() ? "" : "?" + String.join("&", flags);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
