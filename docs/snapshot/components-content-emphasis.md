# Document typography

Release edits for `book/src/components/content.md` (ADR-0636, ADR-0748 and ADR-0749).

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

For automatic hyphenation, set the language and enable `hyphens: auto`.

```html
<p lang="en" style="hyphens: auto">An introduction to internationalization.</p>
```

For Markdown, set `lang="en"` on `markdown-view` or an ancestor and use
`.md-prose { hyphens: auto }` in the application stylesheet. The bundled
patterns use American English spelling. An inserted hyphen appears only when
the word wraps and the word copies with its original spelling. A link button
has a single label. When its text mixes explicit languages, it uses ordinary
wrapping and any authored soft hyphens.

The default `hyphens: manual` permits an explicit soft hyphen (U+00AD or
`&shy;` in HTML) in any language. Use `hyphens: none` to disable these
breaks. Under Markdown’s limitations, replace the sentences about faux
oblique and word rows with “Emphasis uses the italic face.” Under HTML’s
limitations, remove the sentence about mixed faces requiring an engine.
