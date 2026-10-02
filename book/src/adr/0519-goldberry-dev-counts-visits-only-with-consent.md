# ADR-0519: goldberry.dev counts visits, and only with consent

- **Status:** Accepted
- **Date:** 2026-10-02
- **Relates to:** [ADR-0509](0509-goldberry-dev-is-the-landing-page-and-the-book-is-its-docs.md),
  [ADR-0511](0511-the-book-is-a-guide-first-and-the-log-is-its-last-part.md),
  `site/privacy.html`, `site/assets/consent.js`, `docs/site.md`, `site/README.md`

## Context

goldberry.dev has two halves, built by one workflow from this repository: the
landing page at `/` from `site/`, and the book at `/docs/` from `book/`. Until
now neither had any analytics. Nobody could say how many people read the
guide, which chapters they open, or whether they arrive from search, from the
repository or from a link someone shared.

The maintainer has a Yandex Metrika counter, 113266145, and its snippet. As
configured, it sets cookies and turns on the click map, link tracking,
accurate bounce tracking and Webvisor, which records sessions so they can be
replayed. Yandex's snippet runs as soon as the page loads, and it adds a
`<noscript>` pixel for visitors without JavaScript.

Under the GDPR and the ePrivacy rules, cookies that are not strictly
necessary, and a session recording, need the visitor's consent before they
are set. Metrika's data is processed by YANDEX LLC in Russia, which has no
adequacy decision. Consent is the only basis this site can offer for the
counting and for the transfer. The site had no privacy policy at all.

## Decision

**Metrika loads only after the visitor accepts, and a privacy policy says
what it does.**

- **One consent script.** `site/assets/consent.js` holds Yandex's snippet,
  unchanged, inside a function that runs only for a visitor who has accepted.
  Until there is an answer, it shows a banner with *Decline*, *Accept* and a
  link to the policy, and sets nothing. It keeps the answer in localStorage
  under `gb-consent`, with a version number, and `/` and `/docs/` share that
  store. Raising the version asks everybody again, and is how a change in
  what is counted gets fresh consent.
- **Every page loads it.** The landing page and `privacy.html` load it from
  `assets/`. The book loads it from `book/theme/head.hbs`, which mdBook puts
  into the head of every page, as `{{ path_to_root }}../assets/consent.js`,
  the same "one level above the book" the header's brand link uses. Under
  `mdbook serve` there is no landing page above the book, so a local preview
  neither asks nor counts.
- **No pixel.** Yandex's `<noscript>` image counts a visitor who cannot be
  asked, so it is left out.
- **Withdrawal is as easy as consent.** `privacy.html#cookies` shows the
  current answer with both buttons. The landing page's footer and the foot of
  every book page (written by `goldberry.js`) link there as *Cookie
  settings*. Declining after accepting deletes Metrika's `_ym` cookies on the
  host and the domain, and its `_ym` keys in localStorage, then reloads, so
  the running counter stops.
- **The policy** (`site/privacy.html`) names the controller and how to reach
  them, what every visit sends to GitHub Pages, Google Fonts and the GitHub
  API, what Metrika collects with consent, the processor and the transfer to
  Russia, the cookies and local-storage keys, the legal bases, and the
  visitor's rights.
- **Held by tests.** `site/test/consent.test.mjs` runs `consent.js` in Node
  against a small fake DOM with no dependencies. It checks that nothing loads
  before an answer, that *Accept* loads the counter once, that a stored
  answer is honoured, that a changed version asks again, and that withdrawal
  deletes the right cookies and keys and nothing else. `pages.yml` runs it
  before it builds. `SiteTest` in `:build-logic` holds the arrangement: no
  page names `mc.yandex.ru` or has a `<noscript>`, every page loads the
  consent script, the counter id lives in `consent.js`, the policy exists and
  names what it must, and the links to it stay in place.

## Consequences

- Only visitors who accept are counted, so Metrika's numbers undercount
  readers by however many decline or ignore the banner.
- The landing page still loads Google Fonts before any consent. The policy
  says so. Self-hosting the two typefaces would remove that request.
- The policy names GitHub as the contact channel, as the site has no email
  address. A request someone does not want to make in public goes through
  the maintainer's GitHub profile.
- A change to the counter's options, or a new tracker, means raising
  `VERSION` in `consent.js` and updating `privacy.html` in the same commit,
  along with the date at its top.
