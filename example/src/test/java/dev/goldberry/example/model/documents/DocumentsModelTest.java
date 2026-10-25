package dev.goldberry.example.model.documents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.example.ui.DocumentAssets;
import dev.goldberry.widgets.markup.Named;

/// The two document screens: a link reported and not followed, and a ticked
/// box that rewrites the source it was drawn from.
@DisplayName("the documents slice")
class DocumentsModelTest {

    private final DocumentsModel model = new DocumentsModel();
    private final DocumentsModel.Actions actions = new DocumentsModel.Actions(model);

    @Test
    @DisplayName("a pressed link is reported, in each document's own line")
    void linksAreReported() {
        actions.followMarkdownLink("https://example.org/");
        actions.followLink("#shire");

        assertEquals(
                "Followed: https://example.org/",
                Models.observable(model, "md.followed").get());
        assertEquals(
                "Followed: #shire", Models.observable(model, "html.followed").get());

        actions.openWikiLink("Meeting");
        assertEquals(
                "Wiki link: Meeting", Models.observable(model, "md.followed").get());
    }

    @Test
    @DisplayName("a ticked task rewrites the source, and ticking it again puts it back")
    void aTaskRewritesTheSource() {
        // The task is found by parsing, through md4c in libgoldberry.
        RendererRequirement.enforce();
        actions.setMarkdownSource("- [ ] Pack the rope\n- [ ] Return the ring\n");

        actions.toggleTask("1");
        assertEquals(
                "- [ ] Pack the rope\n- [x] Return the ring\n",
                Models.observable(model, "md.source").get());
        assertNotEquals(
                "Press a link, or tick a box — both are edits this application makes.",
                Models.observable(model, "md.followed").get());

        actions.toggleTask(" 1 ");
        assertEquals(
                "- [ ] Pack the rope\n- [ ] Return the ring\n",
                Models.observable(model, "md.source").get());
    }

    @Test
    @DisplayName("the editors write the sources their previews read")
    void editorsWriteSources() {
        actions.setHtmlSource("<p>Hi</p>");

        assertEquals("<p>Hi</p>", Models.observable(model, "html.source").get());
    }

    @Test
    @DisplayName("both documents name the one picture this application ships")
    void assetsAreNamed() {
        assertInstanceOf(
                DocumentAssets.class, model.register(Named.strict()).bound().get("app.assets"));
    }
}
