# CI matrix changes held back until the release

For `book/src/contributing/testing.md`, `## The CI matrix`.

1. Change the trigger cells of these rows:

| Workflow | Trigger |
|---|---|
| `linux.yml` | Pull request not only touching `site/`, and every push through `snapshot.yml` |
| `macos.yml`, `windows.yml` | Pull request not only touching `site/`, and every push through `snapshot.yml` |
| `snapshot.yml` | Push to `master` not only touching `site/` |
| `codeql.yml` | Pull request not only touching `site/`, and weekly |
| `qodana.yml` | Pull request and push, neither only touching `site/` |

2. Replace the `pages.yml` row's last cell with: "The landing page and this
   book, after `SiteTest`".

3. After the paragraph that begins "The per-OS workflows have no `push`
   trigger of their own", add:

A change to `site/` alone runs `pages.yml` and nothing else. The landing page is
not built into anything the library ships, so the workflows that build the
library ignore it, and `pages.yml` runs `SiteTest` itself. A change to `book/` is
never skipped, because `BookTest` and `BookMarkupTest` check the book against the
code. `SiteOnlyChangeTest` holds every workflow to both rules.
