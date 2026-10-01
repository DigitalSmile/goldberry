"""The guide's diagrams, each a function that draws on a Board (draw.py).

Coordinates are logical pixels; a diagram is as wide as the guide's text
column. A note is `note(x, y, w, h, title, body, hue)`, a lane is `frame`, and a
connector is `arrow([(x, y), ...], label)`.
"""


def layers(b):
    """overview/architecture.md: the layers, the application at the top."""
    x, w = 24, 592
    lanes = [
        ("Application", [("Java records", "blue"), ("KDL documents", "blue"), ("CSS", "blue")], "none"),
        ("Widget layer", [("widgets", "yellow"), ("elements", "yellow"), ("render objects", "yellow")], "flow"),
        ("Style, layout and text", [("Style\nCSS engine, pure Java", "green"), ("Layout\nYoga, flexbox", "green"), ("Text\nHarfBuzz, JDK Bidi\nand BreakIterator", "green")], "none"),
        ("Paint and raster", [("Box painter\nLayers and damage", "purple"), ("Blend2D on the CPU\nBanded threads", "purple")], "none"),
        ("Backend SPI", [("Window\nPresent and input", "orange"), ("Clipboard\nCursor and tray", "orange"), ("Popup\nAnd a GPU surface", "orange")], "none"),
        ("Platforms", [("SDL3\nLinux, Windows, macOS", "pink"), ("Headless\nTests and servers", "pink")], "none"),
    ]
    y = 20
    for title, notes, kind in lanes:
        split = [text.split("\n") for text, _ in notes]
        nh = max(b.note_height("\n".join(lines[1:]) or None) for lines in split)
        top_pad = 34
        h = top_pad + nh + 14
        b.frame(x, y, w, h, title)
        gap = 12
        nw = (w - 32 - gap * (len(notes) - 1)) / len(notes)
        for i, ((text, hue), lines) in enumerate(zip(notes, split)):
            nx = x + 16 + i * (nw + gap)
            b.note(nx, y + top_pad, nw, nh, lines[0], "\n".join(lines[1:]) or None, hue)
            if kind == "flow" and i + 1 < len(notes):
                b.arrow([(nx + nw - 2, y + top_pad + 21), (nx + nw + gap + 2, y + top_pad + 21)])
        y += h + 10
    b.caption(x, y + 4, "The application writes the top lane. Everything below it is the toolkit, and only the bottom lane touches the platform.", width=w)


def frame_loop(b):
    """overview/architecture.md: one frame, in order."""
    steps = [
        ("Input events", "Pointer, wheel, keys\nand committed text", "blue"),
        ("Dispatch", "Hit-test against the\npainted frame", "blue"),
        ("Rebuild", "Dirty widgets only", "yellow"),
        ("Diff", "New widgets against\nthe element tree", "yellow"),
        ("Update", "Elements and\nrender objects", "yellow"),
        ("Style", "Invalidated nodes only", "green"),
        ("Layout", "Yoga, incremental", "green"),
        ("Paint", "Record, then rasterize\nin bands on Blend2D", "purple"),
        ("Present", "The buffer and\nits damage", "orange"),
    ]
    cols = 3
    nw, nh, gx, gy = 166, 78, 36, 40
    x0, y0 = 46, 20
    centres = []
    for i, (title, body, hue) in enumerate(steps):
        r, c = divmod(i, cols)
        if r % 2 == 1:
            c = cols - 1 - c
        x = x0 + c * (nw + gx)
        y = y0 + r * (nh + gy)
        b.note(x, y, nw, nh, title, body, hue)
        centres.append((x, y, r, c))
    for i in range(len(steps) - 1):
        x, y, r, c = centres[i]
        nx, ny, nr, nc = centres[i + 1]
        if r == nr:
            if nc > c:
                b.arrow([(x + nw + 2, y + nh / 2), (nx - 2, ny + nh / 2)])
            else:
                b.arrow([(x - 2, y + nh / 2), (nx + nw + 2, ny + nh / 2)])
        else:
            b.arrow([(x + nw / 2, y + nh + 2), (nx + nw / 2, ny - 2)])
    last = centres[-1]
    first = centres[0]
    foot = last[1] + nh + 26
    b.arrow(
        [(last[0] + nw / 2, last[1] + nh + 2), (last[0] + nw / 2, foot), (x0 - 20, foot), (x0 - 20, first[1] + nh / 2), (first[0] - 2, first[1] + nh / 2)],
        label="idle until something changes",
        dashed=True,
    )
    b.caption(x0, foot + 18, "One UI thread runs the loop. Blend2D's workers rasterize in bands beside it. Hit testing reads the painted frame, and only the damage is repainted.", width=560)


def flow(b):
    """applications.md: data flows down, events flow up."""
    nw, nh = 140, 86
    y = 40
    xs = [24, 250, 476]
    b.note(xs[0], y, nw, nh, "Values", "A @Model of plain\nfields. Knows nothing.", "yellow")
    b.note(xs[1], y, nw, nh, "Views", "Widgets, markup, CSS.\nRead values, report\nevents.", "blue")
    b.note(xs[2], y, nw, nh, "Actions", "An @Actions record.\nAssigns the values.", "green")
    b.arrow([(xs[0] + nw + 2, y + nh / 2), (xs[1] - 2, y + nh / 2)], label="read")
    b.arrow([(xs[1] + nw + 2, y + nh / 2), (xs[2] - 2, y + nh / 2)], label="report")
    foot = y + nh + 34
    b.arrow(
        [(xs[2] + nw / 2, y + nh + 2), (xs[2] + nw / 2, foot), (xs[0] + nw / 2, foot), (xs[0] + nw / 2, y + nh + 2)],
        label="assign, then notify",
    )
    b.caption(24, foot + 22, "Data flows down, events flow up (ADR-0063). A value that changed asks the window for a frame; nothing polls.", width=592)


def scroll_nodes(b):
    """layout/scroll.md: the three nodes of a viewport."""
    b.note(24, 24, 384, 176, "scroll", "The viewport: clips, takes the wheel and the keys.", "blue")
    b.note(40, 84, 352, 100, "scroll-content", "The moving box, translated by the offset.", "yellow")
    b.note(56, 140, 320, 32, "… whatever was written inside", None, "grey")
    b.caption(432, 40, "Three nodes, each one idea.", width=184)
    b.caption(432, 72, "A rule for scroll-content moves everything inside. A rule for scroll sizes the window onto it.", width=184)


ALL = {
    "layers": {"width": 640, "height": 764, "draw": layers},
    "frame-loop": {"width": 640, "height": 440, "draw": frame_loop},
    "flow": {"width": 640, "height": 230, "draw": flow},
    "scroll-nodes": {"width": 640, "height": 220, "draw": scroll_nodes},
}
