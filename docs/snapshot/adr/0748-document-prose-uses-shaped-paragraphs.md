# ADR-0748: Document prose uses shaped paragraphs

- **Status:** Accepted
- **Date:** 2026-11-25
- **Relates to:** ADR-0295, ADR-0301, ADR-0615, ADR-0710

## Context

HTML and Markdown previously wrapped a flex row of individual word boxes.
Flex spacing could approximate a space, but the text painter could only
justify text it received as a paragraph. Selection also relied on each word
owning a box.

## Decision

Group adjacent text with the same CSS classes into runs and join the shaped
runs with the same paragraph API as rich text. A hard break starts another
paragraph in the same block container. Inline images contribute their logical
width to the line and rest on its baseline.

Keep the document's copy order, word boundaries and block memo. Map those
logical ranges to visible fragments after wrapping. The painter and this
mapping share the directional runs, line heights and expanded word gaps.
Use the resulting rectangles for selection and link hit testing. Each link
keeps its original button, keyboard action and single focus stop across lines.

## Consequences

Both document views accept `text-align: justify`. A soft wrap leaves the copied
text intact. Reading, shaping and selecting a paragraph require no additional
native dependency. Automatic hyphenation remains a separate text feature.
