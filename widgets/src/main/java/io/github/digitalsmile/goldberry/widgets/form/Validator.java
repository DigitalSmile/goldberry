package io.github.digitalsmile.goldberry.widgets.form;

import java.util.Objects;
import java.util.function.Predicate;

/// Whether a value is acceptable, and what to say when it is not —
/// `docs/core-widgets.md` §4's validation model.
///
/// ## It returns a message, not a boolean
///
/// A validator that answered yes or no would leave the *reason* somewhere else,
/// and the reason is the whole point: a field that goes red without saying why
/// is a field somebody has to guess at. So the result is either
/// [Result#valid()] or a message somebody can read, and there is nowhere for the
/// two to disagree.
///
/// The message is the application's words, not the toolkit's. A toolkit that
/// wrote "Invalid input" would be writing it in one language, in one register,
/// for every field in every application — and §4 already puts formatting and
/// parsing on the application for the same reason.
///
/// ## It does not know what the value means
///
/// `Validator<T>` is over whatever the field's control reports. For a
/// `text-input` that is a `String`, and it is a `String` even when it holds a
/// number: what the user typed is text until something parses it, and a
/// validator is exactly the thing that decides whether it *can* be parsed.
///
/// ## Composing
///
/// [#and] runs this and then the next, and reports the **first** failure. Not
/// all of them: a field's message slot is one line, and a list of three
/// complaints about one value is a worse message than the first one.
///
/// @param <T> what the field's control reports
@FunctionalInterface
public interface Validator<T> {

    /// The outcome — valid, or a message saying what is wrong.
    ///
    /// @param message what to show, or null when the value is acceptable
    record Result(String message) {

        /// Nothing wrong.
        public static final Result VALID = new Result(null);

        /// A failure with `message` on it.
        ///
        /// @throws IllegalArgumentException if the message is blank, because a
        ///         failure nobody can read is [#VALID] with a red border
        public static Result invalid(String message) {
            Objects.requireNonNull(message, "message");
            if (message.isBlank()) {
                throw new IllegalArgumentException(
                        "an invalid result has to say what is wrong; a blank message is a field"
                                + " that goes red and does not say why");
            }
            return new Result(message);
        }

        /// Whether the value passed.
        public boolean isValid() {
            return message == null;
        }
    }

    /// Checks `value`, which may be null when nothing has been entered.
    Result check(T value);

    /// This validator, then `next` — reporting the first failure.
    default Validator<T> and(Validator<T> next) {
        Objects.requireNonNull(next, "next");
        return value -> {
            var first = check(value);
            return first.isValid() ? next.check(value) : first;
        };
    }

    /// Accepts everything. What a field with no `Validator` has.
    static <T> Validator<T> none() {
        return value -> Result.VALID;
    }

    /// A validator from a predicate and the message for when it says no.
    ///
    /// The form most application validators want: the rule is the predicate and
    /// the words are the application's.
    static <T> Validator<T> of(Predicate<T> rule, String message) {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(message, "message");
        return value -> rule.test(value) ? Result.VALID : Result.invalid(message);
    }

    /// Refuses null and blank text — what `required=#true` means for anything
    /// whose value is a `String`.
    ///
    /// Blank and not merely empty: a field holding three spaces has not been
    /// filled in, and a form that accepted it would be one that a user has to
    /// discover the hard way.
    static Validator<String> required(String message) {
        return of(value -> value != null && !value.isBlank(), message);
    }

    /// A minimum length, counted in characters.
    static Validator<String> minLength(int length, String message) {
        return of(value -> value != null && value.length() >= length, message);
    }

    /// Text that matches `pattern` in full.
    ///
    /// An empty value **passes**, and that is deliberate: "this must look like an
    /// email address" and "this must be filled in" are two rules, and a pattern
    /// that also refused emptiness would make every optional field with a format
    /// into a required one. Combine with [#required] when both are meant.
    static Validator<String> matching(java.util.regex.Pattern pattern, String message) {
        Objects.requireNonNull(pattern, "pattern");
        return of(value -> value == null || value.isEmpty() || pattern.matcher(value).matches(),
                message);
    }
}
