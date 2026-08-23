package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import io.github.digitalsmile.goldberry.widgets.controls.select.Select;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// The **Choosers** screen: `docs/core-widgets.md` §3's two `select` options that
/// are not a still picture — `multiple` and `autocomplete`.
///
/// ## Why this screen is Java where the Controls screen is a document
///
/// The plain `select` on the Controls screen is markup, and it should be: its
/// options are a fixed list and its value is a bound property, which is exactly
/// what a document says well.
///
/// Neither of these is. A **multiple** holds a *set*, and what is worth seeing is
/// that `change` is a **toggle** — a chip's × and a click on an already-chosen
/// row are one channel, and the set is the application's to edit
/// ([ADR-0182](../../../../../../../book/src/adr/0182-a-select-may-hold-more-than-one.md)).
/// An **autocomplete** is worse: §3 says filtering is the application's, so the
/// control raises what was typed and renders *whatever options it is handed
/// back*, and a document has no way to hand anything back
/// ([ADR-0183](../../../../../../../book/src/adr/0183-a-combobox-is-a-select-you-can-type-in.md)).
///
/// So the state below is not scaffolding for a demo — it **is** the demonstration:
/// the toggling and the filtering are the parts an application owns, and a screen
/// that hid them would be showing a picture of a control rather than the control.
public record Choosers() implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new ChoosersState();
    }

    static final class ChoosersState extends State<Choosers> {

        /// What a `multiple` holds: a set, edited here because it is the
        /// application's. Ordered by the options rather than by arrival, which is
        /// the control's own rule and costs this screen nothing.
        private List<String> languages = new ArrayList<>(List.of("java", "rust"));

        /// The combobox's committed value, and the last thing typed into it.
        ///
        /// Two fields and not one, because they are two different facts: `city`
        /// is what the control *holds* and `query` is what somebody is currently
        /// typing at it. §3's `Esc` restores the first from the second, and the
        /// control does that itself — this screen only has to filter.
        private String city = "";
        private String query = "";

        private static final List<Option> LANGUAGES = List.of(
                new Option("java", "Java"),
                new Option("rust", "Rust"),
                new Option("kotlin", "Kotlin"),
                new Option("zig", "Zig"));

        private static final List<String> CITIES = List.of(
                "Amsterdam", "Antwerp", "Athens", "Barcelona", "Berlin", "Bergen",
                "Copenhagen", "Dublin", "Edinburgh", "Helsinki", "Lisbon", "Ljubljana",
                "London", "Madrid", "Oslo", "Porto", "Prague", "Reykjavik",
                "Stockholm", "Tallinn", "Vienna", "Zagreb");

        /// §3: "typing filters the options" — and the filtering is **here**,
        /// which is the whole point of the sentence. A remote-backed autocomplete
        /// would answer the same `query` from a server and the control would not
        /// know the difference.
        private List<Option> matches() {
            if (query.isBlank()) {
                // Everything, which is what a combobox shows when it is opened
                // rather than typed into.
                return CITIES.stream().map(Option::new).toList();
            }
            var needle = query.toLowerCase(Locale.ROOT);
            return CITIES.stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(needle))
                    .map(Option::new)
                    .toList();
        }

        /// §3's toggle. The set is the application's, so a value already in it can
        /// only be a request to take it out — which is what makes a chip's × and a
        /// click on a chosen row the same gesture.
        private void toggleLanguage(String value) {
            setState(() -> {
                var next = new ArrayList<>(languages);
                if (!next.remove(value)) {
                    next.add(value);
                }
                languages = next;
            });
        }

        private void typed(String text) {
            setState(() -> query = text);
        }

        /// A value was chosen or accepted. The control reports; this decides.
        private void chooseCity(String value) {
            setState(() -> {
                city = value;
                // The query goes with it: what was typed was a way of reaching
                // this value, and it has been reached.
                query = "";
            });
        }

        @Override
        public Widget build(BuildContext context) {
            var chosen = languages.isEmpty()
                    ? "nothing yet"
                    : String.join(", ", languages);

            return new Column(List.of(
                    new SectionHeader("Several at once"),
                    new Text("§3's multiple: the selection is a set, drawn as chips with a ×"
                            + " on each. Picking from the list does not close it, and change"
                            + " is a toggle — so the × and a second click on a chosen row are"
                            + " one channel.")
                            .withAttributes(Attributes.NONE.classes("caption")),
                    new Select(null, List.copyOf(LANGUAGES),
                            io.github.digitalsmile.goldberry.bind.Property.of(languages),
                            this::toggleLanguage, "Choose languages", true, false, false, null,
                            false, Attributes.NONE.id("languages"))
                            .placeholder("Choose languages"),
                    new Text("Holding: " + chosen).withAttributes(
                            Attributes.NONE.classes("caption")),

                    new SectionHeader("Type to narrow it"),
                    new Text("§3's autocomplete: the closed control is an editable text-input."
                            + " Typing raises the query and this screen filters — Esc puts the"
                            + " committed value back rather than clearing, and a name no city"
                            + " matches is refused.")
                            .withAttributes(Attributes.NONE.classes("caption")),
                    new Select(city, new ArrayList<Widget>(matches()), null,
                            this::chooseCity, "Pick a city", false, true, false, this::typed,
                            false, Attributes.NONE.id("city")),
                    new Text(city.isEmpty() ? "No city chosen" : "Chose: " + city)
                            .withAttributes(Attributes.NONE.classes("caption"))),
                    Attributes.NONE.id("choosers"));
        }
    }
}
