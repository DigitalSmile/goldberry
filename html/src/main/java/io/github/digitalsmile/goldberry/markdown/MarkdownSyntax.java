package io.github.digitalsmile.goldberry.markdown;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import io.github.digitalsmile.goldberry.natives.md4c.enums.MarkdownFlag;

/// Which dialect a document is parsed as — a value, not a set of arguments.
///
/// ```java
/// var syntax = MarkdownSyntax.gitHub().with(MarkdownExtension.WIKI_LINKS);
/// var document = Markdown.parse(source, syntax);
/// ```
///
/// A value because a dialect is a property of a *corpus* rather than of a call: an
/// application parses every note it owns the same way, so it keeps one of these
/// beside the store rather than repeating a list of flags at each parse. It is also
/// what makes the two halves agree — the same syntax value parses a note for the
/// preview and for the HTML the server hands out, and nothing can drift between
/// them.
///
/// @param extensions the departures from CommonMark, which may be empty
public record MarkdownSyntax(Set<MarkdownExtension> extensions) {

    /// Plain CommonMark: no tables, no task lists, no bare URLs.
    ///
    /// The specification and nothing else, which is what a document written to be
    /// read by other tools should be parsed as.
    public static MarkdownSyntax commonMark() {
        return new MarkdownSyntax(Set.of());
    }

    /// What people mean when they say Markdown in 2026: CommonMark with tables,
    /// strikethrough, task lists and bare URLs.
    ///
    /// md4c calls this set `MD_DIALECT_GITHUB`, and it is [Markdown#parse(String)]'s
    /// default because a document written anywhere on the internet in the last
    /// decade assumes it.
    public static MarkdownSyntax gitHub() {
        return new MarkdownSyntax(Set.of(
                MarkdownExtension.TABLES,
                MarkdownExtension.STRIKETHROUGH,
                MarkdownExtension.TASK_LISTS,
                MarkdownExtension.AUTOLINKS));
    }

    /// CommonMark plus exactly these.
    public static MarkdownSyntax of(MarkdownExtension... extensions) {
        return new MarkdownSyntax(Set.of(extensions));
    }

    public MarkdownSyntax {
        Objects.requireNonNull(extensions, "extensions");
        // Copied into an immutable set, so a caller that kept their EnumSet cannot
        // change a dialect a document has already been parsed with.
        extensions = Set.copyOf(extensions);
    }

    /// This dialect and `more`.
    public MarkdownSyntax with(MarkdownExtension... more) {
        var all = EnumSet.noneOf(MarkdownExtension.class);
        all.addAll(extensions);
        all.addAll(Arrays.asList(more));
        return new MarkdownSyntax(all);
    }

    /// This dialect without `fewer`.
    public MarkdownSyntax without(MarkdownExtension... fewer) {
        var all = EnumSet.noneOf(MarkdownExtension.class);
        all.addAll(extensions);
        Arrays.asList(fewer).forEach(all::remove);
        return new MarkdownSyntax(all);
    }

    /// Whether `extension` is on.
    public boolean has(MarkdownExtension extension) {
        return extensions.contains(extension);
    }

    /// The md4c mask this dialect adds up to.
    ///
    /// Package-private: this is where the toolkit's vocabulary ends and the
    /// binding's begins (ADR-0294).
    Set<MarkdownFlag> flags() {
        var flags = EnumSet.noneOf(MarkdownFlag.class);
        for (var extension : extensions) {
            flags.addAll(extension.flags());
        }
        return flags;
    }
}
