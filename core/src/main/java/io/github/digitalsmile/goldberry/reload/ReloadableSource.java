package io.github.digitalsmile.goldberry.reload;

import io.github.digitalsmile.goldberry.natives.log.Logs;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;

/// A file that is parsed, and re-parsed when it changes, keeping the last thing
/// that parsed.
///
/// The half of hot reload that has no threads in it. Everything interesting
/// about reloading is here — what happens when the file is broken, when it is
/// half-written, when it has not actually changed — and [HotReload] adds only
/// the watching.
///
/// ## Broken input is the normal case
///
/// A stylesheet or a document being edited is broken more often than it is
/// whole: every keystroke between `{` and `}` is a parse error, and an editor
/// that truncates before writing leaves it empty for a millisecond. So a failed
/// parse is **not** an error here. The last good value stays in force, the
/// failure is logged once with its position, and the next save gets another go.
///
/// That is the opposite of the load-time strictness in
/// [io.github.digitalsmile.goldberry.css.CssSyntaxException], and deliberately:
/// at start-up a broken stylesheet is a bug worth stopping for; at 2pm on a
/// Tuesday with the editor open it is just Tuesday.
///
/// @param <T> what the file parses into — a `Stylesheet`, a list of `KdlNode`
public final class ReloadableSource<T> {

    private static final Logger LOG = Logs.of(ReloadableSource.class);

    private final Path file;
    private final Function<String, T> parser;

    private T current;
    private String lastText;
    private RuntimeException lastFailure;

    private ReloadableSource(Path file, Function<String, T> parser, T initial, String initialText) {
        this.file = file;
        this.parser = parser;
        this.current = initial;
        this.lastText = initialText;
    }

    /// Loads `file` for the first time.
    ///
    /// The first parse **is** strict: there is no last good value to fall back
    /// to, and an application that starts with a broken stylesheet should say so
    /// rather than render unthemed and leave somebody guessing.
    ///
    /// @throws java.io.UncheckedIOException if the file cannot be read
    /// @throws RuntimeException whatever `parser` throws
    public static <T> ReloadableSource<T> load(Path file, Function<String, T> parser) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(parser, "parser");
        var text = read(file);
        return new ReloadableSource<>(file, parser, parser.apply(text), text);
    }

    /// The value in force: the most recent successful parse.
    public T current() {
        return current;
    }

    public Path file() {
        return file;
    }

    /// Why the last reload failed, if it did and nothing has succeeded since.
    public Optional<RuntimeException> lastFailure() {
        return Optional.ofNullable(lastFailure);
    }

    /// Re-reads and re-parses.
    ///
    /// @return the new value if this produced one, or empty if the file was
    ///         unchanged or would not parse — in both of those cases
    ///         [#current()] is untouched and there is nothing to apply
    public Optional<T> reload() {
        String text;
        try {
            text = read(file);
        } catch (RuntimeException e) {
            // A file that vanished, or a rename-into-place caught mid-flight.
            // Both resolve themselves on the next event.
            LOG.debug("could not read {} during reload", file, e);
            return Optional.empty();
        }

        if (text.equals(lastText)) {
            // Editors write on every autosave and a watcher reports the same
            // change more than once; re-styling the tree for an identical file
            // is work nobody asked for.
            return Optional.empty();
        }

        try {
            var parsed = parser.apply(text);
            lastText = text;
            current = parsed;
            if (lastFailure != null) {
                LOG.info("{} parses again", file.getFileName());
                lastFailure = null;
            }
            return Optional.of(parsed);
        } catch (RuntimeException e) {
            // Logged once per distinct failure, not once per keystroke.
            if (lastFailure == null || !Objects.equals(lastFailure.getMessage(), e.getMessage())) {
                LOG.warn("{} did not parse; keeping the last good version: {}",
                        file.getFileName(), e.getMessage());
            }
            lastFailure = e;
            // Deliberately NOT stored as lastText: the next save is a change
            // against the last good text, so a file edited back to something
            // valid still reloads.
            return Optional.empty();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("could not read " + file, e);
        }
    }
}
