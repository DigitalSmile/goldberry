# Goldberry — Charts

Companion to `core-widgets.md` §11 and `content-widgets.md` §3. Covers the
first-party chart widgets in `goldberry-widgets`: what they are, what the series
palette is and how it was derived, and — because it is the question this document
was written to answer — **which of Grafana's chart features belong in a desktop
toolkit and which do not.**

**The engine decision is already made and is not revisited here.** There is no
third-party chart engine: Java's render through AWT, ImPlot is welded to Dear
ImGui's immediate mode, and the great ones are JavaScript. Charts are built on the
`canvas` primitive so they inherit the theme, the text stack, hit-testing and the
golden-image corpus (`content-widgets.md` §3).

---

## 1. What is built on

**`canvas` is built, and everything here sits on it.** It is a §1 primitive in
`goldberry-core` — a painter slot on `Box`, handed a frame translated to the
box's content corner and clipped to it (ADR-0193), composing onto whatever
transform its ancestors set (ADR-0197). The order was `canvas`, then the chart
substrate (`Ticks`, `Scale`, `Lttb`, `PlotGeometry`), then the five widgets, and
that is the order it happened in; `statistic`'s sparkline was the first consumer
and a chart is the second.

A chart's **interaction** sits on it too, and not entirely: the crosshair and the
hover readout are painted, and the legend is real widgets that a pointer clicks
(ADR-0198). Which half a piece of a chart belongs in is decided by whether it
participates in layout, not by whether it is text.

---

## 2. The series palette, derived and validated

`content-widgets.md` §3 says "categorical series colors from aurora + frost hues"
and "ramps interpolated in OKLCH". The word doing the work is **derived**: Nord's
own hues are a *UI* palette and fail as series colours. Measured rather than
asserted — the same discipline ADR-0175 applied to the semantic hues, which found
the theme's own claim untrue in five of eight pairs:

| Check | Nord's nine hues, used literally |
|---|---|
| Lightness band | **FAIL** — `nord13` at L 0.855, `nord8` at 0.775 |
| Chroma floor | **FAIL** — six of eight below C 0.10; they read as gray and stop doing identity work |
| CVD separation | WARN — `nord14`↔`nord13` ΔE 6.5 under protanopia |
| Normal-vision floor | **FAIL** — `nord9`↔`nord8` ΔE 8.5; two frost blues 5° apart in hue |
| Contrast vs surface | WARN — six of eight below 3:1 on white |

Five of six checks fail. The reason is visible in one line of measurement: Nord's
frost family spans **23° of hue** across its four members, and `nord9`/`nord10`
are 5° apart — the same hue at two lightnesses, which is an elevation ramp rather
than two identities.

### 2.1 What ships instead

Eight slots, each **re-stepped from a Nord hue angle** to a fixed lightness and a
chroma that clears the floor, so a series still reads as Nord and can also do the
job. Light and dark are separate steps from the same angles, not a flip.

| Slot | Hue | Light (on `--gb-surface` `#ffffff`) | Dark (on `#3b4252`) |
|---|---|---|---|
| 1 | `nord14` green | `#679732` | `#73a340` |
| 2 | `nord15` purple | `#b663aa` | `#c46fb7` |
| 3 | `nord13` yellow | `#aa7e05` | `#b88a07` |
| 4 | `nord10` blue | `#4488d8` | `#5094e5` |
| 5 | `nord11` red | `#cc5e6a` | `#da6a76` |
| 6 | `nord8` cyan | `#0796b2` | `#02a3c1` |
| 7 | `nord12` orange | `#cb6443` | `#d9704f` |
| 8 | `nord7` teal | `#0d9999` | `#06a7a7` |

Light steps at OKLCH L 0.62, dark at L 0.66, both at C 0.14 clipped into gamut.
**All six checks pass in both modes**: worst adjacent CVD ΔE 12.4 (protanopia),
worst normal-vision ΔE 21.9, every slot ≥ 3:1 on its surface.

