package dev.goldberry.example.ui.styling;

import java.util.List;

import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.panel.Panel;
import dev.goldberry.widgets.text.Text;

/// The few shapes the two screens' demonstrations are built from, so a card
/// says what it shows rather than how a row is spelled.
///
/// Every class these hand out is a rule in `chapter-styling.css`: a demonstration
/// on these screens is a declaration, and the Java only names which one applies.
final class Demo {

    private Demo() {}

    /// A line telling the reader what to try.
    static Text caption(String text) {
        return new Text(text, Attributes.NONE.classes("caption"));
    }

    /// A line of CSS, set in the code face, so a box can say which declaration
    /// it is wearing.
    static Text code(String text) {
        return new Text(text, Attributes.NONE.classes("styling-code"));
    }

    /// A wrapping row of demonstrations, with an id a stylesheet and a test can
    /// find it by.
    static Row row(String id, Widget... children) {
        return row(id, List.of(children));
    }

    /// The same, over a list.
    static Row row(String id, List<? extends Widget> children) {
        return new Row(List.copyOf(children), Attributes.NONE.id(id).classes("styling-row"));
    }

    /// A column of demonstrations.
    static Column column(String id, Widget... children) {
        return new Column(List.of(children), Attributes.NONE.id(id).classes("styling-column"));
    }

    /// A panel holding one line of text, wearing `classes`: the box most of the
    /// declarations on the Styling screen are shown on.
    static Panel box(String text, String... classes) {
        return new Panel(List.of(new Text(text)), Attributes.NONE.classes(withBase("styling-box", classes)));
    }

    /// The same box, with an id.
    static Panel idBox(String id, String text, String... classes) {
        return new Panel(List.of(new Text(text)), Attributes.NONE.id(id).classes(withBase("styling-box", classes)));
    }

    /// A swatch: a plate wearing `classes` with its name under it.
    static Column swatch(String name, String... classes) {
        return new Column(
                List.of(new Panel(List.of(), Attributes.NONE.classes(withBase("styling-plate", classes))), code(name)),
                Attributes.NONE.classes("styling-swatch"));
    }

    private static String[] withBase(String base, String... classes) {
        var all = new String[classes.length + 1];
        all[0] = base;
        System.arraycopy(classes, 0, all, 1, classes.length);
        return all;
    }
}
