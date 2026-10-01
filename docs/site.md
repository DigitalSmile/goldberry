# goldberry.dev: the landing page and the book

The decision is [ADR-0509](../book/src/adr/0509-goldberry-dev-is-the-landing-page-and-the-book-is-its-docs.md).
What to edit on the page is in [`site/README.md`](../site/README.md). This file
is the runbook and the status.

| Path | What it is |
|---|---|
| `site/` | The landing page, deployed to `/`. `content.js` and `news.js` hold the text |
| `book/` | The mdBook, deployed to `/docs/` |
| `.github/workflows/pages.yml` | Builds both, checks the page's links into the book, and deploys on `master` |
| `build-logic/.../build/site/SiteTest.java` | The same checks with no build, in `./gradlew :build-logic:test` |

## Status

| Step | State |
|---|---|
| `site/` and `pages.yml` brought in from `goldberry-site.zip` | done 2026-10-01 |
| `SUMMARY.md`: the template moved to the end, so mdBook 0.5 builds the book | done 2026-10-01 |
| `pages.yml` checks `news.js` links as well as `content.js` | done 2026-10-01 |
| `SiteTest` / `BookLinkTest` drift guards | done 2026-10-01 |
| Full build reproduced locally (mdBook 0.5.4, Node 22): 518 pages, all links resolve | done 2026-10-01 |
| Settings ▸ Pages ▸ Source = **GitHub Actions** | **manual, pending** |
| DNS at GoDaddy (records below) | **manual, pending** |
| Custom domain `goldberry.dev` in Settings ▸ Pages, then **Enforce HTTPS** | **manual, pending** |
| Domain verified on the account (Settings ▸ Pages ▸ Verified domains) | **manual, recommended** |
| The book as documentation | next ([below](#next-the-book)) |

## Going live, once

The order matters. `.dev` is HSTS-preloaded, so until GitHub has a certificate
for the domain, browsers will not open the page at all.

1. **Pages source.** Repository ▸ Settings ▸ Pages ▸ Build and deployment ▸
   Source = *GitHub Actions*. The `github-pages` environment is created on
   the first deploy and allows `master` by default.
2. **Deploy once.** Push to `master`, or run *Pages* from the Actions tab
   (`workflow_dispatch`). The site is now at `https://digitalsmile.github.io/goldberry/`
   with broken absolute paths. That is expected: it is built for the root of a
   domain.
3. **DNS.** `goldberry.dev` is served by GoDaddy (`ns13/ns14.domaincontrol.com`).
   It currently points at GoDaddy's parking page (`13.248.243.5`,
   `76.223.105.230`), and `www` is a CNAME to the apex. Replace them with:

   | Type | Name | Value |
   |---|---|---|
   | A | `@` | `185.199.108.153` |
   | A | `@` | `185.199.109.153` |
   | A | `@` | `185.199.110.153` |
   | A | `@` | `185.199.111.153` |
   | AAAA | `@` | `2606:50c0:8000::153` |
   | AAAA | `@` | `2606:50c0:8001::153` |
   | AAAA | `@` | `2606:50c0:8002::153` |
   | AAAA | `@` | `2606:50c0:8003::153` |
   | CNAME | `www` | `digitalsmile.github.io.` |

   Also turn off GoDaddy's domain forwarding and parking, if either is on. There
   are no CAA records today. If one is ever added, it must allow
   `letsencrypt.org`.
4. **Custom domain.** Settings ▸ Pages ▸ Custom domain = `goldberry.dev` ▸
   Save. The DNS check turns green once the records have propagated
   (`dig +short goldberry.dev` shows the 185.199.x.153 addresses). The
   certificate follows within the hour. Then tick **Enforce HTTPS**.
5. **Verify the domain.** Profile ▸ Settings ▸ Pages ▸ Add a domain. GitHub
   gives a `_github-pages-challenge-DigitalSmile` TXT record to add at GoDaddy.
   Without it, anyone can claim `goldberry.dev` on their own repository during a
   window when this one is not serving it.
6. **Check.** `https://goldberry.dev/`, `https://www.goldberry.dev/`
   (redirects), `https://goldberry.dev/docs/`, `/sitemap.xml`, `/robots.txt`,
   `/llms.txt`. Then submit the sitemap in Google Search Console.

## Preview locally

```bash
python3 -m http.server -d site 8000            # the page alone, rendered in the browser
```

The whole site as CI builds it, with mdBook 0.5.4 on `PATH`:

```bash
rm -rf /tmp/_site && mkdir -p /tmp/_site && cp -R site/. /tmp/_site/ && rm /tmp/_site/README.md
MDBOOK_OUTPUT__HTML__SITE_URL=/docs/ mdbook build book -d /tmp/_site/docs
node site/build.mjs /tmp/_site "$(sed -n 's/^goldberryVersion=//p' gradle.properties)" 0
python3 -m http.server -d /tmp/_site 8000
```

## Next: the book

The workflow already publishes `book/` at `/docs/`, so a documentation book is
built and deployed by writing chapters. Nothing in `pages.yml` changes. What is
ready, and what that step has to decide:

- **Ready.** mdBook pinned at 0.5.4, which rejects unknown `book.toml` keys, so
  a misspelt option fails the build. `site-url` is set to `/docs/` by the
  workflow, so 404 pages and relative links work under the subpath while a
  local `mdbook serve` still works at the root. Every page has an *edit this
  page* link. Every landing-page link into the book is checked twice: by
  `SiteTest` against `book/src`, and by `pages.yml` against the built book.
- **To decide: structure.** Today the book is six chapters and 509 decision
  records. A guide wants its own parts (Getting started, Markup, Styling,
  Widgets, Native image, ...) ahead of the log, with the log moved into a
  final *Reference ▸ Decisions* part. `content.js` links `docs/status.html`,
  `docs/native.html` and `docs/adr/`. If those move, the links move with them,
  and `SiteTest` fails until they do.
- **To decide: search.** The index is 19.5 MB, almost all of it the log
  (mdBook warns). Options: `[output.html.search] heading-split-level = 0`
  and `boost-*` tuning, a separate book for the log at `/docs/adr/`, or
  excluding the log from search via a second mdBook build.
- **To decide: theme.** The book uses mdBook's `navy`. Matching the landing
  page's colours and leaf mark means a `book/theme/` override (`css/variables.css`,
  `favicon.svg`) plus `additional-css`.
- **To decide: code samples.** Samples that compile are worth having. Either
  `{{#include}}` from `example/` source with anchors, or a test that extracts
  the samples and compiles them.
- **Nice to have.** A check of the links inside the book, for example `lychee`
  over `_site/docs` in `pages.yml`. Today only the landing page's links into
  the book are checked.
