#!/usr/bin/env python3
"""Puts the guide's pictures and tabbed samples into its chapters.

Idempotent, and run by hand after `BookPicturesTest` has taken the pictures.
Two edits, under book/src:

1. Under every widget heading in Layout and Components -- `## `button`` -- a
   `gb-shot` block showing the widget's two pictures, `images/<name>-light.webp`
   and `-dark.webp`, at the logical width the picture was taken at. The alt
   text is written here, per widget, because a picture a screen reader cannot
   describe is not documentation.

2. Every run of adjacent fenced samples that starts with `kdl` and goes on in
   another language -- the markup, then the Java that builds the same tree,
   sometimes the CSS that reaches it -- is wrapped in `<div class="gb-tabs">`,
   which goldberry.js turns into tabs.

A heading already shown, a run already wrapped, is left alone, so running it
twice changes nothing. A heading without an entry in ALT below is reported and
left alone, which is what happens for a new widget until its line is written.
"""

import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "book" / "src"
CATALOGUE = ["layout", "components"]
TABBED_ELSEWHERE = ["overview/concept.md"]
WIDGET_HEADING = re.compile(r"^(#{2,3}) `([a-z0-9-]+)`\s*$")
ANY_HEADING = re.compile(r"^#{1,6} ")
FENCE = re.compile(r"^```(\S*)\s*$")
TABBABLE = {"kdl", "java", "css"}

