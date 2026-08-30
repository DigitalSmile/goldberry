package io.github.digitalsmile.goldberry.widgets.data;

import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

/// `docs/testing.md` §1.1's property-based half, over the three pieces of the
/// chart stack that have invariants rather than answers.
///
/// ## Why these three and not the painters
///
/// A property test needs a statement that is true of *every* input, and most of
/// this toolkit does not have one — "the button looks right" is a golden, not a
/// predicate. These three do:
///
/// - A [Scale] is a bijection, so `from(at(v))` is `v`.
/// - [Lttb] chooses points, so its output is a subsequence of its input — the
///   invariant testing.md names as "M4 envelope ⊆ raw min/max".
/// - [Ticks] labels a range, so its labels are evenly spaced, ordered, and
///   enough of them to be an axis.
///
/// Each of those is worth a generator precisely because the failures live at the
/// edges nobody writes an example for: a zero-width domain, a threshold of
/// exactly three, a range that runs backwards because the y axis does.
///
/// ## Seeded, like everything else here
///
/// jqwik reports the seed of a failing run and replays it. That is the same
/// bargain §0.1 strikes everywhere else in this suite — randomness is allowed
/// when it is reproducible, and nowhere else.
class SeriesPropertyTest {

    private static final double EPSILON = 1e-9;

    // --- Scale: a bijection ---------------------------------------------------

    /// The round-trip. `at` maps a value to a coordinate and `from` maps it back,
    /// and a scale that lost anything in between would put a crosshair somewhere
    /// its own line is not (ADR-0206).
    @Property
    void linearScaleRoundTrips(
            @ForAll @DoubleRange(min = -1e6, max = 1e6) double value, @ForAll("domains") Scale scale) {

        var back = scale.from(scale.at(value));

        // Relative, because a domain of ±1e6 has no business being compared to an
        // absolute epsilon: one ulp there is far larger than 1e-9.
        var tolerance = Math.max(EPSILON, Math.abs(value) * 1e-9);
        if (Math.abs(back - value) > tolerance) {
            throw new AssertionError("at/from lost " + value + ": came back as " + back + " through " + scale);
        }
    }

    /// Order is preserved. Not implied by the round-trip — a scale that reversed
    /// its domain would still invert cleanly — and it is what makes "higher on
    /// the chart means more" true.
    @Property
    void linearScaleIsMonotonic(
            @ForAll @DoubleRange(min = -1e4, max = 1e4) double first,
            @ForAll @DoubleRange(min = -1e4, max = 1e4) double second,
            @ForAll("domains") Scale scale) {

        if (first >= second) {
            return;
        }
        var ascending = scale.rangeMin() <= scale.rangeMax();
        var a = scale.at(first);
        var b = scale.at(second);
        if (ascending ? a > b + EPSILON : a < b - EPSILON) {
            throw new AssertionError(first + " < " + second + " but mapped to " + a + " and " + b);
        }
    }

    /// A y axis runs the other way — bigger values are *higher*, which is a
    /// smaller coordinate — so the generator produces both range directions.
    ///
    /// The **domain** always ascends, and that is a deliberate narrowing rather
    /// than laziness: a backwards domain inverts the mapping a second time, so
    /// "which way should this come out" is the exclusive-or of two directions and
    /// the property above would be asserting the generator's arithmetic rather
    /// than the scale's. A backwards domain is also not a thing any caller
    /// builds — `Scale.linear(min, max, ...)` is named for its argument order.
    @Provide
    Arbitrary<Scale> domains() {
        var bounds = Arbitraries.doubles().between(-1e5, 1e5);
        return Arbitraries.of(true, false)
                .flatMap(inverted -> bounds.flatMap(low -> bounds.filter(high -> high - low > 1e-3)
                        .map(high -> inverted ? Scale.linear(low, high, 400, 0) : Scale.linear(low, high, 0, 400))));
    }

    // --- Lttb: a subsequence --------------------------------------------------

    /// Every point it keeps is a point it was given.
    ///
    /// The invariant that separates picking from averaging: an aggregation that
    /// returned a *mean* would draw a line through readings nobody took, which is
    /// the objection ADR-0201 raises about holes and the same one applies here.
    @Property
    void downsampleKeepsOnlyRealReadings(
            @ForAll("series") List<Double> values, @ForAll @IntRange(min = 0, max = 200) int threshold) {

        var kept = Lttb.downsample(values, threshold);

        var indices = Lttb.indices(values, threshold);
        for (var i = 0; i < kept.size(); i++) {
            if (!kept.get(i).equals(values.get(indices[i]))) {
                throw new AssertionError("kept a value that is not in the input at " + i);
            }
        }
    }

    /// It never returns more than asked for, and never fewer than it has to.
    ///
    /// Below a threshold of three there is no middle to choose from, so it hands
    /// everything back — the documented escape hatch, and the one a caller
    /// sizing an array depends on.
    @Property
    void downsampleRespectsItsThreshold(
            @ForAll("series") List<Double> values, @ForAll @IntRange(min = 0, max = 200) int threshold) {

        var kept = Lttb.downsample(values, threshold);

        var expected = threshold >= values.size() || threshold < 3 ? values.size() : threshold;
        if (kept.size() != expected) {
            throw new AssertionError("asked for " + threshold + " of " + values.size() + " and got " + kept.size()
                    + ", expected " + expected);
        }
    }

