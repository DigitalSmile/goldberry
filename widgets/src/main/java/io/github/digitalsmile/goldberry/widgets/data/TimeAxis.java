package io.github.digitalsmile.goldberry.widgets.data;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/// When each point happened — `content-widgets.md` §3.1's `java.time`-driven time
/// axis.
///
/// ```java
/// chart.times(readings.stream().map(Reading::at).toList());
/// ```
///
/// ## What it changes
///
/// Without one, a chart's x is the point **index**: points are evenly spaced and
/// [io.github.digitalsmile.goldberry.widgets.data.linechart.LineChart#categories]
/// labels them. With one, x is **time**, so a gap in the sampling is a gap on the
/// axis — a series scraped every 15 seconds that missed four minutes shows those
/// four minutes as four minutes wide, rather than as one step like every other.
/// That is the whole reason a time axis is not just a labelling: evenly spacing
/// unevenly sampled data is a picture of a schedule nobody kept.
///
/// The labels come from [TimeTicks], which steps across second, minute, hour,
/// day, month and year boundaries rather than by a round number of milliseconds.
///
/// ## The zone is the application's, and it is not the locale
///
/// A zone decides **which day it is**, so it is read from
/// [ZoneId#systemDefault()] unless an application says otherwise: a desktop
/// application showing "Tuesday" means the user's Tuesday.
///
/// That lands the other way from the rule about *formatting*, which is the root
/// locale everywhere in this toolkit precisely so a golden image is not a test
/// that passes in one country. The two are different questions: a locale changes
/// how a number is written and a zone changes which number it is, so an axis
/// formatted in the machine's language would be an unfamiliar picture and an axis
/// in the machine's zone is the correct one. A test — or an application charting
/// a server's clock — passes [#in] explicitly.
///
/// ## One axis per chart
///
/// Like `categories`, and for the same reason: a chart has one x, and a second
/// series timed differently would be two x axes in one picture, which is the
/// mistake §3.4 refuses in the other direction. The times line up with the
/// **point indices** — the *n*th instant belongs to the *n*th point of every
/// series.
///
/// @param times one instant per point index, in order
/// @param zone  which day is which
public record TimeAxis(List<Instant> times, ZoneId zone) {

    public TimeAxis {
        times = List.copyOf(times == null ? List.of() : times);
        zone = zone == null ? ZoneId.systemDefault() : zone;
        for (var time : times) {
            Objects.requireNonNull(time, "an instant on a time axis");
        }
    }

    /// The times, in the machine's own zone.
    public static TimeAxis of(List<Instant> times) {
        return new TimeAxis(times, ZoneId.systemDefault());
    }

    /// The same, spelled out.
    public static TimeAxis of(Instant... times) {
        return of(List.of(times));
    }

    /// This axis read in `zone` — a server's clock, or `UTC` for a test.
    public TimeAxis in(ZoneId value) {
        return new TimeAxis(times, value);
    }

    /// Whether this axis has an instant for every one of `points` points.
    ///
    /// A chart whose times do not cover its data falls back to the point index
    /// rather than drawing half a time axis: an axis that ran out would put the
    /// remaining points at a time they were not measured, which is worse than
    /// admitting the axis is an index.
    public boolean covers(int points) {
        return times.size() >= points && points > 0;
    }

    /// The instant of point `index`.
    public Instant at(int index) {
        return times.get(index);
    }

    /// Point `index` as a number a [Scale] can work in — epoch milliseconds.
    ///
    /// A `double` holds every millisecond of a ±285 000 year range exactly, so
    /// nothing here loses a tick; the arithmetic that decides *labels* stays in
    /// `java.time`, where a month has the number of days it actually has.
    public double millisAt(int index) {
        return times.get(index).toEpochMilli();
    }

    /// The first instant, or null when there are none.
    public Instant first() {
        return times.isEmpty() ? null : times.getFirst();
    }

    /// The last instant, or null when there are none.
    public Instant last() {
        return times.isEmpty() ? null : times.getLast();
    }
}
