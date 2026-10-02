package dev.goldberry.css.parse;

/// What the parser does with a construct the subset has not got: an unknown
/// pseudo-class, a pseudo-element, an attribute selector, a sibling combinator,
/// an at-rule other than `@media`, `@starting-style` and `@keyframes`.
///
/// ```java
/// CssParser.parseSheet(css, ParseMode.LENIENT, "app.css");
/// ```
///
/// [#STRICT] refuses the whole sheet with a [CssSyntaxException]. It is what the
/// toolkit's own sheets are parsed under, and what a test wants: a rule in
/// `controls.css` that matched nothing would be a control drawn wrong in every
/// application, and the build is the place to find out.
///
/// [#LENIENT] drops the **rule** that asked for it, logs one warning naming the
/// selector, the sheet and the line, and keeps everything else. It is what an
/// application's sheets are parsed under, because a stylesheet written for a
/// browser will name things this subset lacks, and an application that does
/// not start over a `:last-child` is worse than one drawn without that rule.
///
/// Neither mode forgives a sheet that is malformed rather than ambitious: an
/// unclosed block, a declaration with no value, a selector that is not one. Those
/// are mistakes, and hot reload depends on them being refused, because a file
/// saved halfway through an edit is one.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#strict-and-lenient-sheets).
public enum ParseMode {

    /// Anything outside the subset refuses the whole sheet.
    STRICT,

    /// Anything outside the subset drops its rule with a warning.
    LENIENT
}
