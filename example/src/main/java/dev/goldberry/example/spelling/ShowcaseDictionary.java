package dev.goldberry.example.spelling;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.Goldberry;
import dev.goldberry.log.Logs;
import dev.goldberry.text.spell.SpellChecker;
import dev.goldberry.text.spell.SpellCheckers;

/// SCOWL's `en_US` dictionary, installed for the whole showcase the first time a
/// card asks for it.
///
/// The files are the build's (`assetTool.bundle('scowl-en-us', …)`), under
/// this package, and the engine is whichever `goldberry-spell` puts on the
/// module path. Loading takes about half a second cold and a twentieth warm,
/// so a window loads it away from the UI thread ([#install()]); the fields
/// hear the checker arrive and check then.
public final class ShowcaseDictionary {

    private static final Logger LOG = Logs.of(ShowcaseDictionary.class);

    private static @Nullable SpellChecker installed;
    private static boolean tried;

    private ShowcaseDictionary() {}

    /// Installs the English checker: in the background when called on the UI
    /// thread, where a window is waiting to draw, and at once anywhere else, so
    /// an offscreen render has it on its one frame.
    public static void install() {
        if (Goldberry.isUiThread()) {
            var _ = Goldberry.async(ShowcaseDictionary::english);
        } else {
            var _ = english();
        }
    }

    /// The English checker, installed now if it was not already; empty when the
    /// dictionary or the engine is missing, which is logged once.
    public static synchronized Optional<SpellChecker> english() {
        var current = installed;
        if (current != null && SpellCheckers.find(Locale.US).orElse(null) == current) {
            return Optional.of(current);
        }
        if (tried && current == null) {
            return Optional.empty();
        }
        tried = true;
        try (var affix = resource("en_US.aff");
                var words = resource("en_US.dic")) {
            installed = SpellCheckers.load(Locale.US, affix, words);
        } catch (IOException | IllegalStateException e) {
            LOG.warn("the showcase's English dictionary did not load, so nothing is checked: {}", e.getMessage());
            installed = null;
        }
        return Optional.ofNullable(installed);
    }

    private static InputStream resource(String name) throws IOException {
        var stream = ShowcaseDictionary.class.getResourceAsStream(name);
        if (stream == null) {
            throw new IOException(name + " is not among the showcase's resources; run :example:prepareAssets");
        }
        return stream;
    }
}
