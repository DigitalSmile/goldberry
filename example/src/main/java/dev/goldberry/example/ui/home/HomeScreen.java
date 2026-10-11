package dev.goldberry.example.ui.home;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.gallery.GalleryTab;
import dev.goldberry.example.ui.gallery.Section;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.Spacer;
import dev.goldberry.widgets.core.icon.IconView;
import dev.goldberry.widgets.panel.card.Card;
import dev.goldberry.widgets.panel.masonry.Masonry;
import dev.goldberry.widgets.text.Link;
import dev.goldberry.widgets.text.Text;

/// The screen the showcase opens on: what is in it, and three ways in.
///
/// ```text
/// Goldberry showcase
/// Every widget in the toolkit, in a window.
/// [ ⌕ Search screens, cards and commands…  Ctrl K ]  [Take the tour]
/// Recently: Buttons · Forms
/// ┌ Basics ──────────┐ ┌ Controls ────────┐ ┌ Structure ───────┐
/// │ Layout        4  │ │ Buttons      12  │ │ Panels        9  │
/// │ Scrolling     2  │ │ …                │ │ …                │
/// Read the guide
/// OVERVIEW            GETTING STARTED       PERFORMANCE
/// Goldberry ↗         Requirements ↗        What a frame costs ↗
/// A declarative…      A JDK 25 and…         The numbers to…
/// ```
///
/// A tile per [Section], listing its screens with the number of cards each
/// holds, so a reader sees the whole gallery at once and picks rather than
/// scrolls a strip. Not one of the gallery's screens: it mirrors no chapter, so
/// it has no digit and no row in Edit ▸ Go to.
///
/// Read more: [The catalogue](https://goldberry.dev/docs/components/index.html).
///
/// @param recent     the gallery screens visited lately, most recent first
/// @param counts     how many cards each screen holds, by name
/// @param pick       what a screen's row asks for
/// @param openSearch what the search field does
/// @param startTour  what the tour's button does
public record HomeScreen(
        List<String> recent,
        Map<String, Integer> counts,
        Consumer<String> pick,
        Runnable openSearch,
        Runnable startTour)
        implements Widget.Stateless {

    /// The name Home is picked by, beside the gallery's screens.
    public static final String NAME = "home";

    /// What the sidebar and the trail call it.
    public static final String TITLE = "Home";

    /// The bundled icon beside it.
    public static final String ICON = "house";

    /// The narrowest a column of tiles may get.
    static final int TILE_WIDTH = 300;

    public HomeScreen {
        recent = List.copyOf(recent);
        counts = Map.copyOf(counts);
    }

    @Override
    public Widget build(BuildContext context) {
        var children = new ArrayList<Widget>();
        children.add(hero());
        if (!recent.isEmpty()) {
            children.add(recently());
        }
        children.add(new Masonry(
                Arrays.stream(Section.values()).map(this::tile).toList(),
                Masonry.UNSET,
                TILE_WIDTH,
                Attributes.NONE.id("home-tiles").classes("wall")));
        children.add(guide());
        return new Column(children, Attributes.NONE.id("screen-" + NAME).classes("screen", "home"));
    }

    /// The id of the link to the guide's chapter `id`.
    public static String guideLinkId(String id) {
        return "home-" + id;
    }

    /// **Read the guide**: the chapters with nothing to click, as the book's
    /// table of contents with a line under each entry.
    ///
    /// Links and not cards. A card is a place to try something, and these are
    /// places to read: a wall of thirty cards with nothing on them was a screen
    /// of its own that a reader had to find, where a contents list under the
    /// sections is where somebody who has just looked at the gallery goes next.
    private Widget guide() {
        var parts = GuideParts.PARTS.stream().map(HomeScreen::part).toList();
        return new Column(
                List.of(
                        new Text("Read the guide", Attributes.NONE.classes("home-section-title")),
                        new Text(
                                "The chapters to read rather than try: what Goldberry is, how to install it, what a"
                                        + " frame costs, how to test and ship an application, and how to contribute."
                                        + " Each opens in the browser.",
                                Attributes.NONE.classes("prose")),
                        new Masonry(
                                parts,
                                Masonry.UNSET,
                                TILE_WIDTH,
                                Attributes.NONE.id("home-guide-parts").classes("wall"))),
                Attributes.NONE.id("home-guide").classes("home-guide"));
    }

    /// One part of the book: its name over its chapters, each a link and a line.
    private static Widget part(GuideParts.Part part) {
        var rows = new ArrayList<Widget>(part.chapters().size() + 1);
        rows.add(new Text(part.title(), Attributes.NONE.classes("guide-part-title")));
        for (var chapter : part.chapters()) {
            var url = DocLink.page(chapter.page()).url();
            rows.add(new Column(
                    List.of(
                            Link.external(chapter.title(), url)
                                    .withAttributes(Attributes.NONE
                                            .id(guideLinkId(chapter.id()))
                                            .classes("guide-link")
                                            .tooltip(url)
                                            .name("Read " + chapter.title() + " in the guide")),
                            new Text(chapter.summary(), Attributes.NONE.classes("guide-summary"))),
                    Attributes.NONE.classes("guide-entry")));
        }
        var slug = part.title().toLowerCase(Locale.ROOT).replace(' ', '-');
        return new Column(rows, Attributes.NONE.id("guide-" + slug).classes("guide-part"));
    }

    private Widget hero() {
        var screens = Gallery.TABS.size();
        var cards = counts.values().stream().mapToInt(Integer::intValue).sum();
        return new Column(
                List.of(
                        new Text("Goldberry showcase", Attributes.NONE.classes("home-kicker")),
                        new Text("Every widget in the toolkit, in a window.", Attributes.NONE.classes("home-title")),
                        new Text(
                                screens + " screens and " + cards + " cards, one screen per chapter of the guide."
                                        + " Pick a section below, search for anything by name, or read the guide"
                                        + " at the bottom.",
                                Attributes.NONE.classes("prose")),
                        new Row(
                                List.of(
                                        new Pressable(
                                                "Search",
                                                openSearch,
                                                false,
                                                List.of(
                                                        new IconView("search")
                                                                .withAttributes(
                                                                        Attributes.NONE.classes("search-glyph")),
                                                        new Text(
                                                                "Search screens, cards and commands…",
                                                                Attributes.NONE.classes("search-placeholder")),
                                                        new Spacer(),
                                                        new Text("Ctrl K", Attributes.NONE.classes("kbd"))),
                                                Attributes.NONE
                                                        .id("home-search")
                                                        .classes("search-field")),
                                        new Button("Take the tour", startTour)
                                                .withAttributes(Attributes.NONE
                                                        .id("home-tour")
                                                        .classes("primary"))),
                                Attributes.NONE.classes("home-actions"))),
                Attributes.NONE.classes("home-hero"));
    }

    private Widget recently() {
        var row = new ArrayList<Widget>();
        row.add(new Text("Recently", Attributes.NONE.classes("home-recent-label")));
        for (var name : recent) {
            var tab = Gallery.tab(name);
            row.add(new Pressable(
                    tab.title(),
                    () -> pick.accept(name),
                    false,
                    List.of(
                            new IconView(tab.icon()).withAttributes(Attributes.NONE.classes("nav-icon")),
                            new Text(tab.title())),
                    Attributes.NONE.classes("home-chip")));
        }
        return new Row(row, Attributes.NONE.id("home-recent"));
    }

    private Widget tile(Section section) {
        var tabs = Gallery.in(section);
        var children = new ArrayList<Widget>();
        children.add(new Row(
                List.of(
                        new IconView(section.icon()).withAttributes(Attributes.NONE.classes("home-tile-icon")),
                        new Text(section.title(), Attributes.NONE.classes("home-tile-title")),
                        new Spacer(),
                        new Text(
                                tabs.size() + (tabs.size() == 1 ? " screen" : " screens"),
                                Attributes.NONE.classes("caption"))),
                Attributes.NONE.classes("home-tile-head")));
        for (var tab : tabs) {
            children.add(link(tab));
        }
        return new Card(
                children,
                Attributes.NONE
                        .id("home-" + section.name().toLowerCase(Locale.ROOT))
                        .classes("home-tile"));
    }

    private Widget link(GalleryTab tab) {
        var content = new ArrayList<Widget>();
        content.add(new IconView(tab.icon()).withAttributes(Attributes.NONE.classes("nav-icon")));
        content.add(new Text(tab.title(), Attributes.NONE.classes("nav-label")));
        var count = counts.get(tab.name());
        if (count != null && count > 0) {
            content.add(new Text(count + (count == 1 ? " card" : " cards"), Attributes.NONE.classes("nav-count")));
        }
        return new Pressable(
                tab.title(),
                () -> pick.accept(tab.name()),
                false,
                content,
                Attributes.NONE.id("home-link-" + tab.name()).classes("home-link"));
    }
}
