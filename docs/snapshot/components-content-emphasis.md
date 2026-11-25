# Document typography

Release edits for `book/src/components/content.md` (ADR-0636 and ADR-0748).

In both views’ styling sections, remove `md-token` and `html-token` from the
class lists. A paragraph groups adjacent text of the same style into runs.
The `md-word` and `html-word` classes style those runs. Both views accept the same alignment rule.

```css
.md-prose, .html-prose {
  text-align: justify;
}
```

Word gaps expand on wrapped lines. The last line and the line before a hard
break retain their natural spacing. Inline images sit on the text baseline.
A link can cover several lines and remains a single Tab stop.

Under Markdown’s limitations, replace the sentences about faux oblique and
word rows with “Emphasis uses the italic face. Automatic hyphenation is
unavailable.” Under HTML’s limitations, remove the sentence about mixed faces
requiring an engine.
