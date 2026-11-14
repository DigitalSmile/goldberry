package dev.goldberry.text.mark;

import java.util.Set;

/// Where an editor learns how marked words look: the [MarkStyle] of each set of
/// kinds that occurs in its text.
///
/// `rich-input` answers from the stylesheet, a node per set of kinds styled by
/// their classes (`mark.bold.italic`). A canvas application answers from its
/// own fonts.
///
/// Asked whenever a line is shaped or drawn, so an answer should be a lookup and
/// not a resolution. Return **the same [dev.goldberry.text.font.Font] instance**
/// for the same face: two pieces of a line in one font are shaped as one, and a
/// shaping is kept per font instance.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
@FunctionalInterface
public interface MarkStyles {

    /// How words under exactly `kinds` look. Never asked for the empty set:
    /// unmarked words are the text's own.
    MarkStyle of(Set<MarkKind> kinds);
}
