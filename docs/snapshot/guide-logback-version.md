# The Logback version in the guide's snippets

Two one-line pieces, for the release commit. `:example` moved to Logback 1.6.5
on 2026-10-10, because 1.6.3's fix for an MDC path-traversal CVE was
incomplete (the catalog's `logback` entry). The guide still tells an
application to add 1.6.3.

## 1. `book/src/getting-started/installing.md`, the `runtimeOnly` line under the no-provider paragraph

```groovy
runtimeOnly 'ch.qos.logback:logback-classic:1.6.5'
```

## 2. `book/src/guide/logging.md`, the first sample

```groovy
runtimeOnly 'ch.qos.logback:logback-classic:1.6.5'
```

If Logback moves again before the release, write the catalog's version here
instead. The two snippets should name whatever `gradle/libs.versions.toml`
pins on the day the book is published.
