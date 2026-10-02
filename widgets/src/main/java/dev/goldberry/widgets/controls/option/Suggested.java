package dev.goldberry.widgets.controls.option;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Observable;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;

/// A field whose suggestions a document names — what `text-input suggestions=`
/// and `select options=` build.
///
/// ```kdl
/// text-input bind="city.typed" change="city.type" suggestions="city.matches"
/// select autocomplete=#true query="places.search" options="places.matches"
/// ```
///
/// The application supplies the list and the widget raises the query. In Java
/// that is a rebuild with new options in answer to `change`; a document has no
/// rebuild, so it names the value the answer lands in. This node subscribes to
/// that value and describes the field again with whatever it holds each time it
/// changes.
///
/// A composition node with no CSS type: the field it builds is the styled node,
/// so a stylesheet and a hit test see exactly what they saw before.
///
/// Read more: [Fields and forms](https://goldberry.dev/docs/components/forms.html#text-input)
/// and [Choices](https://goldberry.dev/docs/components/choices.html#select).
///
/// @param source what the suggestions are: a collection of [Option]s, or of
///               anything else, which is offered as its string
/// @param field  the field, described with a given list
public record Suggested(@Nullable Observable<?> source, Function<List<Option>, Widget> field)
        implements Widget.Stateless {

    public Suggested {
        Objects.requireNonNull(field, "field");
    }

    @Override
    public Widget build(BuildContext context) {
        return field.apply(options(source == null ? null : source.get()));
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    /// What a bound value offers: its [Option]s as they are, and anything else as
    /// an option whose value and label are its string.
    public static List<Option> options(@Nullable Object value) {
        if (!(value instanceof Collection<?> many)) {
            return List.of();
        }
        var out = new ArrayList<Option>(many.size());
        for (var element : many) {
            switch (element) {
                case null -> {}
                case Option option -> out.add(option);
                default -> out.add(new Option(String.valueOf(element)));
            }
        }
        return List.copyOf(out);
    }
}
