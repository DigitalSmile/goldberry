package io.github.digitalsmile.goldberry.widgets.nav.steps;

import java.util.Locale;

/// Where one step stands — §6's four words.
///
/// Three of them are the list's to decide from its current index: everything
/// before it is [#DONE], it is [#CURRENT], everything after is [#UPCOMING].
/// [#ERROR] is the one a step says about itself, because only the application
/// knows that step three failed validation, and it overrides the other three:
/// a step that failed is neither done nor merely upcoming, wherever the index is.
public enum StepState {
    DONE,
    CURRENT,
    UPCOMING,
    ERROR;

    /// The CSS class this state adds to a `step`, and the word its accessible
    /// name ends with.
    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
