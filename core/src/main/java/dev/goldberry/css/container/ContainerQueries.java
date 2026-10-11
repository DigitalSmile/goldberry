package dev.goldberry.css.container;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.media.MediaCondition;
import dev.goldberry.css.media.MediaQueries;
import dev.goldberry.css.parse.Token;
import dev.goldberry.css.parse.TokenType;

/// Reads an `@container` prelude: an optional name, then a size condition in
/// the syntax a media query has, range forms included.
public final class ContainerQueries {

    private ContainerQueries() {}

    /// The query a prelude writes. Its condition is
    /// [MediaCondition.Unsupported] when it names a feature a container cannot
    /// answer: a `style()` query, or a media feature such as `hover`.
    public static ContainerQuery parse(List<Token> prelude) {
        var tokens = new ArrayList<>(prelude);
        while (!tokens.isEmpty() && tokens.getFirst().is(TokenType.WHITESPACE)) {
            tokens.removeFirst();
        }
        String name = null;
        if (!tokens.isEmpty() && tokens.getFirst().is(TokenType.IDENT) && !isKeyword(tokens.getFirst())) {
            name = tokens.removeFirst().text();
        }
        var block = false;
        var rewritten = new ArrayList<Token>(tokens.size());
        for (var token : tokens) {
            if (token.is(TokenType.FUNCTION)) {
                return new ContainerQuery(
                        name,
                        new MediaCondition.Unsupported(
                                token.text() + "()",
                                "a container answers size features only, not " + token.text() + "() queries"),
                        false);
            }
            if (token.is(TokenType.IDENT)) {
                var feature = token.text().toLowerCase(Locale.ROOT);
                var axis = axis(feature);
                if (axis != null) {
                    block |= axis.block();
                    rewritten.add(new Token(
                            TokenType.IDENT,
                            axis.feature(),
                            token.numeric(),
                            token.unit(),
                            token.line(),
                            token.column()));
                    continue;
                }
            }
            rewritten.add(token);
        }
        var condition = MediaQueries.parse(rewritten);
        var foreign = foreign(condition);
        if (foreign != null) {
            return new ContainerQuery(
                    name,
                    new MediaCondition.Unsupported(
                            condition.toString(), "a container answers size features only, not " + foreign),
                    block);
        }
        return new ContainerQuery(name, condition, block);
    }

    /// A size feature and the media feature it is read as.
    private record Axis(String feature, boolean block) {}

    private static @Nullable Axis axis(String feature) {
        return switch (feature) {
            case "inline-size", "width" -> new Axis("width", false);
            case "min-inline-size", "min-width" -> new Axis("min-width", false);
            case "max-inline-size", "max-width" -> new Axis("max-width", false);
            case "block-size", "height" -> new Axis("height", true);
            case "min-block-size", "min-height" -> new Axis("min-height", true);
            case "max-block-size", "max-height" -> new Axis("max-height", true);
            case "aspect-ratio", "min-aspect-ratio", "max-aspect-ratio", "orientation" -> new Axis(feature, true);
            default -> null;
        };
    }

    /// The first feature in `condition` a container cannot answer, or null.
    private static @Nullable String foreign(MediaCondition condition) {
        return switch (condition) {
            case MediaCondition.And(var parts) -> firstForeign(parts);
            case MediaCondition.Or(var parts) -> firstForeign(parts);
            case MediaCondition.Not(var inner) -> foreign(inner);
            case MediaCondition.Width _,
                    MediaCondition.Height _,
                    MediaCondition.AspectRatio _,
                    MediaCondition.Orientation _,
                    MediaCondition.Unsupported _ -> null;
            default -> condition.toString();
        };
    }

    private static @Nullable String firstForeign(List<MediaCondition> parts) {
        for (var part : parts) {
            var foreign = foreign(part);
            if (foreign != null) {
                return foreign;
            }
        }
        return null;
    }

    private static boolean isKeyword(Token token) {
        return switch (token.text().toLowerCase(Locale.ROOT)) {
            case "not", "and", "or", "only" -> true;
            default -> false;
        };
    }
}
