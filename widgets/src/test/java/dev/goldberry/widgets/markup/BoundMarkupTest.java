package dev.goldberry.widgets.markup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.bind.Property;
import dev.goldberry.bind.registry.ActionRegistry;
import dev.goldberry.bind.registry.BindingRegistry;
import dev.goldberry.kdl.KdlParser;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.Icons;
import dev.goldberry.widgets.Widgets;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.option.Suggested;
import dev.goldberry.widgets.controls.select.Select;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.panel.list.ListView;
import dev.goldberry.widgets.panel.table.Column;
import dev.goldberry.widgets.panel.table.Table;
import dev.goldberry.widgets.panel.tree.Tree;
import dev.goldberry.widgets.panel.tree.TreeNode;
import dev.goldberry.widgets.text.Text;

/// `list`, `table`, `tree` and autocomplete from a document: a `bind=` path
/// hands each of them the application's model, and the widget never copies it.
///
/// Read more: [Markup](https://goldberry.dev/docs/guide/markup.html#bind-is-a-path-and-nothing-else).
class BoundMarkupTest {

    private final BindingRegistry bindings = BindingRegistry.strict();

    private Widget inflate(String kdl) {
        var wiring = new Wiring(ActionRegistry.lenient(), Icons.none(), bindings);
        return Widgets.inflater(wiring).inflate(KdlParser.parse(kdl).getFirst());
    }

    private static ListView<String> names(String... names) {
        return new ListView<>(List.of(names), name -> name, Text::new);
    }

    @Nested
    @DisplayName("list, table and tree")
    class Described {

        @Test
        @DisplayName("a list is the ListView the binding holds, with the document's id and classes laid over")
        void list() {
            bindings.bind(
                    "app.people", Property.<Widget>of(names("Frodo", "Sam").styled("own")));

            var bound = assertInstanceOf(Bound.class, inflate("""
                    list bind="app.people" id="people" class="sidebar"
                    """));
            var built = assertInstanceOf(ListView.class, bound.build(null));

            assertEquals("people", built.attributes().id());
            assertEquals(Set.of("own", "sidebar"), built.attributes().classes());
        }

        @Test
        @DisplayName("a model that replaces the value rebuilds the node")
        void rebuildsOnChange() {
            var people = Property.<Widget>of(names("a"));
            bindings.bind("app.people", people);
            var tree = new ElementTree(inflate("""
                    list bind="app.people"
                    """));
            assertEquals(
                    List.of("a"),
                    ((ListView<?>) tree.root().children().getFirst().widget()).items());

            people.set(names("a", "b"));
            tree.flush();

            assertEquals(
                    List.of("a", "b"),
                    ((ListView<?>) tree.root().children().getFirst().widget()).items());
        }

        @Test
        @DisplayName("a table and a tree inflate the same way")
        void tableAndTree() {
            bindings.bind(
                    "app.table",
                    Property.<Widget>of(
                            new Table<>(List.of("x"), value -> value, List.of(Column.<String>of("v", "V", s -> s)))));
            bindings.bind("app.tree", Property.<Widget>of(new Tree(List.of(TreeNode.leaf("n", "Node")), null, null)));

            assertInstanceOf(Table.class, ((Bound) inflate("""
                    table bind="app.table"
                    """)).build(null));
            assertInstanceOf(Tree.class, ((Bound) inflate("""
                    tree bind="app.tree"
                    """)).build(null));
        }

        @Test
        @DisplayName("nothing bound, or the wrong kind of widget, draws nothing rather than failing")
        void nothingBound() {
            bindings.bind("app.wrong", Property.<Widget>of(new TextInput()));

            assertSame(
                    Widget.nothing(),
                    ((Bound) Widgets.inflater().inflate(KdlParser.parse("list").getFirst())).build(null));
            assertSame(Widget.nothing(), ((Bound) inflate("""
                    list bind="app.wrong"
                    """)).build(null));
        }
    }

    @Nested
    @DisplayName("slot")
    class Slot {

        @Test
        @DisplayName("a slot draws whatever widget the binding holds, with the document's id and classes laid over")
        void anyWidget() {
            bindings.bind("app.detail", Property.<Widget>of(new Text("Nothing selected").styled("empty")));

            var bound = assertInstanceOf(Bound.class, inflate("""
                    slot bind="app.detail" id="detail" class="pane"
                    """));
            var built = assertInstanceOf(Text.class, bound.build(null));

            assertEquals(Widget.class, bound.type());
            assertEquals("Nothing selected", built.content());
            assertEquals("detail", built.attributes().id());
            assertEquals(Set.of("empty", "pane"), built.attributes().classes());
        }

        @Test
        @DisplayName("a model that replaces the widget replaces what is drawn")
        void rebuildsOnChange() {
            var detail = Property.<Widget>of(new Text("Nothing selected"));
            bindings.bind("app.detail", detail);
            var tree = new ElementTree(inflate("""
                    slot bind="app.detail"
                    """));

            detail.set(new TextInput().placeholder("Name"));
            tree.flush();

            assertInstanceOf(TextInput.class, tree.root().children().getFirst().widget());
        }

        @Test
        @DisplayName("a widget with no attributes is drawn as it is")
        void unattributed() {
            Widget plain = Widget.nothing();
            bindings.bind("app.plain", Property.of(plain));

            assertSame(plain, ((Bound) inflate("""
                    slot bind="app.plain" id="ignored"
                    """)).build(null));
        }

        @Test
        @DisplayName("nothing bound, or a value that is not a widget, draws nothing")
        void nothingBound() {
            bindings.bind("app.word", Property.<Object>of("not a widget"));

            assertSame(
                    Widget.nothing(),
                    ((Bound) Widgets.inflater().inflate(KdlParser.parse("slot").getFirst())).build(null));
            assertSame(Widget.nothing(), ((Bound) inflate("""
                    slot bind="app.word"
                    """)).build(null));
        }
    }

    @Nested
    @DisplayName("autocomplete")
    class Autocomplete {

        @Test
        @DisplayName("text-input suggestions= offers what the binding holds, strings and options alike")
        void textInput() {
            bindings.bind("city.matches", Property.<Object>of(List.of("Bree", new Option("bag-end", "Bag End"))));

            var suggested = assertInstanceOf(Suggested.class, inflate("""
                    text-input suggestions="city.matches"
                    """));
            var field = assertInstanceOf(TextInput.class, suggested.build(null));

            assertEquals(
                    List.of("Bree", "bag-end"),
                    field.suggestions().stream().map(Option::value).toList());
        }

        @Test
        @DisplayName("select options= replaces the written options each time the binding changes")
        void select() {
            var matches = Property.<Object>of(List.of("Rohan"));
            bindings.bind("realm.matches", matches);
            var tree = new ElementTree(inflate("""
                    select autocomplete=#true options="realm.matches" { option value="gondor" "Gondor" }
                    """));

            var select = (Select) tree.root().children().getFirst().widget();
            assertEquals(
                    List.of("Rohan"),
                    select.options().stream().map(Option::value).toList());

            matches.set(List.of("Rohan", "Mordor"));
            tree.flush();

            select = (Select) tree.root().children().getFirst().widget();
            assertEquals(
                    List.of("Rohan", "Mordor"),
                    select.options().stream().map(Option::value).toList());
        }

        @Test
        @DisplayName("without the attribute, the field is the field")
        void plain() {
            assertInstanceOf(TextInput.class, inflate("text-input"));
            assertInstanceOf(Select.class, inflate("""
                    select { option value="a" "A" }
                    """));
        }
    }
}
