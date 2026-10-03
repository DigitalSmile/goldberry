package dev.goldberry.example.ui.styling;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.Wall;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// The **Styling** screen: the stylesheet subset, one card per section of the
/// chapter, in its order.
///
/// Almost every demonstration is a rule in `chapter-styling.css` worn by an
/// ordinary widget, because the chapter is about CSS: the cards show a
/// declaration and its result side by side. The few with state of their own —
/// the sheet probes, the typography field, the motion cards, the desktop's theme —
/// are their own classes.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html).
///
/// @param context what the screen is built from
public record StylingChapter(GalleryContext context) implements Widget.Stateless {

    /// The chapter this screen mirrors.
    static final DocLink CHAPTER = DocLink.page("guide/styling");

    @Override
    public Widget build(BuildContext buildContext) {
        var model = context.model();
        var actions = context.actions();
        return Wall.of(
                "styling",
                "Styling",
                "A small CSS: four fixed layers, a closed set of selectors and properties, custom properties for"
                        + " everything a theme decides, and transitions that cannot run layout.",
                CHAPTER,
                List.of(
                        SheetCards.sheets(),
                        SheetCards.cascade(),
                        SheetCards.selectors(),
                        SheetCards.parts(),
                        SheetCards.properties(),
                        PropertyCards.box(),
                        PropertyCards.textFlow(),
                        new TextStylingCard(),
                        PropertyCards.colour(),
                        PropertyCards.gradients(),
                        PropertyCards.borders(),
                        PropertyCards.transform(),
                        new MotionCards.Settling(),
                        MotionCards.keyframes(),
                        new MotionCards.Entering(),
                        PropertyCards.media(),
                        PropertyCards.cursor(),
                        SheetCards.customProperties(),
                        SheetCards.inheritance(),
                        ThemeCards.restyle(model, actions),
                        ThemeCards.themes(model, actions),
                        new ThemeCards.Desktop(actions, context.capabilities()),
                        ThemeCards.textScale()));
    }
}
