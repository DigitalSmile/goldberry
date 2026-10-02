package dev.goldberry.widget.semantics;

/// Whether a widget's arrival is worth interrupting a reader for: whether it is a
/// **live region**, and how urgently.
///
/// [Role] answers "what is this", and that is enough for everything a user
/// reaches: a button is read when the focus lands on it, because the focus
/// landing is the event. A `toast` has no such event. Nobody focuses it, nobody
/// clicks it, and it goes away on its own, so a reader that only speaks what is
/// focused would say nothing at all about a notification. A live region is the
/// answer to that.
///
/// - [#OFF]: read in document order like any other content, if at all. Every
///   widget in the catalogue but one.
/// - [#POLITE]: announce it, at the next pause. What a notification wants: the
///   reader finishes the sentence it is on and then says the toast.
/// - [#ASSERTIVE]: announce it *now*, interrupting whatever is being read.
///   Nothing in the catalogue uses it, on purpose: interrupting is for something
///   the user must act on before anything else, and a toast is by construction
///   dismissible and transient. It exists because a vocabulary of two would make
///   "polite" look like a default rather than a choice.
///
/// No platform API reads this yet; the widget half is complete so that a bridge
/// to the platform has data to read rather than a question to answer. The three
/// values are the ones such a bridge would spell.
///
/// Read more:
/// [Semantics: a role and a name](https://goldberry.dev/docs/guide/writing-a-widget.html#semantics-a-role-and-a-name).
public enum Live {

    /// Not a live region. The default, and right for everything a user reaches.
    OFF,

    /// Announced at the next pause in whatever is being read.
    POLITE,

    /// Announced immediately, interrupting.
    ASSERTIVE,
}
