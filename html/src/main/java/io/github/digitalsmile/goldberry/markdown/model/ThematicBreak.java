package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;

/// `---` — a change of subject, drawn as a rule.
public record ThematicBreak() implements Block {

    @Override
    public List<MarkdownNode> children() {
        return List.of();
    }
}
