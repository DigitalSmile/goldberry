package dev.goldberry.example.ui.controls;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.goldberry.bind.Property;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.option.Option;
import dev.goldberry.widgets.controls.select.Select;
import dev.goldberry.widgets.panel.tree.TreeNode;
import dev.goldberry.widgets.text.Text;

/// The four kinds of `select`, a card each: one value with a placeholder,
/// several values as chips, a field to type in, and a tree.
///
/// Each card holds its own value. The toggling, the filtering and the fetching
/// are what an application owns, so they are written out here rather than hidden
/// behind a model field nothing else reads.
///
/// Read more: [`select`](https://goldberry.dev/docs/components/choices.html#select).
public final class Choosers {

    /// The section every card here links.
    static final DocLink SELECT = DocLink.to("components/choices", "select");

    private Choosers() {}

    /// The four chooser cards, in the order they are offered to the wall.
    public static List<Widget> cards() {
        return List.of(new Fellows(), new Tongues(), new Places(), new Realms());
    }

    private static Widget caption(String text) {
        return new Text(text, Attributes.NONE.classes("caption"));
    }

    /// A single `select` that starts empty, so its placeholder shows, with one
    /// option disabled.
    record Fellows() implements Widget.Stateful {

        private static final List<Option> FELLOWS = List.of(
                new Option("frodo", "Frodo Baggins"),
                new Option("sam", "Samwise Gamgee"),
                new Option("gandalf", "Gandalf the Grey"),
                new Option("boromir", "Boromir of Gondor").disabled(true),
                new Option("legolas", "Legolas Greenleaf"));

        @Override
        public State<?> createState() {
            return new FellowsState();
        }

        /// The chosen companion, empty until one is picked.
        static final class FellowsState extends State<Fellows> {

            private String chosen = "";

            @Override
            public Widget build(BuildContext context) {
                return new ShowcaseCard(
                                "choices-select",
                                "Select",
                                "A select is a closed control and a list that opens in a window of its own. It shows"
                                        + " the placeholder until something is chosen, and is as wide as its widest"
                                        + " option. Space or Down opens it, Enter chooses.",
                                SELECT)
                        .of(
                                new Select(
                                                chosen,
                                                value -> setState(() -> chosen = value),
                                                FELLOWS.toArray(Option[]::new))
                                        .placeholder("Choose a companion")
                                        .withAttributes(Attributes.NONE.id("fellows")),
                                caption(chosen.isEmpty() ? "Nobody chosen" : "Walking with " + label(chosen)));
            }

            private static String label(String value) {
                return FELLOWS.stream()
                        .filter(option -> option.value().equals(value))
                        .map(Option::label)
                        .findFirst()
                        .orElse(value);
            }
        }
    }

    /// A `select` with `multiple`: the selection is a set, drawn as chips with a × on each.
    record Tongues() implements Widget.Stateful {

        private static final List<Option> TONGUES = List.of(
                new Option("sindarin", "Sindarin"),
                new Option("quenya", "Quenya"),
                new Option("khuzdul", "Khuzdul"),
                new Option("rohirric", "Rohirric"),
                new Option("westron", "Westron"));

        @Override
        public State<?> createState() {
            return new TonguesState();
        }

        /// The set, edited here because it is the application's.
        static final class TonguesState extends State<Tongues> {

            private List<String> chosen = new ArrayList<>(List.of("sindarin", "westron"));

            /// The toggle: a value already in the set can only be a request to take
            /// it out, which makes a chip's × and a click on a chosen row one gesture.
            private void toggle(String value) {
                setState(() -> {
                    var next = new ArrayList<>(chosen);
                    if (!next.remove(value)) {
                        next.add(value);
                    }
                    chosen = next;
                });
            }

            @Override
            public Widget build(BuildContext context) {
                return new ShowcaseCard(
                                "tongues-card",
                                "Several at once",
                                "With multiple, the selection is a set drawn as chips with a × on each. The list"
                                        + " stays open while you pick, and change is a toggle: a × and a second click"
                                        + " on a chosen row are the same request.",
                                SELECT)
                        .of(
                                Select.of(Property.of(chosen), this::toggle, TONGUES.toArray(Option[]::new))
                                        .multiple(true)
                                        .placeholder("Which tongues are spoken")
                                        .withAttributes(Attributes.NONE.id("tongues")),
                                caption(chosen.isEmpty() ? "None spoken" : "Holding: " + String.join(", ", chosen)));
            }
        }
    }

