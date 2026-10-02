package dev.goldberry.widgets.nav.steps;

import java.util.Locale;

/// Where one step stands: done, current, upcoming, passed but not done, or in
/// error.
///
/// Three of them are the list's to decide from its current index: everything
/// before it is [#DONE], it is [#CURRENT], everything after is [#UPCOMING].
/// A step may say whether it is complete, and then that word wins over the
/// position: a step before the current one that is not complete is
/// [#INCOMPLETE] — visited and left undone — and one after it that is
/// complete is [#DONE]. [#ERROR] is the other word a step says about itself,
/// because only the application knows that step three failed validation, and
/// it overrides everything: a step that failed is neither done nor merely
/// upcoming, wherever the index is.
///
/// Read more: [Navigation](https://goldberry.dev/docs/components/navigation.html#steps).
public enum StepState {
    DONE,
    CURRENT,
    UPCOMING,
    INCOMPLETE,
    ERROR;

    /// The CSS class this state adds to a `step`, and the word its accessible
    /// name ends with.
    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }
}
