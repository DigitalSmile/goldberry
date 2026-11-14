package dev.goldberry.i18n;

/// Which question a plural rule answers: how many (`1 file`, `2 files`), or
/// which in order (`1st`, `2nd`, `3rd`).
///
/// Read more: [Text](https://goldberry.dev/docs/guide/text.html).
public enum PluralType {
    /// Counting: `{n, plural, …}`.
    CARDINAL,
    /// Ordering: `{n, selectordinal, …}`.
    ORDINAL
}
