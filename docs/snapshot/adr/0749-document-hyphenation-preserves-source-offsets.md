# ADR-0749: Document hyphenation preserves source offsets

- **Status:** Accepted
- **Date:** 2026-11-27
- **Relates to:** ADR-0615, ADR-0748

## Context

HTML and Markdown share paragraph shaping, line placement, selection and
link hit testing. A hyphen added when a word wraps must occupy space on that
line while the source word can still be copied and searched as written.

## Decision

Both views accept the inherited CSS property `hyphens`. The default,
`manual`, permits breaks at soft hyphens. `none` disables discretionary
hyphens. `auto` also consults the patterns for the language of the text.
An HTML element can set `lang` for its descendants. Both views can inherit
the language from a widget's `lang` attribute. An empty HTML language tag
clears the inherited language. Link buttons use one label. A label whose
words have different languages uses only its authored soft hyphens.

The first dictionary uses the American English hyph-utf8 patterns and
exceptions for English tags. Other languages and words outside the pattern
alphabet keep ordinary line breaking and any permitted explicit soft hyphens.

The document supplies break positions in the joined paragraph's UTF-16
coordinates. A style change within a word can change its face while the
dictionary still sees the word. The line breaker measures a candidate with
the hyphen in the preceding text's face. The line records the mark only if
that break is used. Selection, links, and copying continue to address
the original text.

## Consequences

Resizing a paragraph can add or remove a visible hyphen without editing the
document. The pattern data ships in the core JAR and in native images. The
original permissions and attribution are recorded with the distribution.

`TextFlow` and `Lettering` expose the new `hyphens` component. Their existing
constructors still use `manual`. Java source that deconstructs either record
needs a final component pattern, which can be `_`.
