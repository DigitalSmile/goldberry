package dev.goldberry.example.ui.forms.mention;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

/// The application's half of a mention: it hears the field's query, looks the
/// people up, shows them in a panel over the query, and chooses one with the
/// keys or a click.
///
/// ```java
/// var picker = new MentionPicker(Contacts.ALL, this::context);
/// new RichInput(draft, edited).mentions(picker.mentions());
/// ```
///
/// `Up` and `Down` move the marked row, `Enter` and `Tab` take it, and `Escape`
/// is left to the field, which closes the query and so this list. The panel is
/// an attached popup, which never takes the keyboard, so the field keeps it all
/// along. Where a host has no popups (a headless session) the keys still
/// choose: the list is a picture of [#matches()], not the place they live.
public final class MentionPicker implements AutoCloseable {

    /// The character that opens a query.
    public static final String TRIGGER = "@";

    private final List<Contact> people;

    private final Supplier<BuildContext> opener;

    private @Nullable MentionPrompt prompt;

    private List<Contact> matches = List.of();

    private int active;

    private @Nullable Popup popup;

    private @Nullable LogicalRect shownAt;

    /// A picker over `people`, opening its panel from what `opener` gives: the
    /// composer's own context, so the panel is styled as opened from it.
    public MentionPicker(List<Contact> people, Supplier<BuildContext> opener) {
        this.people = List.copyOf(people);
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    /// What to give the field: [#TRIGGER] reported to [#query], the list keys
    /// to [#key].
    public Mentions mentions() {
        return Mentions.on(this::query, TRIGGER).keys(this::key);
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
        var found = Contacts.matching(people, open.query().query());
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

    /// `contact` put in for the query.
    boolean pick(Contact contact) {
        var open = prompt;
        return open != null && open.complete(contact.mentionText(), contact.kind());
    }

    private void show(LogicalRect anchor) {
        var content = new MentionList(matches, active, this::pick);
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

    /// Who matches the open query, in the order they are listed.
    public List<Contact> matches() {
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
