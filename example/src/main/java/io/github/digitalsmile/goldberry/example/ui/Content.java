package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;
import io.github.digitalsmile.goldberry.widgets.panel.tabs.Tab;
import io.github.digitalsmile.goldberry.widgets.panel.tabs.Tabs;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The main pane — the prose and the three action buttons.
///
/// **The one pane that is not a document**, and it is worth knowing why. Two
/// things here are conditional on the model rather than bound to it:
///
/// - the prose is *in the tree* only when the checkbox says so, which is a
///   structural change and not a value one;
/// - Undo and Reset are `disabled` when the click count is zero, and §8's markup
///   has no expressions — `disabled=#true` is a constant, and a document cannot
///   say "disabled when this property is zero".
///
/// Both are the ordinary reason a pane stays in Java, and neither is a gap in
/// the markup: a document that could evaluate `clicks == 0` would be code in a
/// data file, with no stack trace when it went wrong ([ADR-0062]).
///
/// @param model what it reads and what its buttons ask of
/// @param plus  the icon on the primary button — handed in, because a widget is
///              a value that is rebuilt and thrown away and an `Icon` owns native
///              memory that must be closed exactly once (ADR-0043)
public record Content(ShowcaseModel model, Icon plus) implements Widget.Stateless {

    private static final String PROSE = """
            Yoga proposes a width and this paragraph answers with a height, which is the only \
            thing a flexbox algorithm needs to know about text. The answer comes back through a \
            Java method called from C returning a struct by value — the fiddliest thing the \
            toolkit asks of the Foreign Function & Memory API, and the reason ADR-0017 exists.

            Drag the window's edge and the text re-wraps without being shaped again. Click a \
            button, or press Tab until one has the focus and then Space. Ctrl+T switches the \
            theme from the keyboard, and Ctrl+D switches the density — every control gets 4px \
            shorter, and nothing in this file mentions a height.

            The theme radios are one Tab stop, not two: Tab reaches the group and the arrow keys \
            move inside it. Tab away and back and you land on whichever is selected — including \
            after Ctrl+T has changed it from outside the group, because the selection is the \
            position rather than something remembered beside it.""";

    /// §5's strip, built here rather than in a document for the third reason this
    /// pane is in Java: **the list is dynamic**. A tab can be closed and a new one
    /// added, and KDL is data — it can write three tabs, not "however many the
    /// model has".
    ///
    /// Every one of the strip's three events reports and decides nothing: `change`
    /// asks to show a tab, `close` asks for one to go, `new` asks for one to
    /// arrive, and the model answers all three. A strip whose handlers did nothing
    /// would sit there unmoved, which is the visible form of "the model did not
    /// change" (ADR-0063, ADR-0107).
    private Widget tabs() {
        var strip = new java.util.ArrayList<Widget>();
        for (var name : model.tabs()) {
            strip.add(new Tab(name, name,
                    new Text("The " + name.toLowerCase(java.util.Locale.ROOT) + " tab."))
                    .closable(true)
                    // A colour a stylesheet cannot know: it is a fact about the
                    // tab's name rather than about its state.
                    .colour("Log".equals(name) ? 0xFFBF616A : 0));
        }
        return new Tabs(null, strip, model.tab(),
                model::pickTab, model::closeTab, model::newTab,
                io.github.digitalsmile.goldberry.widget.Attributes.NONE)
                .id("tabs");
    }

    @Override
    public Widget build(BuildContext context) {
        var actions = new Row(
                new Button("Click me", model::click)
                        .withIcon(plus).id("click").styled("primary"),
                new Button("Undo", model::undo).disabled(!model.hasClicks()).id("undo"),
                new Button("Reset", model::reset).disabled(!model.hasClicks())
                        .id("reset").styled("danger"))
                .id("actions");

        // `contextMenu` is an attribute like `id` is: any widget may carry one,
        // and right-clicking anywhere in this pane opens the menu the application
        // registered under that name (ADR-0108).
        return model.isProseShown()
                ? new Column(new Text(PROSE).id("prose"), tabs(), actions)
                        .id("content").contextMenu("content")
                : new Column(tabs(), actions).id("content").contextMenu("content");
    }
}