**The order is the safety mechanism, and it was searched rather than chosen.** All
40 320 orderings were scored against the validator in both modes; the sequence
above is the one maximising the worst adjacent pair. Ordering by hue wheel or by
Nord's own numbering both collapse — `nord12` orange beside `nord14` green is
ΔE 0.8 under deuteranopia, which is two series a reader cannot tell apart.

Consequences that are rules, not preferences:

- **Slots are assigned in order and never cycled.** A ninth series is not a
  generated hue — it folds into "Other", or the chart becomes small multiples.
- **Colour follows the entity, not its rank.** Hiding a series must not repaint
  the survivors.
- **`nord9` has no slot.** Four frost hues yield two usable identities.
- **The aurora hues keep their semantic meaning elsewhere** (`design-system.md`
  §1.2). A threshold band drawn in `--gb-danger` and a series drawn in slot 5 are
  different reds on purpose: one means "bad", the other means "series 5".

---

## 3. Grafana parity — the feature list, filtered for a desktop toolkit

Grafana is the reference because it is what people mean by "charts that do
everything". Most of that list is **panel/dashboard machinery**, not chart
machinery, and a UI toolkit is the wrong home for it. Each row says where the
feature lands and why.

### 3.1 In `goldberry-widgets` v1 — the five widgets and what they owe

| Grafana feature | Verdict | Note |
|---|---|---|
| Time series line, multi-series | **v1** | `line-chart`; `java.time` axes are already specified (§3.1) |
| Area, single and stacked | **v1** | `area-chart`; stacking normal and 100% |
| Bar chart, grouped and stacked | **v1** | `bar-chart`; horizontal orientation for long category names |
| Sparkline (no axes, no legend) | **v1** | `sparkline`, and `statistic`'s missing child |
| Donut / pie | **v1, narrowed** | Part-to-whole only, ≥ 3 slices, share labels shown. A two-slice donut is a meter and a many-slice donut is a stacked bar — both are refused rather than drawn badly |
| Legend: placement, list mode | **v1** | Present for ≥ 2 series, absent for one — the title names a lone series |
| Tooltip: single series, all series | **built, less donut** | Crosshair + readout on line/area, band highlight on bar (ADR-0198). Hover on `donut-chart` is not built |
| Shared crosshair across charts | **v1** | Linked by a shared `CrosshairGroup`; cheap because it is one value two widgets read. Not built — the per-chart crosshair it hangs off now exists |
| Null handling: gap / connect / zero | **v1** | Three-way, explicit. A gap drawn as zero is a lie about the data and the default is the gap |
| Interpolation: linear, smooth, step | **v1** | Step matters for state-ish series; smooth is monotone-cubic, which cannot overshoot into impossible values |
| Fill opacity, gradient fill | **v1** | Gradient is a linear OKLCH fade of the series colour to transparent |
| Point markers, size, show-always/never/auto | **v1** | ≥ 8px hit target when hoverable |
| Axis min/max, soft min/max | **v1** | Soft bounds are what stop a flat series rendering as noise |
| Log axis | **v1** | With correct log tick labelling |
| Thresholds: lines and shaded regions | **v1** | Drawn in the *semantic* hues, never a series slot |
| Value formatting per axis | **v1, app-supplied** | See §3.4 |
| Series toggle by clicking the legend | **built** | Click isolates, click again restores; the others are dimmed rather than dropped (ADR-0198) |
| Empty / loading / error states | **v1** | A chart with no data draws a themed message, never an empty grid |

### 3.2 In `goldberry-plot` (post-v1) — science-grade, not dashboard-grade

| Grafana feature | Verdict | Note |
|---|---|---|
| Histogram | **plot** | Freedman–Diaconis binning (§4.1) |
| Heatmap (time × bucket) | **plot** | viridis-class colormaps; never a rainbow |
| Scatter / XY chart | **plot** | With the spatial index for hover on 10⁵ points |
| Candlestick / OHLC | **plot** | A financial mark, not a dashboard one |
| Trend (numeric x, not time) | **plot** | Falls out of having real scales |
| Box plot, error bars, contour | **plot** | §4.1 already names them |
| Pan and zoom on the plot area | **plot** | Table stakes there; §4.2 says so |
| Small multiples / repeat | **plot** | The honest answer to "too many series" |

