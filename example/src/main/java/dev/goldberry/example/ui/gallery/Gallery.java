package dev.goldberry.example.ui.gallery;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.goldberry.example.ui.application.ApplicationChapter;
import dev.goldberry.example.ui.charts.ChartsChapter;
import dev.goldberry.example.ui.collections.CollectionsChapter;
import dev.goldberry.example.ui.content.HtmlChapter;
import dev.goldberry.example.ui.content.MarkdownChapter;
import dev.goldberry.example.ui.content.WebChapter;
import dev.goldberry.example.ui.controls.ButtonsChapter;
import dev.goldberry.example.ui.controls.ChoicesChapter;
import dev.goldberry.example.ui.controls.ValuesChapter;
import dev.goldberry.example.ui.diagnostics.DiagnosticsChapter;
import dev.goldberry.example.ui.drawing.DrawingChapter;
import dev.goldberry.example.ui.forms.FormsChapter;
import dev.goldberry.example.ui.gallery.GalleryTab.Fit;
import dev.goldberry.example.ui.gpu.GpuChapter;
import dev.goldberry.example.ui.guide.GuideChapter;
import dev.goldberry.example.ui.input.InputChapter;
import dev.goldberry.example.ui.layout.LayoutChapter;
import dev.goldberry.example.ui.layout.ScrollingChapter;
import dev.goldberry.example.ui.media.AudioChapter;
import dev.goldberry.example.ui.media.VideoChapter;
import dev.goldberry.example.ui.menus.MenusChapter;
import dev.goldberry.example.ui.navigation.NavigationChapter;
import dev.goldberry.example.ui.overlays.OverlaysChapter;
import dev.goldberry.example.ui.panels.PanelsChapter;
import dev.goldberry.example.ui.sheet.EmojiScreen;
import dev.goldberry.example.ui.sheet.IconsScreen;
import dev.goldberry.example.ui.styling.DesignChapter;
import dev.goldberry.example.ui.styling.StylingChapter;
import dev.goldberry.example.ui.text.TextChapter;
import dev.goldberry.example.ui.windows.WindowsChapter;

/// Every screen in the gallery, in the order the guide has its chapters.
///
/// **The one list.** The strip is built from it, `Ctrl+1`… are bound off it in
/// this order, and the Edit ▸ Go to submenu and the tray's Screens submenu name
/// what it names. A screen added here arrives in all four places.
///
/// The order is the book's: the layout part, then the components chapter by
/// chapter, then the developer guide's chapters, then a screen of reference cards
/// for the chapters with nothing to click. A reader who has a chapter of the guide
/// open finds the same screen one tab along from the last.
///
/// Read more: [The catalogue](https://goldberry.dev/docs/components/index.html).
public final class Gallery {

    /// The tabs, in the strip's order.
    public static final List<GalleryTab> TABS = List.of(
            new GalleryTab("layout", "Layout", Fit.SCROLLED, LayoutChapter::new),
            new GalleryTab("scrolling", "Scrolling", Fit.FILLS, ScrollingChapter::new),
            new GalleryTab("text", "Text", Fit.SCROLLED, TextChapter::new),
            new GalleryTab("buttons", "Buttons", Fit.SCROLLED, ButtonsChapter::new),
            new GalleryTab("choices", "Choices", Fit.SCROLLED, ChoicesChapter::new),
            new GalleryTab("values", "Values", Fit.SCROLLED, ValuesChapter::new),
            new GalleryTab("forms", "Forms", Fit.SCROLLED, FormsChapter::new),
            new GalleryTab("panels", "Panels", Fit.SCROLLED, PanelsChapter::new),
            new GalleryTab("collections", "Collections", Fit.SCROLLED, CollectionsChapter::new),
            new GalleryTab("navigation", "Navigation", Fit.SCROLLED, NavigationChapter::new),
            new GalleryTab("menus", "Menus", Fit.SCROLLED, MenusChapter::new),
            new GalleryTab("overlays", "Overlays", Fit.SCROLLED, OverlaysChapter::new),
            new GalleryTab("charts", "Charts", Fit.SCROLLED, ChartsChapter::new),
            new GalleryTab("drawing", "Drawing", Fit.SCROLLED, DrawingChapter::new),
            new GalleryTab("icons", "Icons", Fit.FILLS, context -> new IconsScreen(context.model(), context.actions())),
            new GalleryTab("emoji", "Emoji", Fit.FILLS, context -> new EmojiScreen(context.model(), context.actions())),
            new GalleryTab("markdown", "Markdown", Fit.FILLS, MarkdownChapter::new),
            new GalleryTab("html", "HTML", Fit.FILLS, HtmlChapter::new),
            new GalleryTab("web", "Web view", Fit.FILLS, WebChapter::new),
            new GalleryTab("audio", "Audio", Fit.SCROLLED, AudioChapter::new),
            new GalleryTab("video", "Video", Fit.SCROLLED, VideoChapter::new),
            new GalleryTab("gpu", "GPU", Fit.SCROLLED, GpuChapter::new),
            new GalleryTab("styling", "Styling", Fit.SCROLLED, StylingChapter::new),
            new GalleryTab("design", "Design system", Fit.SCROLLED, DesignChapter::new),
            new GalleryTab("input", "Input", Fit.SCROLLED, InputChapter::new),
            new GalleryTab("windows", "Windows", Fit.SCROLLED, WindowsChapter::new),
            new GalleryTab("diagnostics", "Diagnostics", Fit.SCROLLED, DiagnosticsChapter::new),
            new GalleryTab("application", "Application", Fit.SCROLLED, ApplicationChapter::new),
            new GalleryTab("guide", "Guide", Fit.SCROLLED, GuideChapter::new));

    private static final Map<String, GalleryTab> BY_NAME =
            TABS.stream().collect(Collectors.toUnmodifiableMap(GalleryTab::name, Function.identity()));

    private Gallery() {}

    /// The screens' names, in the strip's order.
    public static List<String> names() {
        return TABS.stream().map(GalleryTab::name).toList();
    }

    /// The tab called `name`. Refuses rather than defaults, because a defaulted
    /// tab is a menu row that opens nothing.
    public static GalleryTab tab(String name) {
        var tab = BY_NAME.get(name);
        if (tab == null) {
            throw new IllegalArgumentException("no screen is called \"" + name + "\"; the gallery is " + names());
        }
        return tab;
    }
}
