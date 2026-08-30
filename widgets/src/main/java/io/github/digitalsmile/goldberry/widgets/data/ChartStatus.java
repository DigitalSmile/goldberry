package io.github.digitalsmile.goldberry.widgets.data;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// Whether a chart has its data, is waiting for it, or could not get it.
///
/// `charts.md` §3.1: "a chart with no data draws a themed message, never an empty
/// grid". The three states are one type because they are one question with one
/// answer, and because a chart in any of them draws the same shape — a sentence
/// in the middle of the box the chart would have filled.
///
/// ## Why the chart owns this and not the application
///
/// An application can obviously write `loading ? spinner : chart`, and the
/// reason not to is what that costs: the chart is the thing with the **height**.
/// A `masonry` of cards whose charts came and went as their queries resolved
/// would reflow the whole wall twice per panel, and a spinner in a 156px box has
/// to be told to be 156px tall by somebody. A chart that keeps its own box
/// through its own loading is a wall that does not move.
///
/// ## Empty is not one of these
///
/// There is no `EMPTY`. A chart whose series are empty is [#READY] — the
/// application answered the question and the answer was "nothing" — and the
/// widget notices for itself. Making the application say it twice would be a
/// state that can disagree with the data beside it.
///
/// @param kind    which of the three
/// @param message what to say, or null for the default
public record ChartStatus(Kind kind, @Nullable String message) {

    /// Which of the three.
    public enum Kind {

        /// The data is whatever was passed, including nothing.
        READY,

        /// On its way. The chart keeps its box and says so.
        LOADING,

        /// It did not arrive, and the application knows why.
        FAILED
    }

    /// What a chart says when it has no data and nobody said otherwise.
    ///
    /// The second string this toolkit writes rather than the application, and
    /// here for [io.github.digitalsmile.goldberry.widgets.form.field.Field#REQUIRED_MESSAGE]'s
    /// reason: an application that passed an empty list has supplied no words,
    /// and a chart that drew axes over nothing would be asserting a scale nobody
    /// gave it. An application with better words passes them.
    public static final String NO_DATA = "No data";

    /// What a chart says while it waits, when nobody said otherwise.
    public static final String LOADING_MESSAGE = "Loading…";

    /// What a chart says when it failed and the application did not say why.
    ///
    /// It should always say why — [#failed] takes the reason — and this exists
    /// because a chart that failed silently is worse than one that admits it
    /// without details.
    public static final String FAILED_MESSAGE = "Could not load";

    /// The data is whatever was passed.
    public static final ChartStatus READY = new ChartStatus(Kind.READY, null);

    public ChartStatus {
        Objects.requireNonNull(kind, "kind");
        if (message != null && message.isBlank()) {
            message = null;
        }
    }

    /// Waiting, with the default wording.
    public static ChartStatus loading() {
        return new ChartStatus(Kind.LOADING, null);
    }

    /// Waiting, in the application's own words — "Querying Prometheus…".
    public static ChartStatus loading(String message) {
        return new ChartStatus(Kind.LOADING, message);
    }

    /// It failed, and this is why.
    ///
    /// The reason is the argument rather than an option because the application
    /// is the only thing that has one, and "Could not load" on its own tells a
    /// reader nothing they could act on.
    public static ChartStatus failed(String message) {
        return new ChartStatus(Kind.FAILED, message);
    }

    /// Whether this chart is showing data rather than a sentence.
    public boolean isReady() {
        return kind == Kind.READY;
    }

    /// What to draw, given whether the data turned out to be empty.
    ///
    /// Null when there is nothing to say, which is a chart that is ready and has
    /// something in it.
    @Nullable
    String messageFor(boolean hasData) {
        return switch (kind) {
            case READY -> hasData ? null : message == null ? NO_DATA : message;
            case LOADING -> message == null ? LOADING_MESSAGE : message;
            case FAILED -> message == null ? FAILED_MESSAGE : message;
        };
    }

    /// The class a stylesheet selects this state by — `empty`, `loading` or
    /// `failed`.
    ///
    /// A class rather than three CSS types, because what is drawn is the same
    /// node saying different words: `chart-message.failed` is one rule and three
    /// types would be three copies of the centring.
    @Nullable
    String styleClass(boolean hasData) {
        return switch (kind) {
            case READY -> hasData ? null : "empty";
            case LOADING -> "loading";
            case FAILED -> "failed";
        };
    }
}
