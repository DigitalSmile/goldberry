package io.github.digitalsmile.goldberry.widgets.form.field;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Handles;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// The node a stylesheet calls `field`, and the one told when focus leaves.
///
/// [Field] is stateful and styles nothing, so this carries the CSS type, the `id`
/// and the classes — the arrangement `scroll`, `tabs`, `select` and `text-input`
/// all use.
///
/// ## Why it is `Handles` at all
///
/// It takes no pointer and no key. It implements [Handles] for exactly one
/// method: [Handles#onFocusWithin], which is how a field learns that the user has
/// finished with the control inside it. That is §4's "validate on blur", and it
/// is the only definition of blur that works for a field holding two controls —
/// a date range, a pair of radio buttons — because it reports the subtree rather
/// than any one node.
///
/// @param label      the label text, or `""`
/// @param children   the control slot, as the document wrote it
/// @param required   whether the label carries the marker
/// @param message    what is wrong, or `""`
/// @param attributes the `id` and classes the document wrote
/// @param onBlur     called when focus leaves this field's subtree
record FieldBox(
        String label, List<Widget> children, boolean required, String message,
        Attributes attributes, Runnable onBlur)
        implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "field";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    /// `:invalid` — §4's, and the reason it is a pseudo-class rather than a class
    /// is that the specification names it (`docs/core-widgets.md` §1).
    ///
    /// A field is invalid when it has something to say. There is no second flag:
    /// a message and a failure are the same event, so they cannot disagree.
    @Override
    public boolean isInvalid() {
        return !message.isEmpty();
    }

    @Override
    public void onFocusWithin(boolean within, boolean fromKeyboard) {
        if (!within) {
            onBlur.run();
        }
    }

    /// §4's "click-to-focus": clicking the label focuses the control it names.
    ///
    /// A label is the control's **sibling**, so the router's walk up from what
    /// was pressed cannot reach it — this is the flag that makes the walk turn
    /// round and go down instead ([Handles#delegatesFocus()]).
    ///
    /// It costs nothing when the press landed on something real: the control is
    /// found on the way up before this node is reached, and so is a `button`
    /// inside the field. What it catches is the label, the message and the gap.
    @Override
    public boolean delegatesFocus() {
        return true;
    }

    /// The label, and everything else in a box of its own.
    ///
    /// **Two children and not three**, which is what makes both of §4's layouts
    /// come out of one structure: a label column is a *row* and a message below
    /// is a *column*, and a flat field can only be one of them. See [FieldBody].
    @Override
    public List<Widget> children() {
        var body = new ArrayList<Widget>(children.size() + 1);
        body.addAll(children);
        body.add(new FieldMessage(message));
        return List.of(new FieldLabel(label, required), new FieldBody(body));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // Nothing but the cascade decides this node's shape. §4's "consistent
        // label column (or stacked labels via class)" is two flex directions and
        // one class, which is a stylesheet's job entirely -- and making it one
        // here would be the widget overriding what a document asked for.
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// A field that reports nothing, for a test or a preview.
    static FieldBox of(String label, List<Widget> children) {
        return new FieldBox(label, children, false, "", Attributes.NONE, () -> {
        });
    }

}
