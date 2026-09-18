package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.bind.runtime.Models;
import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput;
import io.github.digitalsmile.goldberry.widgets.panel.tabs.Tab;
import io.github.digitalsmile.goldberry.widgets.panel.tabs.Tabs;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// A tab strip demonstrating itself, inside the tab strip that is showing it.
///
/// Nested on purpose. The gallery's own strip is *fixed* — thirteen screens, none of
/// them closable — and this one is everything a strip can be that the gallery's is
/// not: chapters that can be closed, a `+` that opens the next stage of the road,
/// and a tab coloured after what it holds.
///
/// **In Java, and the reason is the one KDL cannot argue with**: the list changes
/// while the window is open, and markup is data — it can write two chapters, not
/// "however many the model has"
/// (ADR-0110).
///
/// Every one of the strip's three events reports and decides nothing: `change`
/// asks to show a chapter, `close` asks for one to go, `new` asks for one to
/// arrive, and the model answers all three. A strip whose handlers did nothing
/// would sit there unmoved, which is the visible form of "the model did not
/// change"
/// (ADR-0063,
/// ADR-0107).
///
/// @param model what it reads and what its strip asks of
public record TabsDemo(ShowcaseModel model, ShowcaseModel.Actions actions) implements Widget.Stateless {

    private static final String NOTE = """
            Close a chapter and it fades out before it goes; open one and it fades in. \
            Neither can be a CSS transition: an arriving tab's element did not exist last \
            frame, and a departing one has already been dropped from the list above — so the \
            strip keeps it for the length of its departure and animates both from the frame \
            clock (ADR-0109). The strip keeps every chapter it has shown alive, so a note typed \
            under one is still there when you come back to it (ADR-0366). Drag a chapter along the \
            row to move it (ADR-0372).""";

    /// What each chapter's panel says. A sentence per stage rather than one
    /// sentence with the name substituted into it, because a strip of identical
    /// panels shows nothing about the panel being rebuilt when the tab changes.
    private static String body(String chapter) {
        return switch (chapter) {
            case "Rivendell" -> "The Council is called, and nine are chosen to answer it.";
            case "Moria" -> "The doors stand open on a hall that has been dark a long while.";
            default -> "Nothing has been written under " + chapter + " yet.";
        };
    }

    @Override
    public Widget build(BuildContext context) {
        var strip = new ArrayList<Widget>();
        for (var name : Models.<List<String>>observable(model, "app.tabs").get()) {
            strip.add(new Tab(
                            name,
                            name,
                            new Text(body(name)).id("tab-body"),
                            // Unbound on purpose: what is typed lives in the
                            // field's own state, which only `keep-alive` keeps.
                            new TextInput().placeholder("A note on " + name))
                    .closable(true)
                    // A colour a stylesheet cannot know: it is a fact about the
                    // chapter's name rather than about its state (ADR-0107).
                    .colour("Moria".equals(name) ? 0xFFBF616A : 0));
        }

        return Notifications.card(
                "chapters-card",
                "A strip that gains and loses chapters",
                List.of(
                        new Tabs(
                                        null,
                                        strip,
                                        Models.observable(model, "app.tab"),
                                        actions::pickTab,
                                        actions::closeTab,
                                        actions::newTab,
                                        Attributes.NONE)
                                .keepAlive(true)
                                .onReorder(actions::moveTab)
                                .id("demo-tabs"),
                        new Text(NOTE, Attributes.NONE.classes("caption")).id("tabs-note")));
    }
}
