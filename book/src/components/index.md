# The catalogue

<p class="gb-lede">Every widget in Goldberry is a Java record, a KDL node and a CSS type, and this part documents each one under the name it has in markup.</p>

By the end of this page you know what every widget shares, how a name in markup
finds the thing it refers to, and which chapter holds the widget you are looking
for. The chapters that follow show each widget three ways: a markup sample, the
Java that builds the same tree, and the stylesheet rules that reach it.

## Three ways to say one widget

A `button` is a `Button` record in Java, a `button` node in KDL and a `button`
type in CSS. A test builds the same widget both ways and asserts the two values
are equal, so the forms cannot drift
([ADR-0059](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0059-a-control-is-a-record-a-node-and-a-rule.md)).

<div class="gb-tabs">

```kdl
column {
    text "Delete this file?"
    row {
        spacer
        button press="dismiss" "Cancel"
        button class="danger" press="delete" "Delete"
    }
}
```

```java
new Column(
        new Text("Delete this file?"),
        new Row(
                new Spacer(),
                new Button("Cancel", this::dismiss),
                new Button("Delete", this::delete).styled("danger")
        )
);
```

</div>

Variants are classes. `danger` is `class="danger"` in markup,
`.styled("danger")` in Java and `button.danger` in a stylesheet, because a
class is the one spelling all three can use. `styled`, `tooltip`,
`contextMenu` and `withAttributes` are on `Attributed`, which every widget in
the catalogue implements. Nothing visual lives in a record. The
metrics are in `controls.css` and the colours are the theme's tokens, so a theme
restyles a control whose rule never names one.

## What every widget carries

Every widget holds an `Attributes` value. The inflater reads five properties off
any node, and the same five have Java withers on `Attributes.NONE`.

| Attribute | Type | Default | What it does |
|---|---|---|---|
| `id` | string | none | The node's id for `#id` rules. It doubles as the reconciler's key, so a node with an id keeps its state and focus across a rebuild that reorders it |
| `class` | string | none | Space-separated classes, for `.name` rules and variants |
| `tooltip` | string | none | Text shown on hover, on any widget ([ADR-0105](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0105-a-tooltip-is-an-attribute-not-a-widget.md)) |
| `context-menu` | string | none | The name of the menu a right-click opens, resolved by the application ([ADR-0108](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0108-a-context-menu-is-a-name-on-a-widget.md)) |
| `name` | string | none | The accessible name, which wins over any name the widget derives ([ADR-0260](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0260-a-name-is-an-attribute-every-widget-has.md)) |

```java
var attributes = Attributes.NONE.id("save").classes("primary").tooltip("Ctrl+S");
new Button("Save", this::save).withAttributes(attributes);
```

In Java a key may differ from the id. `Attributes.NONE.key(row)` keys a list
item by its model row. The two hover hooks, `onPointerEnter` and
`onPointerExit`, are Java only, because a `Runnable` is not a KDL value
([ADR-0327](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0327-a-hover-is-a-node-property-not-a-menus.md)).

Controls that can be disabled read `disabled=#true`. A disabled container
disables every descendant for input: nothing inside it is clicked, focused or
hovered
([ADR-0077](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0077-disabled-propagates-for-input-and-not-for-paint.md)).
The cascade sees it too, so a rule can style a disabled subtree, while the fade
stays on the node that declared it
([ADR-0379](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0379-a-disabled-container-reaches-the-cascade.md)).

## What a name resolves against

Markup names things and cannot build them. Each kind of name has a registry,
and `Widgets.inflater(named, icons, models)` assembles all of them from a
model's annotations.

| Attribute | Registry | What the name is |
|---|---|---|
| `press=`, `change=`, `link=`, `task=` | `ActionRegistry` | A method. `press` names an action with no argument, the others one that is handed a value |
| `bind=` | `BindingRegistry` | A value to follow, read-only. The widget subscribes and rebuilds when it changes ([ADR-0062](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0062-bind-is-a-path-and-nothing-else.md)) |
| `icon=` | `Icons` | An icon the application built and will close ([ADR-0043](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0043-icons-are-stroked-paths.md)) |
| `controller=`, `validator=`, `player=`, `renderer=`, `images=` | `Named` | An object that neither changes nor needs closing ([ADR-0170](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0170-a-document-names-an-object-and-a-label-hands-focus-down.md)) |

