package dev.goldberry.example.ui.gallery;

import static dev.goldberry.example.ui.gallery.Section.BASICS;
import static dev.goldberry.example.ui.gallery.Section.CONTENT;
import static dev.goldberry.example.ui.gallery.Section.CONTROLS;
import static dev.goldberry.example.ui.gallery.Section.DEMOS;
import static dev.goldberry.example.ui.gallery.Section.GRAPHICS;
import static dev.goldberry.example.ui.gallery.Section.PLATFORM;
import static dev.goldberry.example.ui.gallery.Section.STRUCTURE;

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
import dev.goldberry.example.ui.input.InputChapter;
import dev.goldberry.example.ui.layout.LayoutChapter;
import dev.goldberry.example.ui.layout.ScrollingChapter;
import dev.goldberry.example.ui.lookalike.LookalikeChapter;
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

/// Every screen in the gallery, grouped into the sidebar's [Section]s.
///
/// **The one list.** The sidebar is built from it, `Ctrl+1`… are bound off it in
/// this order, the search index names its screens, and the Edit ▸ Go to submenu
/// and the tray's Screens submenu name what it names. A screen added here
/// arrives in all of them.
///
/// The screens are listed section by section, so the next screen down the
/// sidebar is the next screen in this list. Within a section the order is the
/// guide's.
///
/// Read more: [The catalogue](https://goldberry.dev/docs/components/index.html).
public final class Gallery {

    /// The screens, section by section, in the sidebar's order.
    public static final List<GalleryTab> TABS = List.of(
            new GalleryTab("layout", "Layout", BASICS, "layout-dashboard", Fit.SCROLLED, LayoutChapter::new),
            new GalleryTab("scrolling", "Scrolling", BASICS, "scroll-text", Fit.FILLS, ScrollingChapter::new),
            new GalleryTab("text", "Text", BASICS, "type", Fit.SCROLLED, TextChapter::new),
            new GalleryTab("styling", "Styling", BASICS, "palette", Fit.SCROLLED, StylingChapter::new),
            new GalleryTab("design", "Design system", BASICS, "swatch-book", Fit.SCROLLED, DesignChapter::new),
            new GalleryTab("buttons", "Buttons", CONTROLS, "mouse-pointer-click", Fit.SCROLLED, ButtonsChapter::new),
            new GalleryTab("choices", "Choices", CONTROLS, "list-checks", Fit.SCROLLED, ChoicesChapter::new),
            new GalleryTab("values", "Values", CONTROLS, "sliders-horizontal", Fit.SCROLLED, ValuesChapter::new),
            new GalleryTab("forms", "Forms", CONTROLS, "text-cursor-input", Fit.SCROLLED, FormsChapter::new),
            new GalleryTab("panels", "Panels", STRUCTURE, "panels-top-left", Fit.SCROLLED, PanelsChapter::new),
            new GalleryTab("collections", "Collections", STRUCTURE, "rows-3", Fit.SCROLLED, CollectionsChapter::new),
            new GalleryTab("navigation", "Navigation", STRUCTURE, "compass", Fit.SCROLLED, NavigationChapter::new),
            new GalleryTab("menus", "Menus", STRUCTURE, "menu", Fit.SCROLLED, MenusChapter::new),
            new GalleryTab("overlays", "Overlays", STRUCTURE, "layers", Fit.SCROLLED, OverlaysChapter::new),
            new GalleryTab("windows", "Windows", STRUCTURE, "app-window", Fit.SCROLLED, WindowsChapter::new),
            new GalleryTab("charts", "Charts", GRAPHICS, "chart-line", Fit.SCROLLED, ChartsChapter::new),
            new GalleryTab("drawing", "Drawing", GRAPHICS, "pen-tool", Fit.SCROLLED, DrawingChapter::new),
            new GalleryTab(
                    "icons",
                    "Icons",
                    GRAPHICS,
                    "shapes",
                    Fit.FILLS,
                    context -> new IconsScreen(
                            context.model().sheets(), context.actions().sheets())),
            new GalleryTab(
                    "emoji",
                    "Emoji",
                    GRAPHICS,
                    "face-grinning",
                    Fit.FILLS,
                    context -> new EmojiScreen(
                            context.model().sheets(), context.actions().sheets())),
            new GalleryTab("gpu", "GPU", GRAPHICS, "cpu", Fit.SCROLLED, GpuChapter::new),
            new GalleryTab("audio", "Audio", GRAPHICS, "music", Fit.SCROLLED, AudioChapter::new),
            new GalleryTab("video", "Video", GRAPHICS, "video", Fit.SCROLLED, VideoChapter::new),
            new GalleryTab("markdown", "Markdown", CONTENT, "file-text", Fit.FILLS, MarkdownChapter::new),
            new GalleryTab("html", "HTML", CONTENT, "code", Fit.FILLS, HtmlChapter::new),
            new GalleryTab("web", "Web view", CONTENT, "globe", Fit.FILLS, WebChapter::new),
            new GalleryTab("input", "Input", PLATFORM, "keyboard", Fit.SCROLLED, InputChapter::new),
            new GalleryTab("diagnostics", "Diagnostics", PLATFORM, "activity", Fit.SCROLLED, DiagnosticsChapter::new),
            new GalleryTab("application", "Application", PLATFORM, "boxes", Fit.SCROLLED, ApplicationChapter::new),
            new GalleryTab("lookalike", "Lookalike", DEMOS, "headphones", Fit.FILLS, LookalikeChapter::new));

    private static final Map<String, GalleryTab> BY_NAME =
            TABS.stream().collect(Collectors.toUnmodifiableMap(GalleryTab::name, Function.identity()));

    private Gallery() {}

    /// The screens' names, in the sidebar's order.
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

    /// The screens listed under `section`, in the sidebar's order.
    public static List<GalleryTab> in(Section section) {
        return TABS.stream().filter(tab -> tab.section() == section).toList();
    }

    /// The screen `steps` along from `name` in the sidebar's order, wrapping at
    /// either end; the first screen for a name that is not a screen, such as
    /// Home.
    public static String step(String name, int steps) {
        var at = names().indexOf(name);
        if (at < 0) {
            return steps >= 0 ? TABS.getFirst().name() : TABS.getLast().name();
        }
        return TABS.get(Math.floorMod(at + steps, TABS.size())).name();
    }

    /// Whether `name` is one of the gallery's screens.
    public static boolean has(String name) {
        return BY_NAME.containsKey(name);
    }
}
