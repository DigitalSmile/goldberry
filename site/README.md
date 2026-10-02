# goldberry.dev landing page

Preview locally, no build step: `python3 -m http.server -d site 8000`.

| To change | Edit |
|---|---|
| Version | nothing: CI reads `goldberryVersion` from `gradle.properties`. `version` in `content.js` is only the local-preview fallback |
| Version note, tagline, intro, hero buttons, numbers strip | `content.js` (`stats` for the numbers) |
| The three big feature rows (text + sticky screenshot) | `highlights` in `content.js` |
| Widget-name band | `markupNames` in `content.js`; the count is computed from the list |
| Design-system card and the CSS card | `designSystem` and `cssSamples` in `content.js` |
| Closing card and footer columns | `cta` and `footer.columns` in `content.js` |
| Comparison table | `compare` in `content.js`; a cell is a string or `{ t, tone }` with tone `good`, `mid` or `no` |
| News | `news.js`: add an entry at the top of `items`; the newest one is also the pill above the headline; `limit` sets how many show |
| Feature cards (icon, title, text, bullet points) | `features` in `content.js`; icon names come from `assets/icons.js` |
| "Built on" library links | `builtOn` in `content.js` |
| Page title, description, keywords, canonical URL | `seo` in `content.js` |
| Hero drawing (the sources → Goldberry → windows animation) | the `<svg class="engine">` in `index.html`; labels are plain `<text>`, timing lives in `assets/style.css` under "hero engine animation" (`--cycle` is the hot-reload beat) |
| Hero backdrop | the illustration in `.hero-bg`; opacity and crop in `assets/style.css`, the parallax factor (`.35`) in `assets/site.js` |
| Leaf mark | `assets/favicon.svg`, the inline SVG in the header of `index.html`, and `LEAF` in `assets/site.js` share one path |
| Showcase screenshots | `shots` in `content.js`; images in `assets/shots/` (1200x900, from `example/src/test/resources/golden`) |
| Showcase downloads | `downloads` in `content.js`; set `available: true` after the first release |
| Quick start code tabs (`group`: `deps`, `jvm`, `native`); `{{version}}` becomes the real version | `quickstart` in `content.js` |
| Performance numbers | `perf.start` (the start-up trace) and `perf.run` in `content.js` |
| FAQ (also the FAQPage schema and llms.txt) | `seo.faq` in `content.js` |
| Showcase loupes | `zooms` on each entry of `shots`: region centres in 1200×900 picture coordinates |
| Star count | stamped by CI from the GitHub API, refreshed by the browser hourly; nothing to edit |
| Section order, menu | `index.html` |
| Colours, type, motion | the tokens at the top of `assets/style.css` |
| Analytics and the cookie banner | `assets/consent.js`: the Metrika snippet, the counter id and the banner. Raise `VERSION` there when what is counted changes, so everybody is asked again. Test with `node --test "site/test/*.test.mjs"` |
| Privacy policy | `privacy.html`; change its date with it, and keep it in step with `consent.js`. `SiteTest` checks the parts it must name |

Deployed by `.github/workflows/pages.yml`: this folder goes to `/`, the mdBook in
`book/` to `/docs/`. CI runs `build.mjs`, which writes the content into
`index.html` as static HTML (so crawlers and link previews need no JavaScript),
fills the `<head>` from `seo`, and generates `sitemap.xml`, `robots.txt` and `llms.txt` (the page as agents read it).
`assets/render.js` is the one place that turns content into HTML; the browser
and the build both use it.
