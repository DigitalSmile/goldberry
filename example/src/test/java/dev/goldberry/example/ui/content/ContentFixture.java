package dev.goldberry.example.ui.content;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.example.Showcase;
import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.ShowcaseStyles;
import dev.goldberry.example.ui.gallery.Documents;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.html.view.HtmlStyles;
import dev.goldberry.icon.Icon;
import dev.goldberry.markdown.view.MarkdownStyles;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Density;
import dev.goldberry.widgets.Icons;
import dev.goldberry.widgets.Widgets;

/// One content screen in an element tree with a renderer, built from the
/// application's own model and documents, so what a test types into is the
/// screen the window shows.
///
/// Call `RendererRequirement.enforce()` before making one.
final class ContentFixture implements AutoCloseable {

    final Showcase showcase = new Showcase();
    final ShowcaseModel model = modelOf(ShowcaseModel.class);
    private final Icon plus = Icon.bundled("plus", 16);
    private final Fonts fonts = Fonts.bundled();
    private final WidgetRenderer renderer;
    final ElementTree tree;

    /// @param screen builds the screen from the gallery's context
    ContentFixture(Function<GalleryContext, Widget> screen) {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(Theme.NORD_DARK, Density.REGULAR));
        sheets.add(MarkdownStyles.stylesheet());
        sheets.add(HtmlStyles.stylesheet());
        sheets.addAll(ShowcaseStyles.sheets());
        renderer = new WidgetRenderer(sheets, fonts);
        var inflater = Widgets.inflater(
                model.named(), Icons.lenient(), showcase.models().toArray());
        var context = new GalleryContext(
                model, modelOf(ShowcaseModel.Actions.class), new Documents(inflater), plus, () -> {});
        tree = new ElementTree(screen.apply(context));
        frame();
    }

    private <T> T modelOf(Class<T> type) {
        return showcase.models().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    /// One frame: rebuild whatever is dirty, then render, as a window does.
    void frame() {
        tree.flush();
        renderer.render(tree);
    }

    /// Every widget of `type` on the screen, in tree order.
    <T> List<T> all(Class<T> type) {
        var found = new ArrayList<T>();
        collect(tree.root(), type, found);
        return found;
    }

    /// The first widget of `type` on the screen.
    <T> T first(Class<T> type) {
        var found = all(type);
        if (found.isEmpty()) {
            throw new AssertionError("no " + type.getSimpleName() + " on the screen");
        }
        return found.getFirst();
    }

    private static <T> void collect(Element element, Class<T> type, List<T> found) {
        if (type.isInstance(element.widget())) {
            found.add(type.cast(element.widget()));
        }
        element.children().forEach(child -> collect(child, type, found));
    }

    @Override
    public void close() {
        fonts.close();
        plus.close();
    }
}
