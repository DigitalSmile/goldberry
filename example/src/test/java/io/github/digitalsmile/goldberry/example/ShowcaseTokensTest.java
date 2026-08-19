package io.github.digitalsmile.goldberry.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.widgets.Controls;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The showcase's own stylesheet uses no token that nothing defines.
///
/// The application half of `TokenClosureTest`, and the half that has actually
/// caught something: `--gb-text-subtle` was invented in this file, defined
/// nowhere, and produced a stream of dropped-declaration warnings and a caption
/// the same colour as the prose it was meant to be quieter than.
///
/// An application may of course define its own tokens; what it may not do is use
/// one that neither it nor the toolkit declares.
class ShowcaseTokensTest {

    private static final Pattern USE = Pattern.compile("var\\(\\s*(--gb-[a-z0-9-]+)");
    private static final Pattern DEFINE = Pattern.compile("(?m)^\\s*(--gb-[a-z0-9-]+)\\s*:");

    private static Set<String> matches(Pattern pattern, String text) {
        var found = new LinkedHashSet<String>();
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    private static String read(Class<?> anchor, String name) {
        try (var in = anchor.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("no such stylesheet: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("the showcase invents no token")
    void closed() {
        var app = read(Showcase.class, "showcase.css");
        var defined = new TreeSet<String>();
        for (var source : List.of(
                read(Controls.class, "controls.css"),
                read(Controls.class, "density-compact.css"),
                read(Theme.class, "nord-dark.css"),
                read(Theme.class, "nord-light.css"))) {
            defined.addAll(matches(DEFINE, source));
        }
        defined.addAll(matches(DEFINE, app));

        var missing = new TreeSet<>(matches(USE, app));
        missing.removeAll(defined);
        assertTrue(missing.isEmpty(),
                () -> "the showcase uses tokens nothing defines: " + missing
                        + " — the cascade drops the whole declaration, so the node silently"
                        + " keeps whatever it inherited");
    }
}
