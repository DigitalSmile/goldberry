package io.github.digitalsmile.goldberry.weaver.models;

/// What a benchmark holds a model by, so the same call site reaches both forms.
///
/// A woven class is defined in a loader of its own ([io.github.digitalsmile.goldberry.weaver.Woven]),
/// so a benchmark cannot name it — and reaching it by `Method.invoke` would
/// measure reflection rather than the thing under test. An interface resolved
/// from the parent loader is the same type on both sides, so `clicker.click()` is
/// an ordinary interface call either way.
///
/// **Public, and its methods with it.** Two loaders mean two runtime packages, so
/// package-private would not be accessible across the split even though the
/// source sits in one package.
public interface Clicker {

    /// Changes exactly one bound value.
    void click();

    /// Reads it back off the field, with no binding in the way.
    int clicks();
}
