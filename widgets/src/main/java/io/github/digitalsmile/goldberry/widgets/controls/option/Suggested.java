package io.github.digitalsmile.goldberry.widgets.controls.option;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;

/// A field whose suggestions a document names — `text-input suggestions=` and
/// `select options=` (ADR-0367).
///
/// ```kdl
/// text-input bind="city.typed" change="city.type" suggestions="city.matches"
/// ```
///
/// §4 says the application supplies the list and the widget raises the query.
/// In Java that is a rebuild with new options in answer to `change`; a document
/// has no rebuild, so it names the value the answer lands in. This node
/// subscribes to that value and describes the field again with whatever it holds
/// each time it changes.
///
/// A composition node with no CSS type: the field it builds is the styled node,
/// so a stylesheet and a hit test see exactly what they saw before.
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
