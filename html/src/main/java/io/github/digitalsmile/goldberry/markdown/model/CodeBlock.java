package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// A fenced or indented code block.
///
/// A leaf: its content is text, not nodes, and every byte of it is content —
/// indentation and trailing newlines included, which is why this is a `String`
/// rather than a list of lines.
///
/// @param language the fence's first word — `java` — or null when the author wrote
///        none. A highlighter's key; `goldberry-code` is what will read it
/// @param info everything after the fence, `java {hl=3}` and all, for the
///        applications that put their own directives there
/// @param code the code itself, verbatim
public record CodeBlock(@Nullable String language, String info, String code) implements Block {

    public CodeBlock {
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(code, "code");
    }

    /// A block with no info string at all, which is what an indented one is.
    public CodeBlock(String code) {
        this(null, "", code);
    }

    @Override
    public List<MarkdownNode> children() {
        return List.of();
    }

    /// The code as lines, with no trailing empty line for the final newline.
    ///
    /// What a renderer draws, since a line is a box and a `\n` is not.
    public List<String> lines() {
        if (code.isEmpty()) {
            return List.of();
        }
        var body = code.endsWith("\n") ? code.substring(0, code.length() - 1) : code;
        return List.of(body.split("\n", -1));
    }
}
