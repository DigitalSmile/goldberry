#!/usr/bin/env python3
"""Draws the guide's diagrams as pictures, in both themes (ADR-0515).

Each diagram is a few sticky notes, frames and arrows on a dotted board, the
way a whiteboard tool draws them, described in `diagrams.py` and rendered here
with Pillow at twice the detail. The output is `book/src/images/diagram-<name>-
light.webp` and `-dark.webp`, which a chapter shows with the same `gb-shot`
block the screenshots use, so the picture follows the theme.

    python3 book/diagrams/draw.py            # every diagram
    python3 book/diagrams/draw.py layers     # one

The faces are the toolkit's own, Inter and JetBrains Mono, read from the asset
cache `./gradlew :assets:prepareAssets` fills under `.gradle/assets`.
"""

import io
import math
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / ".gradle" / "assets"
OUT = ROOT / "book" / "src" / "images"

sys.path.insert(0, str(Path(__file__).resolve().parent))
import diagrams  # noqa: E402

# Drawn at SUPER times the logical size and brought down to SCALE times it:
# Pillow draws aliased lines, and the downsample is what smooths them.
SCALE = 2
SUPER = 4

# The two themes, with the sticky-note hues the board uses. Notes stay pastel on
# the dark board, as on a dark whiteboard, with dark ink; the board, the frames
# and the connectors take the theme's own colours.
SHADES = {
    "light": {
        "board": "#F4F5F8",
        "dot": "#CFD5DF",
        "ink": "#2E3440",
        "muted": "#5B6577",
        "line": "#4C566A",
        "frame": "#C3CBD9",
        "frame_fill": "#FFFFFF",
        "label_fill": "#FFFFFF",
        "shadow": (31, 37, 48, 70),
    },
    "dark": {
        "board": "#2E3440",
        "dot": "#3F4757",
        "ink": "#2E3440",
        "muted": "#C3CBD9",
        "line": "#D8DEE9",
        "frame": "#4C566A",
        "frame_fill": "#3B4252",
        "label_fill": "#3B4252",
        "shadow": (0, 0, 0, 120),
    },
}
NOTE_HUES = {
    "yellow": "#FFF1A8",
    "blue": "#CFE6FF",
    "green": "#D5F1CF",
    "pink": "#FFD6DC",
    "purple": "#E6DCF8",
    "orange": "#FFE0C2",
    "grey": "#E6EAF0",
}


def font(name, size):
    """A face from the asset cache, at `size` pixels of the supersampled canvas."""
    archive, member = {
        "sans": ("inter.zip", "extras/ttf/Inter-Regular.ttf"),
        "sans-medium": ("inter.zip", "extras/ttf/Inter-Medium.ttf"),
        "sans-bold": ("inter.zip", "extras/ttf/Inter-SemiBold.ttf"),
        "mono": ("jetbrains-mono.zip", "fonts/ttf/JetBrainsMono-Medium.ttf"),
    }[name]
    with zipfile.ZipFile(ASSETS / archive) as zipped:
        data = zipped.read(member)
    return ImageFont.truetype(io.BytesIO(data), size)


