package dev.goldberry.example;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ui.AppMenu;
import dev.goldberry.example.ui.Screen;
import dev.goldberry.html.view.HtmlStyles;
import dev.goldberry.icon.Icon;
import dev.goldberry.markdown.view.MarkdownStyles;
import dev.goldberry.media.view.MediaStyles;
import dev.goldberry.text.font.Font;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.Icons;
import dev.goldberry.widgets.Widgets;

/// The showcase as a scene a test can photograph: the application's own
/// objects, so a picture is of the screen the window draws rather than of a
/// copy of its wiring.
///
/// Two tests take pictures of it — [GalleryGoldenTest], one golden per screen,
/// and `ScreenPicturesTest`, the guide's screens in both themes at twice the
/// detail — and they must agree on what a screen is. This is where
/// they agree. Confined to the thread that opened it: the icons and the font
/// are faces.
///
/// Call `RendererRequirement.enforce()` before opening one, so a machine with
/// no native library skips rather than fails in a constructor.
public final class ShowcaseScene implements AutoCloseable {

    private final Showcase showcase = new Showcase();
    private final ShowcaseModel model = modelOf(ShowcaseModel.class);
    private final ShowcaseModel.Actions actions = modelOf(ShowcaseModel.Actions.class);
    private final Icon palette = Icon.bundled("palette", 16);
    private final Icon plus = Icon.bundled("plus", 16);
    private final Font font = Font.bundled(BundledFont.UI, 13);

    private <T> T modelOf(Class<T> type) {
        return showcase.models().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }

    /// The application's values.
    public ShowcaseModel model() {
        return model;
    }

    /// The whole window, switched to `screen`.
    public Widget root(String screen) {
        actions.pickScreen(screen);

        var inflater = Widgets.inflater(
                // The objects the Forms document names, so this image is the
                // screen the application draws rather than one whose `form` lost
                // its controller to a lenient registry.
                model.named(),
                Icons.strict().bind("palette", palette).bind("plus", plus),
                showcase.models().toArray());
        return new Screen(
                model,
                actions,
                inflater,
                plus,
                () -> {},
                new AppMenu(
                        actions,
                        new AppMenu.Handlers(() -> {}, () -> {}, () -> {}, () -> {}, () -> {}, () -> {}),
                        plus));
    }

    /// The stylesheets the window loads for `theme`, at the model's density.
    public List<Stylesheet> stylesheets(Theme theme) {
        var sheets = new ArrayList<Stylesheet>(Controls.stylesheets(theme, model.density()));
        // The optional module's rules, exactly as `Showcase.stylesheets()` adds
        // them: the Panels wall holds a `markdown-view`, and a golden taken without
        // these would be a picture of a document the application never draws.
        sheets.add(MarkdownStyles.stylesheet());
        // And the module's other half, which the HTML screen is entirely made of.
        sheets.add(HtmlStyles.stylesheet());
        sheets.add(MediaStyles.stylesheet());
        sheets.addAll(ShowcaseStyles.sheets());
        return sheets;
    }

    /// The one UI face the gallery's goldens were taken with.
    public Font font() {
        return font;
    }

    @Override
    public void close() {
        palette.close();
        plus.close();
        font.close();
    }
}
