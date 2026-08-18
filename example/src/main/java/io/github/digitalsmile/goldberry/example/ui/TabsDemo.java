package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.panel.tabs.Tab;
import io.github.digitalsmile.goldberry.widgets.panel.tabs.Tabs;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// The **Tabs** screen: a tab strip demonstrating itself, inside the tab strip
/// that is showing it.
///
/// Nested on purpose. The gallery's own strip is *fixed* — five screens, none of
/// them closable — and this one is everything a strip can be that the gallery's
/// is not: tabs that can be closed, a `+` that adds one, and a tab coloured after
/// what it holds.
///
/// **In Java, and the reason is the second of the two Panes states**: the list
/// changes while the window is open, and KDL is data — it can write three tabs,
/// not "however many the model has"
/// ([ADR-0110](../../../../../../../book/src/adr/0110-the-showcase-is-a-gallery-of-screens.md)).
///
/// Every one of the strip's three events reports and decides nothing: `change`
/// asks to show a tab, `close` asks for one to go, `new` asks for one to arrive,
/// and the model answers all three. A strip whose handlers did nothing would sit
/// there unmoved, which is the visible form of "the model did not change"
/// ([ADR-0063](../../../../../../../book/src/adr/0063-data-flows-down-events-flow-up.md),
/// [ADR-0107](../../../../../../../book/src/adr/0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)).
///
/// @param model what it reads and what its strip asks of
public record TabsDemo(ShowcaseModel model) implements Widget.Stateless {

    private static final String NOTE = """
            Close a tab and it fades out before it goes; add one and it fades in. \
            Neither can be a CSS transition: an arriving tab's element did not \
            exist last frame, and a departing one has already been dropped from \
            the list above — so the strip keeps it for the length of its \
            departure and animates both from the frame clock (ADR-0109).""";

    @Override
    public Widget build(BuildContext context) {
        var strip = new ArrayList<Widget>();
        for (var name : model.tabs().get()) {
            strip.add(new Tab(name, name,
                    new Text("The " + name.toLowerCase(Locale.ROOT) + " tab.").id("tab-body"))
                    .closable(true)
                    // A colour a stylesheet cannot know: it is a fact about the
                    // tab's name rather than about its state (ADR-0107).
                    .colour("Log".equals(name) ? 0xFFBF616A : 0));
        }

        return new Column(List.of(
                new Text("A strip that gains and loses tabs").styled("screen-title"),
                new Tabs(null, strip, model.tab(),
                        model::pickTab, model::closeTab, model::newTab,
                        io.github.digitalsmile.goldberry.widget.Attributes.NONE)
                        .id("demo-tabs"),
                new Text(NOTE).styled("caption").id("tabs-note")),
                io.github.digitalsmile.goldberry.widget.Attributes.NONE)
                .id("screen-tabs");
    }
}
