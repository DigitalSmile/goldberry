package dev.goldberry.example.ui.richinput.suggest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.goldberry.OpenedFrom;
import dev.goldberry.Placement;
import dev.goldberry.Popup;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widgets.form.richinput.MentionPrompt;
import dev.goldberry.widgets.form.richinput.Mentions;

/// The application's half of a query: it hears the field's query, looks it up
/// in the [Suggester] of the trigger that opened it, shows the matches in a
/// panel over the query, and chooses one with the keys or a click.
///
/// ```java
/// var picker = new SuggestionPicker(Map.of("@", Suggester.of(Directories.PEOPLE)), this::context);
/// new RichInput(draft, edited).mentions(picker.mentions());
/// ```
///
/// `Up` and `Down` move the marked row, `Enter` and `Tab` take it, and `Escape`
/// is left to the field, which closes the query and so this list. The panel is
/// an attached popup, which never takes the keyboard, so the field keeps it all
/// along. Where a host has no popups (a headless session) the keys still
/// choose: the list is a picture of [#matches()], not the place they live.
public final class SuggestionPicker implements AutoCloseable {

    private final SequencedMap<String, Suggester> sources;

    private final Supplier<BuildContext> opener;

    private @Nullable MentionPrompt prompt;

    private List<Suggestion> matches = List.of();

    private int active;

    private @Nullable Popup popup;

    private @Nullable LogicalRect shownAt;

    /// A picker answering each trigger of `sources` from its suggester, opening
    /// its panel from what `opener` gives: the field's own context, so the panel
    /// is styled as opened from it.
    public SuggestionPicker(Map<String, Suggester> sources, Supplier<BuildContext> opener) {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("a picker answers at least one trigger");
        }
        var ordered = new LinkedHashMap<String, Suggester>();
        sources.forEach((trigger, suggester) ->
                ordered.put(Objects.requireNonNull(trigger, "trigger"), Objects.requireNonNull(suggester, trigger)));
        this.sources = Collections.unmodifiableSequencedMap(ordered);
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    /// A picker that mentions `people` after `@`: the composer's.
    public static SuggestionPicker people(List<Contact> people, Supplier<BuildContext> opener) {
        return new SuggestionPicker(
                Map.of(
                        "@",
                        Suggester.of(people.stream().map(Contact::suggestion).toList())),
                opener);
    }

    /// The triggers this picker answers, in the order it was given them.
    public List<String> triggers() {
        return List.copyOf(sources.sequencedKeySet());
    }

    /// What to give the field: the triggers reported to [#query], the list
    /// keys to [#key].
    public Mentions mentions() {
        return Mentions.on(this::query, triggers().toArray(String[]::new)).keys(this::key);
    }

    /// The field's report: a query opened or changed, or none.
    public void query(Optional<MentionPrompt> next) {
        if (next.isEmpty()) {
            prompt = null;
            matches = List.of();
            active = 0;
            hide();
            return;
        }
        var open = next.get();
        prompt = open;
        var source = sources.get(open.query().trigger());
        var found = source == null
                ? List.<Suggestion>of()
                : source.suggest(open.query().query());
        if (!found.equals(matches)) {
            matches = found;
            active = 0;
        }
        if (matches.isEmpty()) {
            hide();
        } else {
            show(open.anchor());
        }
    }

    /// A list key while a query is open: whether it was taken.
    public boolean key(KeyEvent event) {
        if (prompt == null || matches.isEmpty()) {
            return false;
        }
        var shifted = event.modifiers().shift();
        return switch (event.key()) {
            case DOWN -> moveBy(1);
            case UP -> moveBy(-1);
            case ENTER, TAB -> !shifted && pick(matches.get(active));
            default -> false;
        };
    }

    private boolean moveBy(int step) {
        active = Math.floorMod(active + step, matches.size());
        var open = prompt;
        if (open != null) {
            show(open.anchor());
        }
        return true;
    }

    /// `suggestion` put in for the query.
    public boolean pick(Suggestion suggestion) {
        var open = prompt;
        return open != null && open.complete(suggestion.text(), suggestion.kind());
    }

    private void show(LogicalRect anchor) {
        var content = new SuggestionList(matches, active, this::pick);
        var open = popup;
        if (open != null && open.isOpen() && anchor.equals(shownAt)) {
            open.content(content);
            return;
        }
        hide();
        var context = opener.get();
        var host = context.host();
        if (host.isEmpty()) {
            return;
        }
        shownAt = anchor;
        OpenedFrom.call(
                        context,
                        () -> host.get().attachedPopup(content, anchor, Placement.ABOVE, 0, (list, size, area) -> list))
                .ifPresent(opened -> popup = opened.takesFocus(false).lightDismiss(true));
    }

    private void hide() {
        var open = popup;
        popup = null;
        shownAt = null;
        if (open != null && open.isOpen()) {
            open.close();
        }
    }

    /// What matches the open query, in the order it is listed.
    public List<Suggestion> matches() {
        return matches;
    }

    /// The index of the row `Enter` takes.
    public int active() {
        return active;
    }

    /// The panel away and the query forgotten.
    @Override
    public void close() {
        prompt = null;
        matches = List.of();
        hide();
    }
}
