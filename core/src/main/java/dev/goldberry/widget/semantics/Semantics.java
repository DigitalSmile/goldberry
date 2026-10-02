package dev.goldberry.widget.semantics;

import org.jspecify.annotations.Nullable;

/// What a widget tells something that cannot see it: what it is, and what it is
/// called.
///
/// ```java
/// @Override public Role role()                       { return Role.STATUS; }
/// @Override public @Nullable String accessibleName() { return attributes.name(); }
/// ```
///
/// Implemented by every focusable widget in the catalogue, which a sweep over the
/// catalogue's source enforces: a control a keyboard can reach and a screen
/// reader cannot name is the accessibility defect that is cheapest to prevent and
/// most expensive to find.
///
/// A separate interface rather than a method on `Handles`, because focusability
/// and describability are different questions with different answers. A `scroll`
/// viewport is focusable and is a region; a `text` is neither focusable nor
/// interactive and still has content worth reading. It is also what lets the
/// sweep be a *rule*: "focusable implies `Semantics`" is checkable, where
/// "focusable implies a sensible default" is not.
///
/// Read more:
/// [Semantics: a role and a name](https://goldberry.dev/docs/guide/writing-a-widget.html#semantics-a-role-and-a-name).
public interface Semantics {

    /// What this widget is.
    Role role();

    /// What it is called, or null when it has no name of its own.
    ///
    /// Null is an answer and not an omission: a `tab-close` button is named by
    /// the tab it belongs to, and a scroll viewport is usually named by whatever
    /// is above it. What the sweep forbids is a widget that has neither a name
    /// nor a reason recorded for not having one.
    ///
    /// The text is the **user's**, so it is whatever the widget was labelled
    /// with: not an id, not a CSS class, and not a type name.
    default @Nullable String accessibleName() {
        return null;
    }

    /// Whether this widget's arrival is worth interrupting a reader for: whether
    /// it is a **live region**.
    ///
    /// [Live#OFF] for everything a user reaches, which is why it is the default:
    /// a button is read when the focus lands on it, and the focus landing is the
    /// event. A `toast` has no such event. Nobody focuses it, nobody clicks it,
    /// and it goes away on its own, so it is the one thing in the catalogue whose
    /// *appearing* is the whole announcement.
    ///
    /// Answered on the widget rather than derived from [#role()], because the two
    /// are independent: the same role can be live in one place and not in
    /// another, and a role that implied it would make the choice unspellable.
    default Live live() {
        return Live.OFF;
    }
}
