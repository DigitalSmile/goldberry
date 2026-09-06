package io.github.digitalsmile.goldberry.widgets.form.colorpicker;

/// Where the plane's cursor was put — two numbers, both `0..1`.
///
/// A named interface rather than a `BiConsumer<Double, Double>`, which would box
/// two doubles per pointer move on the busiest event a picker has, or a
/// `DoubleBinaryOperator`, which is the right shape and the wrong meaning: it
/// returns a value and this reports one.
@FunctionalInterface
interface PlaneCursor {

    /// The cursor is now at `saturation` across and `value` up.
    void at(double saturation, double value);
}
