package dev.goldberry.example.ui.collections;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Collections** screen: a list, a table, a tree and a slot, each a card
/// with a selection of its own.
///
/// Read more: [Collections](https://goldberry.dev/docs/components/collections.html).
///
/// @param context what the screen is built from
public record CollectionsChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("components/collections");

    private static final String SUMMARY = "Three widgets over a model the application owns: they select by id,"
            + " report what the user chose, and never edit the data. A slot places whatever widget the model holds.";

    @Override
    public Widget build(BuildContext buildContext) {
        // Two columns, not as many as fit: the cards on this screen hold rows that
        // do not wrap, and a narrow column cuts them at a large text scale.
        return Wall.inColumns(
                "collections",
                "Collections",
                SUMMARY,
                CHAPTER,
                2,
                List.of(new LeaguesCard(), new CompanyCard(), new RealmsCard(), new SlotCard()));
    }
}
