package io.github.digitalsmile.goldberry.widgets.data;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// Which series a chart is showing — the one piece of state that spans a chart's
/// two halves.
///
/// `charts.md` §3.1 calls clicking a legend entry to isolate a series "the one
/// interaction Grafana users reach for first", and it is the reason a chart is a
/// stateful widget at all: the click arrives at the **legend** and changes what
/// the **plot** draws, and those are siblings. Something above both has to
/// remember it, and this is that.
///
/// One state class for the three axis charts, for [ChartSpec]'s reason.
final class ChartState extends State<ChartSpec<?>> {

    /// The series shown alone, or -1 for all of them.
    ///
    /// An index and not a set. **Isolate, not hide**: one click shows one series
    /// and the next click puts them all back, which is the interaction people
    /// expect from a legend and is one integer to hold. A set of hidden series
    /// is the other design and it is a worse one for a *dashboard* chart —
    /// unhiding requires remembering what you hid, and a chart with three of
    /// eight series showing has a legend that no longer says what the picture is.
    private int isolated = -1;

    @Override
    public Widget build(BuildContext context) {
        var chart = widget();
        return new ChartView(
                chart.chartType(),
                ChartParts.of(
                        chart.series(), chart.categories(), chart.mode(), isolated, this::isolate, chart.options()),
                chart.attributes());
    }

    /// Shows `slot` alone, or shows everything again when it is already alone.
    ///
    /// **The second click is the way back**, and it is the same click. A legend
    /// entry that isolated on click and needed something else to restore would
    /// be a control with no visible way out of the state it just entered.
    private void isolate(int slot) {
        if (slot < 0 || slot >= widget().series().size()) {
            return;
        }
        setState(() -> isolated = isolated == slot ? -1 : slot);
    }
}
