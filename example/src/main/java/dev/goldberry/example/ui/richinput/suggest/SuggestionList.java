package dev.goldberry.example.ui.richinput.suggest;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.pressable.Pressable;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.text.Text;

/// The suggestions under a query: a row per suggestion, the one `Enter` takes
/// marked `.active`, any of them taken with a click.
///
/// @param suggestions what matches the query
/// @param active      the index of the marked row
/// @param onPick      what choosing a row does
public record SuggestionList(List<Suggestion> suggestions, int active, Consumer<Suggestion> onPick)
        implements Widget.Stateless {

    /// The list's class, which `chapter-richinput.css` styles.
    public static final String CLASS = "suggestion-list";

    public SuggestionList {
        suggestions = List.copyOf(suggestions);
        Objects.requireNonNull(onPick, "onPick");
    }

    @Override
    public Widget build(BuildContext context) {
        var rows = new ArrayList<Widget>(suggestions.size());
        for (var index = 0; index < suggestions.size(); index++) {
            rows.add(row(index, suggestions.get(index)));
        }
        return new Column(rows, Attributes.NONE.classes(CLASS));
    }

    private Widget row(int index, Suggestion suggestion) {
        var classes =
                index == active ? new String[] {"suggestion-option", "active"} : new String[] {"suggestion-option"};
        var words = new ArrayList<Widget>(2);
        words.add(new Text(suggestion.title(), Attributes.NONE.classes("suggestion-title")));
        if (!suggestion.detail().isEmpty()) {
            words.add(new Text(suggestion.detail(), Attributes.NONE.classes("suggestion-detail")));
        }
        return new Pressable(
                "Choose " + suggestion.title(),
                () -> onPick.accept(suggestion),
                false,
                words,
                Attributes.NONE.id("suggestion-" + suggestion.id()).classes(classes));
    }
}