# What each widget's picture shows, in the light shade; the dark one is the
# same scene. Keyed by the markup name. The caption follows the alt text.
ALT = {
    "button": ("Five buttons in a row: Save, New with a plus icon in the accent, Delete in red, Later greyed out, and What is this? as a link", "The five variants. Each is a class, not a constructor argument."),
    "badge": ("Three badges: a count of 3, offline in red, and passing in green", "A count, a danger badge and a success badge bound to a value."),
    "chip": ("Four chips: Unread filled because it is selected, Live outlined with a green dot, java with a tag icon and a dismiss cross, and Sealed greyed out", "A selected chip, an outlined one with a dot, one with an icon and a dismiss, and a disabled one."),
    "line-chart": ("A line chart of Downloads and Installs over three points, with a y axis from 1000 to 3500 and a legend under it", "Two series, an axis the chart chose, and a legend."),
    "series": ("A line chart with one series, Uptime, over Mon and Tue", "One series, two points."),
    "point": ("A bar chart with two bars for Riders, one labelled Mon and one with no label", "A point with a label and one without."),
    "bar-chart": ("Grouped bars for Crebain and Riders over Mon, Tue and Wed, Crebain in green and Riders in pink", "Two series side by side at each label."),
    "area-chart": ("A stacked area chart of Lembas over Dried meat across three days", "Two series stacked, the second on top of the first."),
    "donut-chart": ("A donut in three segments, Lembas in green, Dried meat in pink and Nothing in gold, with a legend", "One point per series, each a segment."),
    "sparkline": ("A rising line filled underneath, with a marker on its last point", "Filled, with a marker at the end."),
    "checkbox": ("Three checkboxes: Frosted sidebar ticked, Sworn to the Fellowship ticked and greyed out, and Some of them with a dash", "Checked, checked and disabled, and indeterminate."),
    "toggle": ("Two switches: Frosted sidebar on in the accent, and Bound by oath on and greyed out", "Bound, and on but disabled."),
    "radio": ("Three radios: Moria filled, Lothlórien empty, and Follow the system greyed out", "A group with one selected and one disabled."),
    "radio-group": ("Two radios, Moria selected and Lothlórien not, with a caption under them", "A group bound to a value, with a caption as a third child."),
    "segmented": ("A segmented bar with List, Grid filled in the accent, and Map with a map icon", "Three options, the bound one filled."),
    "select": ("Three selects stacked: Moria chosen in the first, Sindarin and Westron as chips in the second, and a third reading Anywhere in the West", "Single, multiple as chips, and an autocomplete with a placeholder."),
    "option": ("A select showing Grid with a grid icon", "The chosen option, drawn with its icon."),
    "list": ("A list of six stops from Hobbiton to Lothlórien, Rivendell highlighted", "Six rows, one selected."),
    "table": ("A table with Name, Realm and Leagues columns and four rows of the Company", "Three columns, the last a fixed width."),
    "tree": ("A tree with two closed branches, Eriador and Erebor, each with a checkbox", "Two roots with cascading checkboxes."),
    "markdown-view": ("A split pane: Markdown source in a monospace text area on the left and the rendered document on the right, with a heading, a paragraph and a task list", "The source and the document it renders, side by side."),
    "html-view": ("A rendered HTML document: a heading, The Red Book, and a paragraph with an italic word and a link", "A heading, emphasis and a link."),
    "image": ("Three images in a column, each drawn as its alt text because nothing supplies the pixels", "With no image source bound, each image is its alt text."),
    "qr-code": ("A QR code for goldberry.dev with a quiet zone around it", "A code at error-correction level M."),
    "text-input": ("Five fields: Peregrin Took, 8080, a password field with a placeholder, a read-only field, and a disabled one", "Bound, filtered, password, read-only and disabled."),
    "text-area": ("Three text areas: a short bio, a monospace one with a line gutter holding Markdown, and a tall one holding HTML", "Three rows, a gutter, and one that fills."),
    "field": ("A horizontal form: Name and Port labels beside their fields, and an Enlist button aligned with the fields", "Two labelled fields and an actions row."),
    "form": ("A form with a Name field and a Port field, each showing its placeholder", "Two fields under a controller."),
    "code-input": ("Three code inputs: six boxes with 1, 2 and 3 typed, six masked boxes, and five boxes for letters and digits", "Digits, masked, and alphanumeric."),
    "date-picker": ("A date picker field reading 9/14/26 with a chevron", "Closed, showing its bound date."),
    "time-picker": ("A time picker field reading 9:30 with a chevron", "Closed, showing its bound time."),
    "color-picker": ("Two colour swatches, one frost blue and one translucent red, each with a chevron", "A bound colour and one with alpha."),
    "menubar": ("A menu bar with File, View and Help", "The bar at rest. A menu opens on press."),
    "menu": ("A menu card with Rename, Duplicate, a separator, and Delete", "Three items and a separator."),
    "item": ("One menu item: a palette icon, Switch the light, a tick, and the accelerator Ctrl+T", "An icon, a label, a check and an accelerator."),
    "separator": ("A thin horizontal rule", "A rule between items."),
    "breadcrumbs": ("A trail: a house icon with Home, then Library, Reference, and The Red Book in plain text", "Three links and the current page."),
    "crumb": ("One crumb: a house icon and the word Home", "An icon and a label."),
    "steps": ("Three steps in a row: Account ticked with a description, Payment current in the accent, and Review upcoming", "Done, current and upcoming."),
    "step": ("One step marked 1, Account, with Who you are under it", "A reachable step with a description."),
    "wizard": ("A wizard on its Payment page: the indicator shows Account done and Review upcoming, and the page reads Nothing to pay", "The second of three pages, chosen by the bound step."),
    "page": ("A wizard page titled Payment reading Nothing to pay", "One page on its own."),
    "dialog": ("A dialog titled Unsaved changes over a dimmed page, with Don't save, Keep editing and a highlighted Discard", "Three roles: neutral, dismissive and affirmative."),
    "popover": ("A small card reading Saved a moment ago with an Undo button", "A card with text and a ghost button."),
    "message": ("Three banners: a warning about a session ending with a Stay signed in button, a red one that could not save with a dismiss cross, and a green Saved", "Warning, danger with a dismiss, and success."),
    "hud": ("A small dark readout showing dashes for frames per second and paint time", "At rest, before a frame has been measured."),
    "panel": ("A panel reading The map and Where the road goes next", "A surface with two lines of text."),
    "card": ("A card titled Surfaces with a line of text under it", "An interactive card at rest."),
    "group-box": ("A box titled The Company with two rows: Ring-bearer, Frodo Baggins and Guide, Gandalf the Grey", "A title and two rows."),
    "collapse": ("An open section titled The Council's terms with two rows, Bearers 1 and Companions 8", "Open, with its chevron turned."),
    "carousel": ("A slide reading Stage 1 of 3: Bag End with three dots under it", "The first of three slides."),
    "skeleton": ("A grey circle beside a grey title bar and two grey text lines", "Circle, title and two lines of text."),
    "statistic": ("Three statistics: Leagues walked 1,795 up 42, Days from Rivendell 93 d down 2, and Companions lost 1 with Gandalf as the delta", "A label, a value, a unit and a delta."),
    "tabs": ("A tab strip with The map selected, The road with a footprints icon and a close cross, and Moria in red, and the page reading Where the road goes", "Three tabs, one closable, one coloured."),
    "tab": ("One tab, The road, with a footprints icon and a close cross, over its page", "A closable tab with an icon and a colour."),
    "timeline": ("A vertical timeline: Drafted on Mon, Reviewed on Thu with a badge reading 3 as its marker, and Released on Fri with a tag icon", "A dot, a badge and an icon as markers, and a pending end."),
    "entry": ("One timeline entry: a tag icon in green, Reviewed on Thu, and Two approvals under it", "An entry with an icon, a colour and a body."),
    "text": ("Three lines: The Red Book as a title, a caption in muted ink, and a bound status line", "A title, a caption and a bound value."),
    "link": ("Three links: Read the docs, Goldberry on the web with an external arrow, and Write to us in the visited ink", "An action link, an external link and a visited one."),
    "slider": ("A horizontal slider at 62 percent with ticks, a vertical fader, and a disabled slider at 70", "Horizontal, vertical, and disabled."),
    "knob": ("Two knobs: a small one at 62 and a large one at a quarter turn", "A detented knob and a large circular one."),
    "progress": ("Three bars: one at 40 percent, one at 62, and one indeterminate", "A value, a bound value, and indeterminate."),
    "spinner": ("Three spinners, small, regular and large", "Three sizes."),
    "affix": ("A scrolling list with a Hobbiton header pinned at its top and rows under it", "The section header is held at the top of its scroll."),
    "masonry": ("Three cards in a wall: Leagues with a statistic, The Company, and Provisions", "Cards in as many columns as fit."),
    "row": ("A toolbar: Find on the left, New in the accent on the right", "Two buttons with a spacer between."),
    "column": ("A line reading Delete this file? with Cancel and Delete in red under it on the right", "Text over a row of buttons."),
    "scroll": ("A scroll box listing Hobbiton, Bree, Rivendell and Moria", "A vertical viewport over a column."),
    "spacer": ("A title bar: Goldberry on the left and a Theme button on the right", "The spacer takes the room between."),
    "split-pane": ("Two panels side by side, The map and The road, with a divider between them", "Two panels and a divider."),
    "stack": ("A square with GB in it and a badge reading 3 over its corner", "A badge laid over a portrait."),
}


