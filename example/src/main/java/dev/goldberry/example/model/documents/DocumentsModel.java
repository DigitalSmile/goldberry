package dev.goldberry.example.model.documents;

import dev.goldberry.bind.Action;
import dev.goldberry.bind.Bind;
import dev.goldberry.bind.Model;
import dev.goldberry.example.ui.DocumentAssets;
import dev.goldberry.example.ui.HtmlSample;
import dev.goldberry.example.ui.MarkdownSample;
import dev.goldberry.markdown.Markdown;
import dev.goldberry.widgets.markup.Named;

/// What the Markdown and HTML screens show: each one's source, what its last
/// pressed link reported, and the one picture both documents may name.
///
/// Read more: [Values](https://goldberry.dev/docs/applications.html#values).
@Model
// Every field below is read, and none of them is read *here*: a `@Bind` field is
// reached by path through the generated registry, or through a woven call site
// in a native image. The reader is generated.
@SuppressWarnings("UnusedVariable")
public final class DocumentsModel {

    /// The Markdown screen's document, which is **one property read twice**: the
    /// editor on the left writes it through an action and the preview on the right
    /// follows it with `bind=`.
    ///
    /// That is the whole of "live". Nothing in this application watches the editor,
    /// diffs the text or schedules a re-render — the element holding the
    /// `markdown-view` is subscribed to this property, a keystroke marks it for
    /// rebuild, and the next frame is the parsed document.
    ///
    /// Loaded from `markdown-sample.md` beside the screens' documents rather than
    /// written as a text block here: it is prose with backslashes, backticks and
    /// trailing spaces in it, and every one of those is something a Java string
    /// literal would eat.
    @Bind("md.source")
    @SuppressWarnings("unused")
    private String markdownSource = MarkdownSample.text();

    /// The HTML screen's page, which is the same arrangement one tab along: the
    /// editor writes it, `html-view` reads it, and nothing in this application
    /// connects the two.
    ///
    /// Loaded from `html-sample.html` for the same reason the Markdown one is loaded
    /// from a file, and one more: it is markup, so it is quotation marks all the way
    /// down.
    @Bind("html.source")
    @SuppressWarnings("unused")
    private String htmlSource = HtmlSample.text();

    /// What the Markdown screen's last pressed link or ticked box reported.
    ///
    /// The same line the HTML screen has, and it is the honest demonstration of
    /// how a document view works: the toolkit hands over a destination or an
    /// ordinal and stops.
    @Bind("md.followed")
    @SuppressWarnings("unused")
    private String markdownFollowed = "Press a link, or tick a box — both are edits this application makes.";

    /// What the last link a reader pressed handed over.
    ///
    /// The showcase's whole answer to "what does following a link do", and it is
    /// deliberately the smallest honest one: the toolkit hands over an `href` and
    /// stops, because whether a link may be followed, what a relative path is relative
    /// to and what opening one costs are the application's. This application
    /// prints it.
    @Bind("html.followed")
    @SuppressWarnings("unused")
    private String htmlFollowed = "Press a link in the page — nothing here opens a browser.";

    /// The one picture this application ships, for whichever document names it.
    private final DocumentAssets assets = new DocumentAssets();

    /// Registers where both document screens' pictures come from on `named`, and
    /// answers it.
    ///
    /// A **named object** rather than an action or a binding, because an image
    /// source is neither a method nor a value that changes — which is the third
    /// registry's whole job.
    public Named register(Named named) {
        return named.bind("app.assets", assets);
    }

    /// Everything that *happens* to the two documents.
    ///
    /// Read more: [Actions](https://goldberry.dev/docs/applications.html#actions).
    @dev.goldberry.bind.runtime.Actions
    public record Actions(DocumentsModel values) {

        /// Every keystroke in the Markdown editor.
        ///
        /// The preview is not mentioned here and does not need to be: it is bound to
        /// the field this writes, so the assignment *is* the notification.
        @Action("md.set-source")
        @SuppressWarnings("unused")
        public void setMarkdownSource(String value) {
            values.markdownSource = value;
        }

        /// A link in the rendered Markdown, pressed.
        @Action("md.follow")
        @SuppressWarnings("unused")
        public void followMarkdownLink(String href) {
            values.markdownFollowed = "Followed: " + href;
        }

        /// A `[[wiki link]]`, pressed — a **target** rather than an href, which is why
        /// it is a second action: what `[[Meeting]]` names is this application's
        /// business and not a URL.
        @Action("md.open-wiki")
        @SuppressWarnings("unused")
        public void openWikiLink(String target) {
            values.markdownFollowed = "Wiki link: " + target;
        }

        /// A task box, ticked.
        ///
        /// **The round trip in three lines**, and it is the whole of the loop: the
        /// view reports *which* box — the nth task in the document — `toggleTask`
        /// rewrites that one character of the source, and the preview re-parses
        /// because it is bound to the property this assigns. The editor on the left
        /// shows the edit too, for the same reason.
        @Action("md.toggle-task")
        @SuppressWarnings("unused")
        public void toggleTask(String index) {
            int task;
            try {
                task = Integer.parseInt(index.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "md.toggle-task takes the task's position in the document, not \"" + index + "\"", e);
            }
            values.markdownSource = Markdown.toggleTask(values.markdownSource, task);
            values.markdownFollowed = "Ticked task " + task + " — the source on the left changed with it.";
        }

        /// Every keystroke in the HTML editor. The preview is not mentioned here and
        /// does not need to be, for the reason above it.
        @Action("html.set-source")
        @SuppressWarnings("unused")
        public void setHtmlSource(String value) {
            values.htmlSource = value;
        }

        /// What an anchor in the page hands over when it is pressed.
        ///
        /// A **valued** action, because a valued action is how a control reports
        /// *what* it should become and a link reports where it points. This is the
        /// application deciding what following a link means; deciding to print it is a
        /// showcase's answer, and a real one would navigate, open the desktop's browser
        /// or refuse an `href` it does not trust.
        @Action("html.follow")
        @SuppressWarnings("unused")
        public void followLink(String href) {
            values.htmlFollowed = "Followed: " + href;
        }
    }
}
