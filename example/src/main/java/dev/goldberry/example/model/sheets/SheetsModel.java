package dev.goldberry.example.model.sheets;

import dev.goldberry.bind.Action;
import dev.goldberry.bind.Bind;
import dev.goldberry.bind.Model;

/// What the Icons and Emoji screens' search fields hold.
///
/// Bound so each field reads its value and **watched** by its screen, which are
/// two different things: the field shows the value, and the sheet is a different
/// number of rows per query — a value for one and a structure for the other.
///
/// Read more: [Values](https://goldberry.dev/docs/applications.html#values).
@Model
// Every field below is read, and none of them is read *here*: a `@Bind` field is
// reached by path through the generated registry, or through a woven call site
// in a native image. The reader is generated.
@SuppressWarnings("UnusedVariable")
public final class SheetsModel {

    /// What the Icons screen's search field holds.
    @Bind("app.icon-query")
    @SuppressWarnings("unused")
    private String iconQuery = "";

    /// The same for the Emoji screen, and a second field rather than one shared
    /// with the icons: two sheets that filtered on one value would clear each
    /// other every time a reader moved between them.
    @Bind("app.emoji-query")
    @SuppressWarnings("unused")
    private String emojiQuery = "";

    /// What the icon sheet is filtering on.
    public String iconQuery() {
        return iconQuery;
    }

    /// And what the emoji sheet is filtering on.
    public String emojiQuery() {
        return emojiQuery;
    }

    /// What the two search fields report.
    ///
    /// Read more: [Actions](https://goldberry.dev/docs/applications.html#actions).
    @dev.goldberry.bind.runtime.Actions
    public record Actions(SheetsModel values) {

        /// What the search field reports on every keystroke.
        @Action("app.set-icon-query")
        @SuppressWarnings("unused")
        public void setIconQuery(String value) {
            values.iconQuery = value;
        }

        /// The emoji sheet's own field, for [SheetsModel#emojiQuery]'s reason.
        @Action("app.set-emoji-query")
        @SuppressWarnings("unused")
        public void setEmojiQuery(String value) {
            values.emojiQuery = value;
        }
    }
}
