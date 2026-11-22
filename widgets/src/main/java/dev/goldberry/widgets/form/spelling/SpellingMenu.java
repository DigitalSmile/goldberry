package dev.goldberry.widgets.form.spelling;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.i18n.Labels;
import dev.goldberry.i18n.ToolkitLabel;
import dev.goldberry.text.spell.Misspelling;
import dev.goldberry.text.spell.SpellCheck;
import dev.goldberry.widget.Widget;
import dev.goldberry.widgets.menu.Item;

/// The rows a field's menu shows above Cut, Copy and Paste when it opens on a
/// misspelled word: up to five suggestions, *Add to dictionary* and *Ignore*.
///
/// ```java
/// spellingMenu.aim(check, text, caret);   // just before the menu opens
/// new TextMenu(host, this, () -> spellingMenu.rows(this::replace), List::of);
/// ```
///
/// [#aim] finds the misspelled word at an offset and asks for its suggestions
/// on the check's background thread. The menu opens at once, saying it is
/// finding them, and `refresh` redraws it when they arrive. Choosing one
/// replaces the word through the field, as one undo step.
public final class SpellingMenu {

    /// How many suggestions the menu offers at most.
    public static final int SUGGESTIONS = 5;

    /// How a field replaces `[start, end)` of its text with `with`, as one undo
    /// step.
    @FunctionalInterface
    public interface Replace {

        /// @return whether the text changed
        boolean replace(int start, int end, String with);
    }

    /// The word the menu is open on, as offsets into the field's whole text,
    /// with what its two last rows do. The rows hold the actions rather than the
    /// check, which the field owns and closes.
    private record Target(int start, int end, String word, Runnable addToDictionary, Runnable ignore) {}

    private final Runnable refresh;
    private @Nullable Target target;
    private @Nullable List<String> suggestions;

    /// @param refresh redraws the open menu: [dev.goldberry.widgets.form.edit.TextMenu#refresh]
    public SpellingMenu(Runnable refresh) {
        this.refresh = Objects.requireNonNull(refresh, "refresh");
    }

    /// Points the menu at the misspelled word at `offset` of `text`, the
    /// word the caret is in or just after, or at nothing when there is none or
    /// the field does not check.
    public void aim(@Nullable SpellCheck check, String text, int offset) {
        target = null;
        suggestions = null;
        if (check == null || offset < 0 || offset > text.length()) {
            return;
        }
        var lineStart = offset == 0 ? 0 : text.lastIndexOf('\n', offset - 1) + 1;
        var newline = text.indexOf('\n', lineStart);
        var line = text.substring(lineStart, newline < 0 ? text.length() : newline);
        Misspelling found = null;
        for (var word : check.misspellings(line)) {
            if (word.holds(offset - lineStart)) {
                found = word;
                break;
            }
        }
        if (found == null) {
            return;
        }
        var word = found.word();
        var aimed = new Target(
                lineStart + found.start(),
                lineStart + found.end(),
                word,
                () -> check.addToDictionary(word),
                () -> check.ignore(word));
        target = aimed;
        check.suggest(word, SUGGESTIONS, list -> {
            if (target == aimed) {
                suggestions = list;
                refresh.run();
            }
        });
    }

    /// Whether the menu is open on a misspelled word.
    public boolean isAimed() {
        return target != null;
    }

    /// The rows, as they stand: none when the menu is not on a misspelled word.
    public List<Widget> rows(Replace replace) {
        Objects.requireNonNull(replace, "replace");
        var aimed = target;
        if (aimed == null) {
            return List.of();
        }
        var rows = new ArrayList<Widget>();
        var found = suggestions;
        if (found == null) {
            rows.add(new Item(Labels.text(ToolkitLabel.FINDING_SUGGESTIONS)).disabled(true));
        } else if (found.isEmpty()) {
            rows.add(new Item(Labels.text(ToolkitLabel.NO_SUGGESTIONS)).disabled(true));
        } else {
            for (var suggestion : found) {
                rows.add(new Item(suggestion, () -> {
                    var _ = replace.replace(aimed.start(), aimed.end(), suggestion);
                }));
            }
        }
        rows.add(new Item(Labels.text(ToolkitLabel.ADD_TO_DICTIONARY), aimed.addToDictionary()));
        rows.add(new Item(Labels.text(ToolkitLabel.IGNORE_SPELLING), aimed.ignore()));
        return List.copyOf(rows);
    }
}
