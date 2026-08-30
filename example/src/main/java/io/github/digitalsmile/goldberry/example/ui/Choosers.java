package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.widgets.controls.select.Select;
import io.github.digitalsmile.goldberry.widgets.panel.tree.TreeNode;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The Forms screen's three `select` options that are **not** a still picture.
///
/// ## Why these are Java where the rest of the screen is a document
///
/// The plain `select` on the Basic screen is markup, and it should be: its options
/// are a fixed list and its value is a bound property, which is exactly what a
/// document says well.
///
/// None of these three is:
///
/// - A **multiple** holds a *set*, and what is worth seeing is that `change` is a
///   **toggle** — a chip's × and a click on an already-chosen row are one channel,
///   and the set is the application's to edit ([ADR-0182]).
/// - An **autocomplete** raises what was typed and renders *whatever options it is
///   handed back*, and a document has no way to hand anything back ([ADR-0183]).
/// - A **tree**'s model is nodes with suppliers under them, which is a shape KDL
///   cannot write at all — `Select.inflate` passes `List.of()` for it in as many
///   words ([ADR-0184]).
///
/// So the state below is not scaffolding for a demo — it **is** the demonstration:
/// the toggling, the filtering and the fetching are the parts an application owns,
/// and a screen that hid them would be showing a picture of a control rather than
/// the control.
///
/// Three cards and three states, for [Notifications]'s reason: a masonry places by
/// column height, and each of these owns a value the other two never read.
public final class Choosers {

    private Choosers() {}

    /// The three chooser cards, in the order they are offered to the wall.
    public static List<Widget> cards() {
        return List.of(new Tongues(), new Places(), new Realms());
    }

    private static Widget caption(String text) {
        return new Text(text, Attributes.NONE.classes("caption"));
    }

    /// §3's `multiple`: the selection is a set, drawn as chips with a × on each.
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

        static final class TonguesState extends State<Tongues> {

            /// What a `multiple` holds: a set, edited here because it is the
            /// application's. Ordered by the options rather than by arrival, which
            /// is the control's own rule and costs this card nothing.
            private List<String> chosen = new ArrayList<>(List.of("sindarin", "westron"));

            /// §3's toggle. The set is the application's, so a value already in it
            /// can only be a request to take it out — which is what makes a chip's
            /// × and a click on a chosen row the same gesture.
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
                return Notifications.card(
                        "tongues-card",
                        "Several at once",
                        List.of(
                                caption("The selection is a set, drawn as chips with a × on each."
                                        + " Picking from the list does not close it, and change is a"
                                        + " toggle — so the × and a second click on a chosen row are"
                                        + " one channel."),
                                Select.of(Property.of(chosen), this::toggle, Tongues.TONGUES.toArray(Option[]::new))
                                        .multiple(true)
                                        .placeholder("Which tongues are spoken")
                                        .withAttributes(Attributes.NONE.id("tongues")),
                                caption(chosen.isEmpty() ? "None spoken" : "Holding: " + String.join(", ", chosen))));
            }
        }
    }

    /// §3's `autocomplete`: the closed control is an editable `text-input`, and
    /// the filtering is the application's.
    record Places() implements Widget.Stateful {

        /// Enough names to make a filter worth having, and deliberately with
        /// several sharing a first letter: a list where every prefix matches one
        /// row shows nothing about narrowing.
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

        static final class PlacesState extends State<Places> {

            /// The committed value, and the last thing typed at it.
            ///
            /// Two fields and not one, because they are two different facts:
            /// `place` is what the control *holds* and `query` is what somebody is
            /// currently typing at it. §3's `Esc` restores the first from the
            /// second, and the control does that itself — this card only has to
            /// filter.
            private String place = "";
            private String query = "";

            /// §3: "typing filters the options" — and the filtering is **here**,
            /// which is the whole point of the sentence. A remote-backed
            /// autocomplete would answer the same `query` from a service and the
            /// control would not know the difference.
            private List<Option> matches() {
                if (query.isBlank()) {
                    // Everything, which is what a combobox shows when it is opened
                    // rather than typed into.
                    return Places.PLACES.stream().map(Option::new).toList();
                }
                var needle = query.toLowerCase(Locale.ROOT);
                return Places.PLACES.stream()
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(needle))
                        .map(Option::new)
                        .toList();
            }

            private void typed(String text) {
                setState(() -> query = text);
            }

            /// A value was chosen or accepted. The control reports; this decides.
            private void chose(String value) {
                setState(() -> {
                    place = value;
                    // The query goes with it: what was typed was a way of reaching
                    // this value, and it has been reached.
                    query = "";
                });
            }

            @Override
            public Widget build(BuildContext context) {
                return Notifications.card(
                        "places-card",
                        "Type to narrow it",
                        List.of(
                                caption("The closed control is an editable text-input. Typing raises"
                                        + " the query and this card filters — Esc puts the committed"
                                        + " value back rather than clearing, and a name no place"
                                        + " matches is refused."),
                                new Select(place, this::chose, matches().toArray(Option[]::new))
                                        .autocomplete(this::typed)
                                        .placeholder("Where to next")
                                        .withAttributes(Attributes.NONE.id("places")),
                                caption(place.isEmpty() ? "Nowhere chosen" : "Bound for " + place)));
            }
        }
    }

    /// §3's `tree=`: the popup is a tree and a selection is a node.
    record Realms() implements Widget.Stateful {

        /// A model with a **lazy** branch in it, because that is the half of §3's
        /// tree a still list cannot show: "a node's children are fetched when it
        /// first expands". Nothing here reads a disk, but the supplier runs once
        /// and only when Rhovanion is opened — which is what a directory tree
        /// needs, and why a node draws a chevron before anyone knows what is
        /// under it.
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

        static final class RealmsState extends State<Realms> {

            private String realm = "";

            private void chose(String id) {
                setState(() -> realm = id);
            }

            @Override
            public Widget build(BuildContext context) {
                return Notifications.card(
                        "realms-card",
                        "A tree instead of a list",
                        List.of(
                                caption("Right opens a branch or steps into it, Left closes it or"
                                        + " steps out. A parent is not an answer — leaf-only is the"
                                        + " default, because Gondor is a heading. Rhovanion fetches"
                                        + " its children the first time it opens."),
                                new Select(realm, this::chose)
                                        .tree(Realms.REALMS)
                                        .placeholder("Choose a realm")
                                        .withAttributes(Attributes.NONE.id("realms")),
                                caption(realm.isEmpty() ? "No realm chosen" : "Chose: " + realm)));
            }
        }
    }
}
