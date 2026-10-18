# A segmented bar is as wide as its widest segment

Held back until the release (ADR-0643). One piece.

## `book/src/components/choices.md`, `## `segmented``, the paragraph after the styling list

Replace "Segments are equal, each exactly `1/n` of the bar, so the indicator
travels by a percentage and the bar takes the width it is given." with:

Segments are equal, each exactly `1/n` of the bar, so the indicator travels by a
percentage. Each is as wide as the widest segment's label, so a bar that nothing
sizes is as wide as its segments. A column that stretches it, or a `width`,
still sizes it, and the segments share what they are given equally.
