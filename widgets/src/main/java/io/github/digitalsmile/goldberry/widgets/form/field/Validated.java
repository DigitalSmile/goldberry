package io.github.digitalsmile.goldberry.widgets.form.field;

/// What a `form` can ask of a `field`.
///
/// The seam between the two, and public because they are in different packages —
/// a `field` keeps its parts to itself, which is ADR-0065's rule, and a form must
/// still be able to validate one.
///
/// Deliberately not "a field": it is the four questions a form has, and nothing
/// about labels, messages or layout. Anything else that wants to take part in a
/// form's submission — a `field-set`, a custom control an application wrote —
/// implements this and is treated identically.
public interface Validated {

    /// Runs the rule now, makes the field show what is wrong, and reports whether
    /// it passed.
    boolean check();

    /// Whether it *would* pass, without making it complain. A submit button asks
    /// this; it must not have side effects.
    boolean isValid();

    /// What it is complaining about, or `""`.
    String message();

    /// Forgets the complaint and that the user has been here.
    void clear();
}
