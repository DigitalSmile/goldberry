package dev.goldberry.example.ui.forms.mention;

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

/// The suggestions under a query: a row per contact, the one `Enter` takes
/// marked `.active`, any of them taken with a click.
///
/// @param contacts what matches the query
/// @param active   the index of the marked row
/// @param onPick   what choosing a row does
public record MentionList(List<Contact> contacts, int active, Consumer<Contact> onPick) implements Widget.Stateless {

    /// The list's class, which `chapter-forms.css` styles.
    public static final String CLASS = "mention-list";

    public MentionList {
        contacts = List.copyOf(contacts);
        Objects.requireNonNull(onPick, "onPick");
    }

    @Override
    public Widget build(BuildContext context) {
        var rows = new ArrayList<Widget>(contacts.size());
        for (var index = 0; index < contacts.size(); index++) {
            rows.add(row(index, contacts.get(index)));
        }
        return new Column(rows, Attributes.NONE.classes(CLASS));
    }

    private Widget row(int index, Contact contact) {
        var classes = index == active ? new String[] {"mention-option", "active"} : new String[] {"mention-option"};
        return new Pressable(
                "Mention " + contact.name(),
                () -> onPick.accept(contact),
                false,
                List.of(
                        new Text(contact.name(), Attributes.NONE.classes("mention-name")),
                        new Text("@" + contact.handle(), Attributes.NONE.classes("mention-handle"))),
                Attributes.NONE.id("mention-" + contact.handle()).classes(classes));
    }
}