A registry is strict by default, so `press="delte"` fails at inflation rather
than producing a button that does nothing. `Widgets.inflater()` with no
arguments binds nothing and complains about nothing, which is what a preview
wants. A value is named once, in its `@Bind` annotation, and a widget built in
Java follows it by the same path
([ADR-0129](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0129-a-value-is-named-one-way.md)). Data flows down through
`bind=` and events flow up through actions. A widget is handed an `Observable`
with no `set` on it, so a control built from markup cannot write the model
([ADR-0063](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0063-data-flows-down-events-flow-up.md)).

## Parts are not widgets

A checkbox's glyph, a chart's plot and a legend's swatch are parts. Each is a
CSS type a stylesheet can reach, `check-indicator` or `chart-legend-swatch`, and
none is a node a document can create
([ADR-0065](https://github.com/DigitalSmile/goldberry/blob/master/book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)). A part
outside its widget means nothing, so the inflater does not know its name. Each
chapter's *Styling* section lists the parts a widget has.

## The chapters

<div class="gb-cards">
<a class="gb-card" href="text.html"><strong>Text and links</strong><span>text, link</span></a>
<a class="gb-card" href="buttons.html"><strong>Buttons, badges and chips</strong><span>button, badge, chip</span></a>
<a class="gb-card" href="choices.html"><strong>Choices</strong><span>checkbox, toggle, radio, radio-group, segmented, select, option</span></a>
<a class="gb-card" href="values.html"><strong>Values and progress</strong><span>slider, knob, progress, spinner</span></a>
<a class="gb-card" href="forms.html"><strong>Fields and forms</strong><span>text-input, text-area, field, form, code-input, date-picker, time-picker, color-picker</span></a>
<a class="gb-card" href="panels.html"><strong>Panels</strong><span>panel, card, group-box, collapse, carousel, skeleton, statistic, tabs, timeline</span></a>
<a class="gb-card" href="collections.html"><strong>Collections</strong><span>list, table, tree</span></a>
<a class="gb-card" href="navigation.html"><strong>Navigation</strong><span>breadcrumbs, steps, wizard</span></a>
<a class="gb-card" href="menus.html"><strong>Menus and the tray</strong><span>menubar, menu, item, separator, and the tray icon</span></a>
<a class="gb-card" href="overlays.html"><strong>Overlays</strong><span>dialog, popover, message, hud, toast and tour</span></a>
<a class="gb-card" href="charts.html"><strong>Charts</strong><span>line-chart, bar-chart, area-chart, donut-chart, sparkline</span></a>
<a class="gb-card" href="drawing.html"><strong>Canvas, images and QR codes</strong><span>canvas, image, qr-code</span></a>
<a class="gb-card" href="content.html"><strong>Markdown, HTML and the web</strong><span>markdown-view, html-view, and the web view</span></a>
<a class="gb-card" href="media.html"><strong>Audio and video</strong><span>media-player, video-view, audio-player, media-controls</span></a>
<a class="gb-card" href="gpu.html"><strong>The GPU canvas</strong><span>canvas3d</span></a>
<a class="gb-card" href="../layout/index.html"><strong>Layout</strong><span>row, column, spacer, stack, scroll, split-pane, masonry, affix</span></a>
</div>

## Every markup name

The 79 names the inflater knows, and where each is documented. The layout
widgets have a part of their own.

| Name | Chapter |
|---|---|
| [`action`](overlays.md#action) | Overlays |
| [`affix`](../layout/affix.md#affix) | Layout |
| [`area-chart`](charts.md#area-chart) | Charts |
| [`audio-player`](media.md#audio-player) | Audio and video |
| [`badge`](buttons.md#badge) | Buttons, badges and chips |
| [`bar-chart`](charts.md#bar-chart) | Charts |
| [`breadcrumbs`](navigation.md#breadcrumbs) | Navigation |
| [`button`](buttons.md#button) | Buttons, badges and chips |
| [`canvas`](drawing.md#canvas) | Canvas, images and QR codes |
| [`canvas3d`](gpu.md#canvas3d) | The GPU canvas |
| [`card`](panels.md#card) | Panels |
| [`carousel`](panels.md#carousel) | Panels |
| [`checkbox`](choices.md#checkbox) | Choices |
| [`chip`](buttons.md#chip) | Buttons, badges and chips |
| [`code-input`](forms.md#code-input) | Fields and forms |
| [`collapse`](panels.md#collapse) | Panels |
| [`color-picker`](forms.md#color-picker) | Fields and forms |
| [`column`](../layout/row-and-column.md#column) | Layout |
| [`crumb`](navigation.md#crumb) | Navigation |
| [`date-picker`](forms.md#date-picker) | Fields and forms |
| [`dialog`](overlays.md#dialog) | Overlays |
| [`donut-chart`](charts.md#donut-chart) | Charts |
| [`entry`](panels.md#entry) | Panels |
| [`field`](forms.md#field) | Fields and forms |
| [`form`](forms.md#form) | Fields and forms |
| [`group-box`](panels.md#group-box) | Panels |
| [`html-view`](content.md#html-view) | Markdown, HTML and the web |
| [`hud`](overlays.md#hud) | Overlays |
| [`image`](drawing.md#image) | Canvas, images and QR codes |
| [`item`](menus.md#item) | Menus and the tray |
| [`knob`](values.md#knob) | Values and progress |
| [`line-chart`](charts.md#line-chart) | Charts |
| [`link`](text.md#link) | Text and links |
| [`list`](collections.md#list) | Collections |
| [`markdown-view`](content.md#markdown-view) | Markdown, HTML and the web |
| [`marker`](panels.md#marker) | Panels |
| [`masonry`](../layout/masonry.md#masonry) | Layout |
| [`media-controls`](media.md#media-controls) | Audio and video |
| [`media-player`](media.md#media-player) | Audio and video |
| [`menu`](menus.md#menu) | Menus and the tray |
| [`menubar`](menus.md#menubar) | Menus and the tray |
| [`message`](overlays.md#message) | Overlays |
| [`option`](choices.md#option) | Choices |
| [`page`](navigation.md#page) | Navigation |
| [`panel`](panels.md#panel) | Panels |
| [`point`](charts.md#point) | Charts |
| [`popover`](overlays.md#popover) | Overlays |
| [`progress`](values.md#progress) | Values and progress |
| [`qr-code`](drawing.md#qr-code) | Canvas, images and QR codes |
| [`radio`](choices.md#radio) | Choices |
| [`radio-group`](choices.md#radio-group) | Choices |
| [`row`](../layout/row-and-column.md#row) | Layout |
| [`scroll`](../layout/scroll.md#scroll) | Layout |
| [`segmented`](choices.md#segmented) | Choices |
| [`select`](choices.md#select) | Choices |
| [`separator`](menus.md#separator) | Menus and the tray |
| [`series`](charts.md#series) | Charts |
| [`skeleton`](panels.md#skeleton) | Panels |
| [`slider`](values.md#slider) | Values and progress |
| [`spacer`](../layout/spacer.md#spacer) | Layout |
| [`sparkline`](charts.md#sparkline) | Charts |
| [`spinner`](values.md#spinner) | Values and progress |
| [`split-pane`](../layout/split-pane.md#split-pane) | Layout |
| [`stack`](../layout/stack.md#stack) | Layout |
| [`statistic`](panels.md#statistic) | Panels |
| [`step`](navigation.md#step) | Navigation |
| [`steps`](navigation.md#steps) | Navigation |
| [`tab`](panels.md#tab) | Panels |
| [`table`](collections.md#table) | Collections |
| [`tabs`](panels.md#tabs) | Panels |
| [`text`](text.md#text) | Text and links |
| [`text-area`](forms.md#text-area) | Fields and forms |
| [`text-input`](forms.md#text-input) | Fields and forms |
| [`time-picker`](forms.md#time-picker) | Fields and forms |
| [`timeline`](panels.md#timeline) | Panels |
| [`toggle`](choices.md#toggle) | Choices |
| [`tree`](collections.md#tree) | Collections |
| [`video-view`](media.md#video-view) | Audio and video |
| [`wizard`](navigation.md#wizard) | Navigation |

## Java only

Four widgets have no markup name, because each holds something a document
cannot describe: a queue, a sequence of steps, a platform handle or a page.

- **Toast** and **Tour** are in [Overlays](overlays.md).
- **TrayIcon** is in [Menus and the tray](menus.md).
- **WebView** is in [Markdown, HTML and the web](content.md#the-web-view).
