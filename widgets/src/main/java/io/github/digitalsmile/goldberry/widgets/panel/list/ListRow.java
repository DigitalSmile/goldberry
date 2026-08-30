package io.github.digitalsmile.goldberry.widgets.panel.list;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// One row of a [ListView] — a **part**, so it is styleable and not
/// constructible ([ADR-0065]).
///
/// ## It carries attributes, which no other part in the catalog does
///
/// §10 asks for "item context menus", and a menu is a **name on a widget**
/// ([ADR-0108]) that the launcher finds by walking up from an element and asking
/// whether its widget is [Attributed]. Naming it on whatever the item-factory
/// returned would work for a right-click and not for the keyboard: the menu key
/// walks up from the **focused** element ([ADR-0208]), and the focused element is
/// this row rather than anything inside it. So the row is where the name has to
/// live, and carrying an [Attributes] is how a widget says one.
///
/// Its `id` and classes are *not* the attributes', because both are computed —
/// the id is the focus name [ListState] moves the keyboard by, and `selected` is
/// a fact about the model. What the attributes carry here is the menu, and room
/// for whatever a later version needs to say per row.
///
/// ## The keyboard splits the same way a tree's does
///
/// `Up` and `Down` are the scope's ([ListBox]). `Enter` chooses, which is
/// `option`'s rule in a list and the same one: a set where the keyboard commits
/// must not choose before it. `Home`, `End` and type-to-select all need rows this
/// one cannot see, so each is a callback the list hands down — the shape
/// `TreeRow` established ([ADR-0209]).
///
/// @param id         this row's focus name, already scoped to its list
/// @param selectable whether it may be chosen — false when the list selects
///                   nothing
/// @param selected   whether it is a chosen row
/// @param content    what the item-factory returned
/// @param attributes what the row *says*: §10's per-item context menu
/// @param onSelect   asked to be chosen, **with the modifiers that were held** —
///                   `Ctrl` and `Shift` mean different things in a multi-select
///                   list, and only the list knows what they resolve to
/// @param onEnd      asked to move to the first or last row
/// @param onType     what was typed, for §10's type-to-select
record ListRow(
        String id,
        boolean selectable,
        boolean selected,
        Widget content,
        Attributes attributes,
        Consumer<Modifiers> onSelect,
        IntConsumer onEnd,
        Consumer<String> onType)
        implements Widget.Leaf, Styled, Paints, Handles, Attributed<ListRow>, Semantics {

    @Override
    public String cssType() {
        return "list-row";
    }

    /// The focus name, not the attributes' `id` — [ListState] moves the keyboard
    /// by it and nothing else sets one.
    @Override
    public String id() {
        return id;
    }

    /// Its item's identity, so a row keeps its element — and its focus — when the
    /// model is rebuilt or reordered. The rule §10 states for `list` keys and §3
    /// restates for a tree's expansion.
    @Override
    public Object key() {
        return id;
    }

    @Override
    public Set<String> classes() {
        return selected ? Set.of("selected") : Set.of();
    }

    /// Every row is a stop within the list's own scope, so the arrows rove
    /// between them and the list is one Tab stop from outside (§7.2).
    ///
    /// **A row is focusable even when nothing is selectable.** §10's `none` is a
    /// statement about what may be *chosen*, not about what may be read: a list
    /// nobody can select from is still one a keyboard user must be able to walk,
    /// or its rows are unreachable content.
    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public List<Widget> children() {
        return List.of(content);
    }

    @Override
    public ListRow withAttributes(Attributes value) {
        return new ListRow(id, selectable, selected, content, value, onSelect, onEnd, onType);
    }

    @Override
    public void onPointer(PointerEvent event) {
        if (event.kind() != PointerEvent.Kind.CLICKED) {
            return;
        }
        if (selectable) {
            // **The modifiers travel with it.** A click is `Ctrl`-clicked or
            // `Shift`-clicked or neither, and which of the three it was decides
            // what the new selection is -- a question only the list can answer,
            // because a range runs over rows this one cannot see.
            onSelect.accept(event.modifiers());
            event.consume();
        }
        // A row in a `NONE` list does **not** consume: there is nothing for the
        // click to mean here, and swallowing it would stop a button the
        // item-factory put on the row from ever being pressed.
    }

    @Override
    public void onKey(KeyEvent event) {
        if (event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        // **`Enter` is handled before the unmodified guard**, because it is the
        // one key here that means something different when a modifier is held:
        // `Ctrl+Enter` adds a row to a selection and `Shift+Enter` sweeps to it,
        // which are the keyboard's halves of the gestures the pointer has.
        // `Alt` and the platform key are nobody's here and fall through, so an
        // application's `Alt+Enter` accelerator still reaches it.
        if (event.key() == Key.ENTER) {
            if (selectable && !event.modifiers().alt() && !event.modifiers().meta()) {
                onSelect.accept(event.modifiers());
                event.consume();
            }
            return;
        }
        if (!event.modifiers().none()) {
            return;
        }
        switch (event.key()) {
            // §10's `Home`/`End`. To the ends of the **model** rather than of the
            // viewport, which is what every list means by it and what `Ctrl+End`
            // means in every document -- a list's own scrolling is a `scroll`
            // ancestor's business, and the focus ring is what asks it to follow
            // ([ADR-0120]).
            case HOME -> {
                onEnd.accept(-1);
                event.consume();
            }
            case END -> {
                onEnd.accept(1);
                event.consume();
            }
            default -> {}
        }
    }

    /// §10's "type-to-select when items expose text".
    ///
    /// A [TextEvent] rather than a key for `select`'s reason: what a typeahead
    /// wants is what was *typed*, and one character can take several keys.
    ///
    /// **Not consumed when there is no typeahead.** A list whose items expose no
    /// text hands `onType` down as null, and a row that swallowed the text anyway
    /// would stop a `text-input` elsewhere from ever seeing a keystroke that
    /// bubbled past it.
    @Override
    public void onText(TextEvent event) {
        if (event.text().isEmpty() || onType == null) {
            return;
        }
        onType.accept(event.text());
        event.consume();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    @Override
    public Role role() {
        return Role.ROW;
    }

    /// No name of its own: a row is named by the content it was handed.
    @Override
    public @Nullable String accessibleName() {
        return null;
    }
}
