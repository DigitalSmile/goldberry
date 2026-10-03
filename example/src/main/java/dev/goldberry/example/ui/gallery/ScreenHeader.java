package dev.goldberry.example.ui.gallery;

import java.util.List;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.Spacer;
import dev.goldberry.widgets.text.Text;

/// What every screen opens with: its heading and the link to its chapter, then a
/// line saying what the screen is for.
///
/// A [Wall] puts one over its cards; a screen that fills its tab, such as the
/// Markdown editor or the icon sheet, puts one over whatever it holds.
///
/// Read more: [Views](https://goldberry.dev/docs/applications.html#views).
///
/// @param title   the screen's heading
/// @param summary one or two sentences, held to [Summaries#require]
/// @param doc     the chapter the screen mirrors
public record ScreenHeader(String title, String summary, DocLink doc) implements Widget.Stateless {

    public ScreenHeader {
        Summaries.require("the " + title + " screen", summary);
    }

    @Override
    public Widget build(BuildContext context) {
        return new Column(
                List.of(
                        new Row(
                                List.of(
                                        new SectionHeader(title),
                                        new Spacer(),
                                        ShowcaseCard.docsLink("screen-docs", title, doc)),
                                Attributes.NONE.classes("screen-head")),
                        new Text(summary, Attributes.NONE.classes("prose"))),
                Attributes.NONE.classes("screen-intro"));
    }
}