class Board:
    """One diagram being drawn: logical coordinates in, pixels out."""

    def __init__(self, width, height, shade):
        self.shade = SHADES[shade]
        self.k = SCALE * SUPER
        self.width, self.height = width, height
        self.image = Image.new("RGBA", (width * self.k, height * self.k), self.shade["board"])
        self.shadows = Image.new("RGBA", self.image.size, (0, 0, 0, 0))
        self.draw = ImageDraw.Draw(self.image)
        self._dots()

    def px(self, v):
        return int(round(v * self.k))

    def _dots(self):
        step = 24
        r = self.px(1.2)
        for y in range(step, self.height, step):
            for x in range(step, self.width, step):
                cx, cy = self.px(x), self.px(y)
                self.draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=self.shade["dot"])

    def _shadow(self, box, radius):
        x0, y0, x1, y1 = (self.px(v) for v in box)
        lift = self.px(3)
        layer = ImageDraw.Draw(self.shadows)
        layer.rounded_rectangle((x0, y0 + lift, x1, y1 + lift), radius=self.px(radius), fill=self.shade["shadow"])

    def frame(self, x, y, w, h, title=None):
        """A lane: a thin rounded outline with its title at the top left."""
        box = (x, y, x + w, y + h)
        self.draw.rounded_rectangle(
            tuple(self.px(v) for v in box),
            radius=self.px(14),
            fill=self.shade["frame_fill"],
            outline=self.shade["frame"],
            width=self.px(1.5),
        )
        if title:
            self.text(x + 16, y + 12, title, "sans-bold", 13, self.shade["muted"], tracking=True)

    @staticmethod
    def note_height(body=None, mono=None):
        """How tall a note with this much on it is."""
        lines = 0 if not body else len(body.split("\n"))
        return 12 + 22 + 17 * lines + (20 if mono else 0) + 10

    def note(self, x, y, w, h, title, body=None, hue="yellow", mono=None):
        """A sticky note: a pastel card with a title, a line or two of body, and a code line."""
        self._shadow((x, y, x + w, y + h), 10)
        self.draw.rounded_rectangle(
            tuple(self.px(v) for v in (x, y, x + w, y + h)),
            radius=self.px(10),
            fill=NOTE_HUES[hue],
        )
        ty = y + 12
        self.text(x + 14, ty, title, "sans-bold", 15, self.shade["ink"])
        ty += 22
        if body:
            for line in body.split("\n"):
                self.text(x + 14, ty, line, "sans", 12.5, self.shade["ink"])
                ty += 17
        if mono:
            self.text(x + 14, ty + 2, mono, "mono", 11.5, self.shade["ink"])

    def text(self, x, y, s, face, size, colour, anchor="la", tracking=False):
        f = font(face, int(self.px(size)))
        if tracking:
            s = s.upper()
        self.draw.text((self.px(x), self.px(y)), s, font=f, fill=colour, anchor=anchor)

    def text_width(self, s, face, size):
        f = font(face, int(self.px(size)))
        return self.draw.textlength(s, font=f) / self.k

    def arrow(self, points, label=None, curve=False, dashed=False):
        """A connector through `points`, with a head at the end and a label at its middle."""
        pts = [(self.px(x), self.px(y)) for x, y in points]
        if curve and len(pts) == 3:
            pts = self._bezier(pts)
        colour = self.shade["line"]
        w = self.px(2.2)
        if dashed:
            self._dashes(pts, colour, w)
        else:
            self.draw.line(pts, fill=colour, width=int(w), joint="curve")
        # The head: a filled triangle along the last segment.
        (x0, y0), (x1, y1) = pts[-2], pts[-1]
        angle = math.atan2(y1 - y0, x1 - x0)
        size = self.px(9)
        left = (x1 - size * math.cos(angle - 0.5), y1 - size * math.sin(angle - 0.5))
        right = (x1 - size * math.cos(angle + 0.5), y1 - size * math.sin(angle + 0.5))
        self.draw.polygon([(x1, y1), left, right], fill=colour)
        if label:
            # On the longest segment, where it hides the least.
            longest = max(zip(pts, pts[1:]), key=lambda seg: math.dist(seg[0], seg[1]))
            mid = ((longest[0][0] + longest[1][0]) / 2, (longest[0][1] + longest[1][1]) / 2)
            self.pill(mid[0] / self.k, mid[1] / self.k, label)

    def _dashes(self, pts, colour, w):
        on, off = self.px(7), self.px(5)
        for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
            length = math.hypot(x1 - x0, y1 - y0)
            if length == 0:
                continue
            ux, uy = (x1 - x0) / length, (y1 - y0) / length
            d = 0
            while d < length:
                e = min(d + on, length)
                self.draw.line(((x0 + ux * d, y0 + uy * d), (x0 + ux * e, y0 + uy * e)), fill=colour, width=int(w))
                d += on + off

    def _bezier(self, pts, steps=40):
        (x0, y0), (cx, cy), (x1, y1) = pts
        out = []
        for i in range(steps + 1):
            t = i / steps
            out.append((
                (1 - t) ** 2 * x0 + 2 * (1 - t) * t * cx + t**2 * x1,
                (1 - t) ** 2 * y0 + 2 * (1 - t) * t * cy + t**2 * y1,
            ))
        return out

    def pill(self, cx, cy, label):
        """A small rounded label centred on a point, over the connector."""
        w = self.text_width(label, "sans-medium", 11.5) + 16
        h = 20
        box = (cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
        self.draw.rounded_rectangle(
            tuple(self.px(v) for v in box),
            radius=self.px(10),
            fill=self.shade["label_fill"],
            outline=self.shade["frame"],
            width=self.px(1),
        )
        self.text(cx, cy, label, "sans-medium", 11.5, self.shade["muted"], anchor="mm")

    def caption(self, x, y, s, width=None, size=12):
        """A line of muted text, wrapped to `width` logical pixels when given."""
        lines = [s]
        if width:
            lines, line = [], ""
            for word in s.split():
                trial = (line + " " + word).strip()
                if line and self.text_width(trial, "sans", size) > width:
                    lines.append(line)
                    line = word
                else:
                    line = trial
            lines.append(line)
        for i, line in enumerate(lines):
            self.text(x, y + i * (size + 5), line, "sans", size, self.shade["muted"])
        return len(lines)

    def save(self, name, shade):
        blurred = self.shadows.filter(ImageFilter.GaussianBlur(self.px(4)))
        board = Image.new("RGBA", self.image.size, self.shade["board"])
        # The shadows go under the notes: the board, then the shadows, then
        # everything drawn — which means drawing happened on a copy of the board.
        composed = Image.alpha_composite(board, blurred)
        # `self.image` holds the dots and every shape over an opaque board; lift
        # the drawn pixels by differencing against the board colour is fragile,
        # so the drawn image is composed over the shadows with the board colour
        # made transparent where nothing was drawn.
        drawn = self.image.convert("RGBA")
        mask = _not_board(drawn, self.shade["board"])
        composed.paste(drawn, (0, 0), mask)
        final = composed.convert("RGB").resize(
            (self.width * SCALE, self.height * SCALE), Image.Resampling.LANCZOS
        )
        OUT.mkdir(parents=True, exist_ok=True)
        target = OUT / f"diagram-{name}-{shade}.webp"
        final.save(target, "WEBP", lossless=True)
        return target, final.size


def _not_board(image, board):
    """A mask of every pixel that is not the board colour."""
    r, g, b = Image.new("RGB", (1, 1), board).getpixel((0, 0))
    pixels = image.split()
    from PIL import ImageChops

    diff = ImageChops.difference(image.convert("RGB"), Image.new("RGB", image.size, (r, g, b)))
    mask = diff.convert("L").point(lambda v: 255 if v > 0 else 0)
    return mask


def main(argv):
    wanted = set(argv[1:])
    for name, spec in diagrams.ALL.items():
        if wanted and name not in wanted:
            continue
        for shade in SHADES:
            board = Board(spec["width"], spec["height"], shade)
            spec["draw"](board)
            target, size = board.save(name, shade)
            print(f"{target.relative_to(ROOT)} {size[0]}x{size[1]}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
