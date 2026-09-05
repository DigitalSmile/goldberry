package io.github.digitalsmile.goldberry.widget.semantics;

/// Whether a widget's arrival is worth interrupting a reader for —
/// `docs/core-widgets.md` §7's **live region**.
///
/// [Role] answers "what is this", and that is enough for everything a user
/// reaches: a button is read when the focus lands on it, because the focus
/// landing is the event. A `toast` has no such event. Nobody focuses it, nobody
/// clicks it, and it goes away on its own — so a reader that only speaks what is
/// focused says nothing at all about a notification, which is precisely the case
/// §7 names a live region for (ADR-0225).
///
/// ## Three values, and the middle one is the answer
///
/// - [#OFF] — read in document order like any other content, if at all. Every
///   widget in the catalog but one.
/// - [#POLITE] — announce it, at the next pause. What a notification wants: the
///   reader finishes the sentence it is on and then says the toast.
/// - [#ASSERTIVE] — announce it *now*, interrupting whatever is being read.
///   Nothing in the catalog uses it, and that is a statement rather than an
///   omission: interrupting is for something the user must act on before
///   anything else, and a toast is by construction dismissible and transient.
///   It exists because a vocabulary of two would make "polite" look like a
///   default rather than a choice.
///
/// ## Why this exists before the AccessKit bridge
///
/// [Role]'s own note applies unchanged: no platform API is touched here and
/// nothing is exported to a screen reader — that is M5. What it buys now is that
/// the widget half is *complete*, so the bridge has data to read rather than a
/// question to answer, and a toast raised today already carries everything an
/// announcement needs.
///
/// The shape is AccessKit's, which spells the same three as a `Live` property on
/// a node. This is the data rather than a rehearsal of it.
public enum Live {

    /// Not a live region. The default, and right for everything a user reaches.
    OFF,

    /// Announced at the next pause in whatever is being read.
    POLITE,

    /// Announced immediately, interrupting.
    ASSERTIVE,
}
