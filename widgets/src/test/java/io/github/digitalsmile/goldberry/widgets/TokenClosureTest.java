package io.github.digitalsmile.goldberry.widgets;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.Theme;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// Every `var(--gb-…)` the toolkit writes resolves to something.
///
/// This exists because the same mistake happened twice within a day, and both
/// times the only symptom was a log line. A custom property that resolves to
/// nothing makes the cascade **drop the whole declaration** — so the widget keeps
/// whatever it inherited, which is usually plausible: the first was a step
/// counter that came out the same colour as its prose, and the second was a
/// primary button that lost its foreground and looked merely unstyled.
///
/// Neither was visible in a golden, because a golden compares against whatever
/// was rendered when it was last accepted, and both tokens were already missing
/// then. The log said so every frame, per node, which is a stream rather than a
/// message.
///
/// So it is checked here instead, where a missing token is one failure with the
/// name in it.
class TokenClosureTest {

    /// `var(--gb-foo)` and `var(--gb-foo, fallback)`.
    private static final Pattern USE = Pattern.compile("var\\(\\s*(--gb-[a-z0-9-]+)");

    /// A declaration of a custom property — `--gb-foo:` at the start of a
    /// declaration rather than inside a `var()`.
    private static final Pattern DEFINE = Pattern.compile("(?m)^\\s*(--gb-[a-z0-9-]+)\\s*:");

    private static String read(String resource) {
        try (var in = Controls.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("no such stylesheet: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Set<String> matches(Pattern pattern, String text) {
        var found = new LinkedHashSet<String>();
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static String readCore(String name) {
        try (var in = Theme.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("no such theme: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest
    @EnumSource(Theme.class)
    @DisplayName("every token the catalog uses is defined by every theme")
    void closedUnderEveryTheme(Theme theme) {
        var used = new TreeSet<String>();
        var defined = new TreeSet<String>();
        for (var source : List.of(read("controls.css"), read("density-compact.css"))) {
            used.addAll(matches(USE, source));
            defined.addAll(matches(DEFINE, source));
        }
        var themeSource = readCore(theme == Theme.NORD_DARK ? "nord-dark.css" : "nord-light.css");
        used.addAll(matches(USE, themeSource));
        defined.addAll(matches(DEFINE, themeSource));

        var missing = new TreeSet<>(used);
        missing.removeAll(defined);
        assertTrue(missing.isEmpty(),
                () -> "these tokens are used and never defined under " + theme + ": " + missing
                        + " — a var() that resolves to nothing makes the cascade drop the whole"
                        + " declaration, so the widget silently keeps what it inherited");
    }

}
