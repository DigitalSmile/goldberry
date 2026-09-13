package io.github.digitalsmile.goldberry.natives.md4c;

import java.util.Objects;

import io.github.digitalsmile.goldberry.natives.md4c.enums.BlockType;
import io.github.digitalsmile.goldberry.natives.md4c.enums.SpanType;
import io.github.digitalsmile.goldberry.natives.md4c.enums.TextType;

/// One thing md4c said about a document.
///
/// A parse is a **list** of these rather than a callback per event, because the
/// events are encoded natively and read once (ADR-0294) — so by the time Java sees
/// a document the parse is already over, and what it has is a value it can walk
/// twice, keep, or hand to a test.
///
/// The stream is well nested: every [EnterBlock] has a matching [LeaveBlock] and
/// every [EnterSpan] a [LeaveSpan], with the document's own [BlockType#DOC] pair
/// around the lot. Nothing here builds a tree; that is `:html`'s job, and the
/// reason this list is flat is that md4c's own interface is.
public sealed interface MarkdownEvent {

    /// A block begins.
    ///
    /// @param type what kind of block
    /// @param detail what else it says about itself — see [BlockDetail]
    record EnterBlock(BlockType type, BlockDetail detail) implements MarkdownEvent {

        public EnterBlock {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /// That block ends.
    ///
    /// No detail: md4c passes the same struct again on the way out, and a consumer
    /// that needs it has the [EnterBlock] on its own stack.
    record LeaveBlock(BlockType type) implements MarkdownEvent {

        public LeaveBlock {
            Objects.requireNonNull(type, "type");
        }
    }

    /// An inline span begins.
    record EnterSpan(SpanType type, SpanDetail detail) implements MarkdownEvent {

        public EnterSpan {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /// That span ends.
    record LeaveSpan(SpanType type) implements MarkdownEvent {

        public LeaveSpan {
            Objects.requireNonNull(type, "type");
        }
    }

    /// A run of text.
    ///
    /// @param type what kind of run — an entity, a break and ordinary words are all
    ///        text events, and the difference is here rather than in the bytes
    /// @param text the run itself. A break carries the newline it stands for, so a
    ///        consumer that ignores [#type()] still produces something sensible
    record Text(TextType type, String text) implements MarkdownEvent {

        public Text {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(text, "text");
        }
    }
}
