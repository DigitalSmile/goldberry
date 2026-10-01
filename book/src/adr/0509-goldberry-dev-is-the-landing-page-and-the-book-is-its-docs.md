# ADR-0509: goldberry.dev is the landing page, and the book is its /docs/

- **Status:** Accepted
- **Date:** 2026-10-01
- **Relates to:** `docs/site.md`, `site/README.md`, `.github/workflows/pages.yml`

## Context

The project has a domain, `goldberry.dev`, and nothing on it: the registrar's
parking page answers there. Everything a newcomer can read is in the repository
itself: the README, and this book read as Markdown on GitHub, where the
cross-links between records work and the table of contents does not.

A landing page was written outside the repository: a static `site/` folder
(HTML, one stylesheet, content in `content.js` and `news.js`, a Node script that
prerenders it for crawlers), and a Pages workflow that builds it together with
this book. Three things had to be settled to bring it in:

- **Where it lives.** A separate repository, a `gh-pages` branch, or a folder
  in this one.
- **How the domain reaches it.** `.dev` is on the HSTS preload list, so a browser
  never speaks plain HTTP to it. The page is unreachable, not merely insecure,
  until GitHub has issued its certificate.
- **The book was not buildable.** `SUMMARY.md` had the `[Template]` suffix
  chapter in the middle of the decision log, with ADR-0441 onwards listed after
  it. mdBook refuses a numbered list after a suffix chapter. Nothing built the
  book, so nothing noticed.

## Decision

The landing page lives in `site/` in this repository. `pages.yml` deploys it with
GitHub Actions as the Pages source: `site/` at `/`, and the mdBook in `book/` at
`/docs/`, as one artifact from one job. The page's version is read from
`goldberryVersion` in `gradle.properties`, its links into the book are checked
against the built book before deploying, and a pull request builds without
deploying.

The domain is the apex, `goldberry.dev`. It carries GitHub's four A and four
AAAA records, and `www` is a CNAME to `digitalsmile.github.io` so GitHub
redirects it to the apex. The domain is set in the repository's Pages
settings. `site/CNAME` carries it too, for a reader of the repository, though an
Actions deployment ignores the file. HTTPS is enforced as soon as the
certificate exists.

mdBook is pinned (0.5.4) and downloaded as a release binary. The prerender is
plain Node with no dependencies, so there is no `package.json` and no lock file
to keep current. `SiteTest` in `build-logic` checks the same links against
`book/src` without building anything. It also checks that the template stays
the last line of `SUMMARY.md`.

## Alternatives considered

- **A `gh-pages` branch, with mdBook's `cname` option.** It puts a second
  history beside `master` that nobody reviews, and the option writes `CNAME`
  into the book's output root, which would be `/docs/CNAME`, not the site root.
- **A separate `DigitalSmile.github.io` repository.** That would separate the
  version, the screenshots and the book links from the code they describe. The
  version would then be typed twice, once in each repository.
- **Netlify or Cloudflare Pages.** Both work. Either is another account, another
  set of credentials, and another dashboard for a site that needs no server-side
  behaviour.
- **A static site generator (Hugo, Astro).** The page is one HTML file and two
  content files. A generator would bring in a toolchain and a lock file to
  render them, when 120 lines of Node already do it.
- **The book at the root and the landing page somewhere else.** The landing page
  is what a link preview and a search result show. The book is for a reader who
  has already decided to read.

## Consequences

- Every push to `master` that touches `site/`, `book/` or `gradle.properties`
  deploys. A record added to the decision log is live a minute later. There is
  no separate release step for the site, and none for the book.
- A weekly scheduled build keeps the stamped GitHub star count from going stale.
  It costs one cheap workflow run per week.
- The book's search index is 19.5 MB, almost all of it the decision log. That is
  acceptable for a reference and too much for a user guide that is loaded on
  every page. The next step, the book as documentation, has to settle it
  (`docs/site.md`, "Next: the book").
- The deployed artifact is about 43 MB, well inside the 1 GB Pages limit.
- The domain is outside this repository's control. Changing the DNS records, and
  verifying the domain on the GitHub account against takeover, are manual steps
  listed in `docs/site.md`. The page is not live until they are done.
- `book.toml` now gives every page an "edit this page" link to its source on
  `master`.