### 3.3 Not a chart, and not this module

| Grafana feature | Verdict | Why |
|---|---|---|
| Gauge (radial), bar gauge / LCD | **core widgets** | `progress`, `knob` and a `meter` are controls; a gauge is a control that happens to be round |
| Stat panel | **built** | `statistic` (§5), plus the sparkline it is waiting on |
| Table panel | **catalog** | §10's `table`, deferred with virtualization |
| State timeline, status history | **v1.x candidate** | A real desktop want (a build pipeline, a device's states); it is a bar chart with a time axis and a categorical fill, so it costs little once the substrate exists |
| Flame graph | **candidate** | The one "observability" form that is genuinely a desktop want — profilers are desktop applications. Not v1 |
| Geomap | **non-goal** | `content-widgets.md` §4.2 already says maps are out |
| Node graph, traces, service map | **non-goal** | Graph layout is a library of its own |
| Logs panel | **non-goal** | That is `list` + `code-view`, which other modules own |
| Text / news / dashboard-list panels | **non-goal** | Dashboard furniture, not visualization |
| Alert list, annotations list | **non-goal** | Application domain |

### 3.4 Dashboard machinery a *toolkit* must not grow

These are the ones worth naming explicitly, because "Grafana parity" invites them
and each would be a mistake here.

- **Query editors, data sources, transformations.** A chart takes a `Series`. Where
  it came from — SQL, Prometheus, a file — is the application's. Growing a query
  layer inside a UI toolkit is how a UI toolkit stops being one.
- **Field overrides and value mappings.** Grafana needs a config language because
  its charts are configured by non-programmers through a form. Goldberry's are
  configured in Java by the person writing the program: `series.color(…)` is the
  override mechanism, and `0 → "off"` is a formatter.
- **Auto-refresh and time-range pickers.** The frame loop is idle when nothing is
  animating (`design-system.md` §1.7) and a chart that polled would falsify that
  for every window with one on screen — the same rule that keeps `hud` from
  requesting frames (ADR-0101). An application refreshes its own model; the chart
  redraws because the model changed.
- **Panel inspect / CSV export.** `Plot.renderTo(image)` gives PNG export for free
  (§4.3). Data export is the application's, which already has the data.
- **Dual y-axes.** Grafana offers them; this toolkit will not. Two measures at
  different scales are two charts, small multiples, or one indexed to a common
  base. **This contradicts `content-widgets.md` §4.1**, which lists "dual y-axes"
  among `goldberry-plot`'s scales — recorded here and in `ARCHITECTURE.md` §17.1
  rather than settled, because the design documents are the authority and this
  needs a decision rather than an edit.

### 3.5 What Grafana has that a desktop toolkit should want *more* than Grafana does

Three things that matter more here than they do in a browser, and are easy to miss
in a parity list:

- **Keyboard operation of the chart itself.** Arrow keys walk the crosshair
  point-by-point, `Home`/`End` jump to the ends, `Tab` moves between series. A
  browser dashboard is a pointer surface; a desktop application is not, and §2.2
  requires everything to be reachable. Grafana is weak here and it is not a model
  to copy.
- **Selection and copy.** `Ctrl+C` on a focused chart copying the hovered value —
  or the series — as text is the desktop convention and costs a clipboard call
  the toolkit already has.
- **Determinism.** Every chart in the corpus is a golden image on three OSes with
  embedded fonts and a CPU rasterizer. That is a property no browser charting
  library can offer, and it is what makes the tick algorithm and the palette
  testable rather than eyeballed.

---

## 4. Scope guardrail

`content-widgets.md` §3 is unchanged and is the rule: **dashboard-grade, five
widgets.** The list in §3.1 is what those five owe, not a licence to grow a sixth.
Anything in §3.2 is `goldberry-plot`'s and waits for it. When a request does not
fit either, the honest answers in order are: a `canvas` (the escape hatch exists
for exactly this), a table, or a different module.
