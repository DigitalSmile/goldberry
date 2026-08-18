package io.github.digitalsmile.goldberry.widgets.controls;


/// The curve between a control's **value** and its **position** along its track.
///
/// [io.github.digitalsmile.goldberry.widgets.controls.slider.Slider] places its thumb at a fraction of the travel and reads the pointer
/// back the same way ([ADR-0079]). Both directions go through here, so a control
/// whose value is not linear in what the ear or the eye does with it can say so
/// without any other part of the widget changing.
///
/// `docs/core-widgets.md` §3 asks for exactly one of these — "`fader` = vertical
/// variant with **optional dB scale mapping**" — and `knob`'s "taper" is the same
/// thing again, which is why this is an interface rather than a boolean on the
/// fader ([ADR-0080](../../../../../../../book/src/adr/0080-a-value-is-measured-along-a-part.md)).
///
/// ## Why it is a value and not a function
///
/// A [java.util.function.DoubleUnaryOperator] would be the obvious spelling and
/// is the wrong one: a widget is a **record**, and §11's parity invariant asserts
/// that the Java-built and KDL-built forms of a control are `equals`. Two lambdas
/// doing the same arithmetic never are. A sealed interface over records is, so
/// `scale="db"` in markup and [#decibels()] in Java produce the same value
/// ([ADR-0059]).
public sealed interface Scale {

    /// Where a value sits along the travel, `0..1`.
    ///
    /// Clamped to the track: a model outside the range is an application bug, and
    /// a thumb rendered off the end is a worse way to report it than a thumb
    /// pinned at the end.
    double toFraction(double value, double min, double max);

    /// The value at a position along the travel — the inverse of [#toFraction].
    double toValue(double fraction, double min, double max);

    /// What markup writes for this scale, and what [#of] parses.
    String token();

    /// Whether this scale can describe a `min..max` range at all.
    ///
    /// Checked when a [io.github.digitalsmile.goldberry.widgets.controls.slider.Slider] is constructed, so a fader over a range its scale
    /// cannot express fails at inflation rather than drawing a thumb at `NaN`.
    /// The default accepts everything the slider itself accepts.
    default void validate(double min, double max) {
    }

    /// Position is the value. What every control has unless it says otherwise.
    Scale LINEAR = new Linear();

    /// A fader in **decibels** over a linear gain, with silence at the bottom of
    /// the travel and unity at the top — `docs/core-widgets.md` §3's "dB scale
    /// mapping", and [#decibels(double)] with the usual −60 dB floor.
    static Scale decibels() {
        return new Decibels(-60);
    }

    /// A decibel fader whose travel starts at `floorDb` below `max`.
    ///
    /// @param floorDb how far down the scale reaches, in dB; negative
    static Scale decibels(double floorDb) {
        return new Decibels(floorDb);
    }

    /// The scale a document named, strictly.
    ///
    /// Strict for the reason every §9 registry is: `scale="dB"` resolving quietly
    /// to linear would give a fader that works and is wrong, which is the failure
    /// a typo in markup should never be able to produce ([ADR-0062]).
    static Scale of(String token) {
        if (token == null || token.isEmpty() || token.equals(LINEAR.token())) {
            return LINEAR;
        }
        if (token.equals("db")) {
            return decibels();
        }
        throw new IllegalArgumentException(
                "unknown slider scale \"" + token + "\"; it is `linear` or `db`");
    }

    /// The identity scale.
    record Linear() implements Scale {

        @Override
        public double toFraction(double value, double min, double max) {
            return Math.clamp((value - min) / (max - min), 0, 1);
        }

        @Override
        public double toValue(double fraction, double min, double max) {
            return min + Math.clamp(fraction, 0, 1) * (max - min);
        }

        @Override
        public String token() {
            return "linear";
        }
    }

    /// Position is **linear in decibels**, value is linear in amplitude.
    ///
    /// The mapping a mixing desk's fader has, and the reason §3 asks for it: a
    /// gain of 0.5 is 6 dB down, which is a small part of the way down a fader
    /// and *half* the way down a linear slider. Placing gain linearly gives a
    /// control whose useful range is the top inch of its travel.
    ///
    /// ```
    ///  1.0  ──  0 dB      top of the travel
    ///  0.5  ── -6 dB      90% of the way up
    ///  0.1  ── -20 dB     2/3 of the way up
    ///  0.0  ── -inf       the bottom, and only the bottom
    /// ```
    ///
    /// The bottom of the travel is `min` **exactly** rather than
    /// `max * 10^(floor/20)`, because the thing a fader must be able to do is go
    /// silent. That is a discontinuity of `10^(-60/20)` = 0.001 of full scale at
    /// one end of the control, which is the difference between −60 dB and silence
    /// and is not audible; a fader that bottomed out at "very quiet" is a fader
    /// with a bug.
    ///
    /// @param floorDb how far down the travel reaches below `max`, in dB
    record Decibels(double floorDb) implements Scale {

        public Decibels {
            if (!Double.isFinite(floorDb) || floorDb >= 0) {
                throw new IllegalArgumentException(
                        "a decibel scale's floor is below its top, so it is negative — not "
                                + floorDb);
            }
        }

        @Override
        public void validate(double min, double max) {
            // Gain is an amplitude ratio, so the arithmetic below is
            // `log10(value / max)`: a negative or zero `max` has no decibel
            // value at all, and a negative `min` is a gain the scale cannot
            // place. Refused rather than clamped, because a fader over -1..1 is
            // an application that meant something else.
            if (min < 0 || max <= 0) {
                throw new IllegalArgumentException(
                        "a decibel scale needs a gain range with min >= 0 and max > 0, not min="
                                + min + " max=" + max);
            }
        }

        @Override
        public double toFraction(double value, double min, double max) {
            if (value <= min || value <= 0) {
                return 0;
            }
            var level = 20 * Math.log10(value / max);
            return Math.clamp(1 - level / floorDb, 0, 1);
        }

        @Override
        public double toValue(double fraction, double min, double max) {
            var position = Math.clamp(fraction, 0, 1);
            if (position <= 0) {
                return min;
            }
            return Math.clamp(max * Math.pow(10, floorDb * (1 - position) / 20), min, max);
        }

        @Override
        public String token() {
            return "db";
        }
    }
}