    /// A `select` with `autocomplete`: the closed control is an editable
    /// `text-input`, and the filtering is the application's.
    record Places() implements Widget.Stateful {

        /// Enough names to make a filter worth having, several sharing a first
        /// letter so typing visibly narrows the list.
        private static final List<String> PLACES = List.of(
                "Bree",
                "Bag End",
                "Buckland",
                "Bruinen",
                "Rivendell",
                "Rohan",
                "Isengard",
                "Ithilien",
                "Lothlórien",
                "Lorien Eaves",
                "Moria",
                "Minas Tirith",
                "Mirkwood",
                "Osgiliath",
                "Edoras",
                "Emyn Muil",
                "Fangorn",
                "Gondor",
                "Helm's Deep",
                "Weathertop");

        @Override
        public State<?> createState() {
            return new PlacesState();
        }

        /// The committed value, and what is being typed at it: two facts, so two
        /// fields. `Esc` restores the first, and the control does that itself.
        static final class PlacesState extends State<Places> {

            private String place = "";
            private String query = "";

            /// Every place when nothing is typed, and the ones starting with the
            /// query otherwise. A remote-backed field would answer the same query
            /// from a service and the control would not know the difference.
            private List<Option> matches() {
                var needle = query.toLowerCase(Locale.ROOT);
                return PLACES.stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(needle))
                        .map(Option::new)
                        .toList();
            }

            private void typed(String text) {
                setState(() -> query = text);
            }

            private void chose(String value) {
                setState(() -> {
                    place = value;
                    query = "";
                });
            }

            @Override
            public Widget build(BuildContext context) {
                return new ShowcaseCard(
                                "places-card",
                                "Type to narrow it",
                                "With autocomplete, the closed control is a field. Typing raises the query, the"
                                        + " application answers with new options, and the list narrows. Esc puts the"
                                        + " committed value back, and a name no place matches is refused.",
                                SELECT)
                        .of(
                                new Select(place, this::chose, matches().toArray(Option[]::new))
                                        .autocomplete(this::typed)
                                        .placeholder("Where to next")
                                        .withAttributes(Attributes.NONE.id("places")),
                                caption(place.isEmpty() ? "Nowhere chosen" : "Bound for " + place));
            }
        }
    }

    /// A `select` with a tree: the popup is a tree and a selection is a node.
    record Realms() implements Widget.Stateful {

        /// A model with a lazy branch in it: Rhovanion's children are fetched the
        /// first time it opens, which is the half of a tree a still list cannot show.
        private static final List<TreeNode> REALMS = List.of(
                TreeNode.of(
                        "eriador",
                        "Eriador",
                        TreeNode.leaf("shire", "The Shire"),
                        TreeNode.leaf("bree", "Bree-land"),
                        TreeNode.leaf("imladris", "Rivendell")),
                TreeNode.of(
                        "gondor",
                        "Gondor",
                        TreeNode.leaf("minas-tirith", "Minas Tirith"),
                        TreeNode.leaf("ithilien", "Ithilien"),
                        TreeNode.leaf("osgiliath", "Osgiliath")),
                TreeNode.of(
                        "rohan",
                        "Rohan",
                        TreeNode.leaf("edoras", "Edoras"),
                        TreeNode.leaf("helms-deep", "Helm's Deep")),
                TreeNode.lazy(
                        "rhovanion",
                        "Rhovanion",
                        () -> List.of(
                                TreeNode.leaf("mirkwood", "Mirkwood"),
                                TreeNode.leaf("erebor", "Erebor"),
                                TreeNode.leaf("dale", "Dale"))));

        @Override
        public State<?> createState() {
            return new RealmsState();
        }

        /// The chosen node's id.
        static final class RealmsState extends State<Realms> {

            private String realm = "";

            private void chose(String id) {
                setState(() -> realm = id);
            }

            @Override
            public Widget build(BuildContext context) {
                return new ShowcaseCard(
                                "realms-card",
                                "A tree instead of a list",
                                "In Java a select can open a tree. Right opens a branch, Left closes it, and only a"
                                        + " leaf is an answer. Rhovanion fetches its children the first time it opens.",
                                SELECT)
                        .of(
                                new Select(realm, this::chose)
                                        .tree(REALMS)
                                        .placeholder("Choose a realm")
                                        .withAttributes(Attributes.NONE.id("realms")),
                                caption(realm.isEmpty() ? "No realm chosen" : "Chose: " + realm));
            }
        }
    }
}
