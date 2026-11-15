package dev.goldberry.widgets.form.richinput;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MentionQuery;

/// An open mention query as a [RichInput] reports it: what was typed, where it
/// is, and the way back into the field.
///
/// ```java
/// prompt.query().query();                                   // "an", matched against contacts
/// host.attachedPopup(list, prompt.anchor(), Placement.ABOVE, 0, fit);
/// prompt.complete("@Ann Lee", new MarkKind.Mention("user:42"));
/// ```
///
/// A prompt answers for the query it was made for. Once the field has moved on
/// (another letter typed, the caret moved away) [#isCurrent()] is false, and
/// [#complete] and [#dismiss] change nothing, so a suggestion list a frame
/// behind cannot put a name over the wrong word.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#keyboard).
public final class MentionPrompt {

    /// What the field does with a prompt's answer: [RichInputState].
    interface Field {

        boolean complete(MentionPrompt prompt, String text, @Nullable MarkKind kind);

        boolean dismiss(MentionPrompt prompt);

        boolean isCurrent(MentionPrompt prompt);
    }

    private final MentionQuery query;

    private final LogicalRect anchor;

    private final Field field;

    MentionPrompt(MentionQuery query, LogicalRect anchor, Field field) {
        this.query = Objects.requireNonNull(query, "query");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.field = Objects.requireNonNull(field, "field");
    }

    /// What was typed and the word it replaces.
    public MentionQuery query() {
        return query;
    }

    /// The query's first line, in the window's coordinates: what a list of
    /// suggestions hangs off, as a popup's anchor.
    public LogicalRect anchor() {
        return anchor;
    }

    /// Puts `text` in place of the query's word, under `kind` (a
    /// [MarkKind.Mention] makes it an atom), with a space after it, the caret past
    /// both: one undo step. Null `kind` puts plain text in.
    ///
    /// @return whether it went in: false when the field has moved on, is
    ///         read-only or is gone
    public boolean complete(String text, @Nullable MarkKind kind) {
        Objects.requireNonNull(text, "text");
        return field.complete(this, text, kind);
    }

    /// Closes the query without choosing, as `Escape` does: the field does not
    /// report it again until the caret leaves the word.
    ///
    /// @return whether it was still open
    public boolean dismiss() {
        return field.dismiss(this);
    }

    /// Whether this is still the field's open query.
    public boolean isCurrent() {
        return field.isCurrent(this);
    }

    @Override
    public String toString() {
        return "MentionPrompt[" + query.typed() + " at " + anchor + "]";
    }
}
