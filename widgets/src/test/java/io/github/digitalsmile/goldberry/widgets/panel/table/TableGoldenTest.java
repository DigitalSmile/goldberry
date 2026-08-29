package io.github.digitalsmile.goldberry.widgets.panel.table;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.golden.GoldenImage;
import io.github.digitalsmile.goldberry.paint.BoxPainter;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.panel.list.Selection;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What a `table` looks like — and it is the only proof of the one thing
/// [TableTest] cannot check: that **a header and the cells under it are the same
/// width**.
///
/// Every other assertion about the columns is about counts and keys, which would
/// all still pass if the header row and the body rows were sized by two
/// different rules. The picture is where a misaligned column is obvious
/// ([ADR-0214]).
class TableGoldenTest {

    private record Person(String id, String name, String home, int age) { }

    private static final List<Person> PEOPLE = List.of(
            new Person("fro", "Frodo", "The Shire", 50),
            new Person("sam", "Samwise", "The Shire", 38),
            new Person("ara", "Aragorn", "Gondor", 87),
            new Person("gim", "Gimli", "Erebor", 139));

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static final String SCENE = """
            table { padding: 8px; background: var(--gb-surface); width: 320px }
            """;

    private static List<Column<Person>> columns() {
        return List.of(
                // A weight of 2 against a weight of 1, so the picture also shows
                // that the shares are honoured rather than split evenly.
                Column.<Person>of("name", "Name", Person::name).sortable(true).weight(2),
                Column.<Person>of("home", "Home", Person::home).sortable(true),
                Column.<Person>of("age", "Age", p -> String.valueOf(p.age()))
                        .sortable(true).fixed(76));
    }

    private void paint(String name, Theme theme, Sort sort, Set<String> selected) {
        var host = new TestHost();
        var tree = new ElementTree(
                new Table<>(PEOPLE, Person::id, columns())
                        .sorted(sort, value -> { })
                        .selection(Selection.MULTIPLE)
                        .selected(selected, values -> { }),
                host);

        var renderer = new WidgetRenderer(
                List.of(Controls.baseStylesheet(), theme.load(),
                        Stylesheet.parse(CascadeLayer.APPLICATION, SCENE)),
                TestFont.get());

        GoldenImage.assertMatches(name, 320, 176, 1.0f,
                frame -> BoxPainter.paint(frame, renderer.render(tree)));
    }

    @Test
    @DisplayName("a table sorted by its first column, dark")
    void dark() {
        paint("table-dark", Theme.NORD_DARK, Sort.by("name"), Set.of("sam"));
    }

    @Test
    @DisplayName("and the same table on the light theme")
    void light() {
        paint("table-light", Theme.NORD_LIGHT, Sort.by("name"), Set.of("sam"));
    }

    /// The descending caret, which is the other half of the one value a picture
    /// is the only proof of: a caret pointing the wrong way says the column is
    /// sorted the other way.
    @Test
    @DisplayName("the caret turns over when the sort does")
    void descending() {
        paint("table-descending-dark", Theme.NORD_DARK, new Sort("age", true), Set.of());
    }

    /// Unsorted, which is a real state and not the absence of one: no caret
    /// anywhere, and every header at the same weight.
    @Test
    @DisplayName("an unsorted table draws no caret at all")
    void unsorted() {
        paint("table-unsorted-dark", Theme.NORD_DARK, null, Set.of());
    }
}
