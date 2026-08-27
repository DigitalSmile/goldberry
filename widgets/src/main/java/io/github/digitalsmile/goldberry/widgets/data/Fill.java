package io.github.digitalsmile.goldberry.widgets.data;

/// What is under a band or a line — `charts.md` §3.1's "fill opacity, gradient
/// fill", which was the last row of that table left unbuilt.
///
/// The opacity half shipped with the area chart: a band is drawn at 85%, which is
/// nearly opaque because a *stack's* bands do not overlap and translucency there
/// only mixes each band with the gridlines behind it. What was deferred is the
/// ramp, and it was deferred for a reason that had nothing to do with charts —
/// Blend2D has gradients and the export list did not, so the first commit of this
/// was a widening of the native surface
/// ([ADR-0207](../../../../../../../book/src/adr/0207-a-fill-may-be-a-ramp.md)).
///
/// **This is three values and not a number**, unlike [Bounds] beside it. An
/// opacity is a number a caller could reasonably want any value of; a fill is a
/// choice between conventions, and the two that matter — a flat wash and a fade
/// toward the axis — are the two a dashboard is drawn with. A caller who wants a
/// third writes a `canvas`, which is the escape hatch `charts.md` §4 names for
/// exactly this.
public enum Fill {

    /// A flat wash at the band's own opacity — what an area chart drew before
    /// there was a choice, and what it still draws.
    ///
    /// The honest rendering of a stacked area chart: the bands are adjacent
    /// rather than overlapping, they add up to the total, and a reader compares
    /// their thicknesses. A fade makes the bottom of a thick band lighter than
    /// the top of a thin one, which is a difference that means nothing.
    SOLID,

    /// A fade from the series colour to nothing, running down the fill.
    ///
    /// What a dashboard's time series usually looks like, and what it is *for*:
    /// the line is the reading and the fill is a hint at magnitude, so a fill
    /// that thins out says "the line is the data" in a way a flat wash does not.
    /// Best on a [io.github.digitalsmile.goldberry.widgets.data.linechart.LineChart],
    /// where there is one region per series and nothing underneath it to
    /// obscure.
    ///
    /// **The fade keeps the hue.** Fading to transparent black would take a green
    /// band through grey on its way out, which is the classic wrong gradient;
    /// the far stop is the same colour at zero alpha, so what thins out is the
    /// colour rather than the light. The `OKLCH` in the deferred entry's wording
    /// turns out not to be a choice at all — a ramp between two alphas of one
    /// hue is the same curve in every perceptual space, and what makes it
    /// correct is premultiplied interpolation rather than the space it is stated
    /// in.
    GRADIENT,

    /// No fill at all: the line, and nothing under it — the **default**.
    ///
    /// Which is what a line chart already is, and that is why it is the default
    /// rather than an option: every chart in the toolkit keeps the picture it
    /// had, and [#GRADIENT] is a thing a caller asks for rather than something
    /// that happened to their dashboard.
    ///
    /// Only a line chart can honour it. A band with no fill is not a band, so an
    /// [io.github.digitalsmile.goldberry.widgets.data.areachart.AreaChart] reads
    /// this as [#SOLID] — which is the same rule that keeps its axis at zero and
    /// refuses it a log scale: an area chart encodes by area, and the ways of
    /// not drawing one are not choices it has.
    NONE
}