def webp_size(file):
    """The pixel size of a lossless WebP, from its VP8L header."""
    data = file.read_bytes()
    if data[12:16] == b"VP8L":
        bits = struct.unpack("<I", data[21:25])[0]
        return (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1
    if data[12:16] == b"VP8X":
        w = int.from_bytes(data[24:27], "little") + 1
        h = int.from_bytes(data[27:30], "little") + 1
        return w, h
    raise ValueError(f"{file} is not a WebP this script reads")


def shot(name, chapter_dir):
    light = ROOT / "images" / f"{name}-light.webp"
    dark = ROOT / "images" / f"{name}-dark.webp"
    if not light.exists() or not dark.exists():
        return None
    width = webp_size(light)[0] // 2
    alt, caption = ALT[name]
    prefix = "../" * (len(chapter_dir.parts))
    return (
        f'<div class="gb-shot">'
        f'<img class="gb-light" src="{prefix}images/{name}-light.webp" width="{width}" alt="{alt}">'
        f'<img class="gb-dark" src="{prefix}images/{name}-dark.webp" width="{width}" alt="{alt}">'
        f"<p>{caption}</p></div>"
    )


def fences(lines):
    """Every fence as (start, end, info), end being the closing line."""
    out = []
    i = 0
    while i < len(lines):
        m = FENCE.match(lines[i])
        if m:
            j = i + 1
            while j < len(lines) and lines[j].strip() != "```":
                j += 1
            out.append((i, j, m.group(1)))
            i = j + 1
        else:
            i += 1
    return out


def language(info):
    return info.split(",")[0]


def add_tabs(lines):
    """Wraps each run of adjacent kdl-first samples. Returns the new lines and how many runs."""
    runs = []
    found = fences(lines)
    k = 0
    while k < len(found):
        run = [found[k]]
        while k + 1 < len(found) and all(l.strip() == "" for l in lines[found[k][1] + 1 : found[k + 1][0]]):
            k += 1
            run.append(found[k])
        k += 1
        langs = [language(f[2]) for f in run]
        if len(run) < 2 or langs[0] != "kdl" or len(set(langs)) < 2 or not set(langs) <= TABBABLE:
            continue
        before = run[0][0] - 1
        while before >= 0 and lines[before].strip() == "":
            before -= 1
        if before >= 0 and lines[before].strip() == '<div class="gb-tabs">':
            continue
        runs.append((run[0][0], run[-1][1]))
    for start, end in reversed(runs):
        lines[end + 1 : end + 1] = ["", "</div>"]
        lines[start:start] = ['<div class="gb-tabs">', ""]
    return lines, len(runs)


def add_shots(lines, chapter_dir, report):
    """Puts a shot under every widget heading that has pictures and none yet."""
    added = 0
    i = 0
    while i < len(lines):
        m = WIDGET_HEADING.match(lines[i])
        if not m:
            i += 1
            continue
        name = m.group(2)
        j = i + 1
        while j < len(lines) and not ANY_HEADING.match(lines[j]):
            j += 1
        section = lines[i + 1 : j]
        if any(f'images/{name}-light.webp' in l for l in section):
            i = j
            continue
        block = None
        if name in ALT:
            block = shot(name, chapter_dir)
        if block is None:
            report.append(f"no picture for `{name}`" + ("" if name in ALT else " (no ALT entry)"))
            i = j
            continue
        # After the heading's first paragraph, before the first fence or block.
        k = i + 1
        while k < j and lines[k].strip() == "":
            k += 1
        if k < j and not lines[k].startswith("```") and not lines[k].startswith("<"):
            while k < j and lines[k].strip() != "":
                k += 1
        lines[k:k] = ["", block]
        added += 1
        i = j + 2
    return lines, added


def main():
    report = []
    for part in CATALOGUE:
        for chapter in sorted((ROOT / part).glob("*.md")):
            text = chapter.read_text()
            lines = text.split("\n")
            lines, shots = add_shots(lines, chapter.relative_to(ROOT).parent, report)
            lines, tabs = add_tabs(lines)
            new = "\n".join(lines)
            if new != text:
                chapter.write_text(new)
                print(f"{chapter.relative_to(ROOT)}: {shots} pictures, {tabs} tab groups")
    for page in TABBED_ELSEWHERE:
        chapter = ROOT / page
        text = chapter.read_text().replace('<div class="gb-pair">', '<div class="gb-tabs">')
        lines, tabs = add_tabs(text.split("\n"))
        new = "\n".join(lines)
        if new != chapter.read_text():
            chapter.write_text(new)
            print(f"{page}: {tabs} tab groups")
    for line in report:
        print("note:", line)
    return 0


if __name__ == "__main__":
    sys.exit(main())