    /// The ends survive. A downsample that dropped the first or last reading
    /// would shorten the axis, and the chart would silently start somewhere the
    /// data does not.
    @Property
    void downsampleKeepsBothEnds(
            @ForAll("series") List<Double> values, @ForAll @IntRange(min = 3, max = 200) int threshold) {

        var kept = Lttb.downsample(values, threshold);

        if (!kept.getFirst().equals(values.getFirst()) || !kept.getLast().equals(values.getLast())) {
            throw new AssertionError("the ends moved: " + values.getFirst() + "…" + values.getLast() + " became "
                    + kept.getFirst() + "…" + kept.getLast());
        }
    }

    /// Indices come back in order, which is what lets a caller use one as an x.
    @Property
    void indicesAreOrderedAndInRange(
            @ForAll("series") List<Double> values, @ForAll @IntRange(min = 0, max = 200) int threshold) {

        var indices = Lttb.indices(values, threshold);

        for (var i = 0; i < indices.length; i++) {
            if (indices[i] < 0 || indices[i] >= values.size()) {
                throw new AssertionError("index " + indices[i] + " is outside the series");
            }
            if (i > 0 && indices[i] <= indices[i - 1]) {
                throw new AssertionError("indices are not ascending at " + i);
            }
        }
    }

    /// Non-empty, because `Lttb` reads `values.getFirst()` and a property about
    /// what it keeps has nothing to say about a series with nothing in it.
    @Provide
    Arbitrary<List<Double>> series() {
        return Arbitraries.doubles().between(-1e4, 1e4).list().ofMinSize(1).ofMaxSize(300);
    }

    // --- Ticks: an axis somebody can read -------------------------------------

    /// Whatever bounds it is handed, it produces an axis: at least two labels,
    /// a positive step, and labels that ascend.
    ///
    /// The bounds are generated in both orders, because `extended` promises to
    /// swap them and that promise is only worth having if something checks it.
    @Property
    void labellingIsAlwaysAnAxis(
            @ForAll @DoubleRange(min = -1e6, max = 1e6) double first,
            @ForAll @DoubleRange(min = -1e6, max = 1e6) double second,
            @ForAll @IntRange(min = 2, max = 12) int target) {

        // A series whose readings are all the same has no range to label, and
        // `extended` answers with a single label at the value. That is the right
        // answer and it is asserted by example in `flatSeriesGetsOneLabel` --
        // demanding two labels of it here would be demanding an axis be invented
        // for data that has none.
        if (first == second) {
            return;
        }

        var labelling = Ticks.extended(first, second, target);

        if (labelling.count() < 2) {
            throw new AssertionError("an axis with " + labelling.count() + " labels");
        }
        if (!(labelling.step() > 0)) {
            throw new AssertionError("a step of " + labelling.step() + " never terminates");
        }
        var values = labelling.values();
        for (var i = 1; i < values.size(); i++) {
            if (values.get(i) <= values.get(i - 1)) {
                throw new AssertionError("labels are not ascending: " + values);
            }
        }
    }

    /// The labelling is internally consistent: `values()` yields exactly `count`
    /// positions, and the last of them is `max`.
    ///
    /// This is what replaced a property asserting that the labels *cover* the
    /// data, which failed on `0…0.06` labelled `0…0.05` — and rightly. Coverage
    /// is one of four **scored** criteria in Talbot–Lin–Hanrahan, penalised in
    /// both directions and traded against simplicity and density; the class says
    /// so, and its own worked example labels `0…97` as `0…100`, running past the
    /// data rather than short of it. A property demanding coverage would be
    /// asserting a different algorithm.
    @Property
    void labellingIsInternallyConsistent(
            @ForAll @DoubleRange(min = -1e5, max = 1e5) double first,
            @ForAll @DoubleRange(min = -1e5, max = 1e5) double second,
            @ForAll @IntRange(min = 2, max = 12) int target) {

        var labelling = Ticks.extended(first, second, target);
        var values = labelling.values();

        if (values.size() != labelling.count()) {
            throw new AssertionError("count says " + labelling.count() + " and values() has " + values.size());
        }
        var last = labelling.min() + (labelling.count() - 1) * labelling.step();
        var slack = Math.max(1e-6, Math.abs(last) * 1e-9);
        if (Math.abs(last - labelling.max()) > slack) {
            throw new AssertionError("max is " + labelling.max() + " but min + (count-1)*step is " + last);
        }
    }

    /// The flat series, by example rather than by property: every reading the
    /// same, so there is no range and one label is the whole axis.
    @net.jqwik.api.Example
    void flatSeriesGetsOneLabel() {
        var labelling = Ticks.extended(5, 5, 5);

        if (labelling.count() != 1 || labelling.min() != 5 || labelling.step() != 0) {
            throw new AssertionError("a flat series should get one label at its value," + " and got " + labelling);
        }
    }
}
