# 450. The WebView2 runtime ships with Windows; its headers do not

Date: 2026-09-21

## Status

Accepted.

## Context

[ADR-0441] made `libgoldberry-webview` the one artifact in this build that an
installation is allowed not to have, and wrote the rule for deciding whether to
build it: Linux probes for WebKitGTK's development headers and degrades when
there are none, while macOS and Windows are always on. The line that put them
there said:

> WKWebView is a system framework and WebView2 ships with the OS, so there is no
> optional header to probe for.

Half of that is right. WebView2 is two things with one name, and the sentence
conflates them:

- the **runtime** is Edge. It does ship with Windows, and it is what
  `WebView2Loader.dll` eventually talks to;
- the **SDK header** `WebView2.h` does not ship with anything. It is delivered
  exclusively in the `Microsoft.Web.WebView2` NuGet package, and `webview.h`
  includes it by name.

So the Windows leg of the superbuild compiled every upstream, linked
`goldberry.dll`, reached the one translation unit of the second library, and
stopped:

```
webview.h(2788,10): error C1083: Cannot open include file: 'WebView2.h'
```

This broke `master`. `publish / windows / libgoldberry (windows-x64)` failed at
the Build step, and `publish / windows / Verify layouts (windows-x64)` failed
behind it at `download-artifact`, because the artifact it waits for is uploaded
by the job that had just died. Two red jobs, one cause. Linux and macOS were
unaffected: Linux probes, and on macOS WebKit really is a framework in the SDK.

webview's own CMake solves this by downloading the package
(`WEBVIEW_USE_BUILTIN_MSWEBVIEW2`, on by default). This build never runs it:
`FetchContent_Declare(webview ... SOURCE_SUBDIR do-not-add-this-subdirectory)`
takes the header and none of the project around it, on purpose, because the
implementation *is* the header and there is nothing to link.

## Decision

**Fetch the WebView2 SDK headers, pin them in the catalog, and probe for the
result.**

`webview2 = "1.0.1150.38"` goes in `gradle/libs.versions.toml` beside every
other upstream ref ([ADR-0035]) — including on the two platforms that never
download it, because a ref that appears only inside a platform branch of a
CMake file is one nobody reviews. It is the version webview 0.12.0 pins and
therefore the version upstream tests against, which is why it is a 2022 date
rather than the newest available.

CMake fetches the package on Windows only, then looks for `WebView2.h` inside
it. Finding it turns the library on; not finding it leaves it off with a message
saying so, exactly as a Linux machine without WebKitGTK behaves.

### Headers only

Nothing from the package is linked and nothing from it is shipped. The six
libraries on the Windows link line — `advapi32 ole32 shell32 shlwapi user32
version` — are Windows' own, and webview's built-in loader resolves
`WebView2Loader.dll` with `LoadLibrary` at run time, against the Edge already on
the machine. This is the same shape Linux has: headers at build time, the
desktop's own engine at run time, and a 70-odd KB shim in between.

### A URL, and no hash

Every other upstream here is fetched by git tag, and a tag is *mutable* — it can
be moved. A NuGet version cannot: the registry refuses to republish a version it
has already served. The URL is therefore a stronger pin than the ones around it,
and adding a `URL_HASH` would buy nothing except a second place to edit on a
bump and a confusing failure for whoever forgot.

`DOWNLOAD_NAME` is set because the URL's last component is a bare version with
no extension, and CMake will not extract an archive whose name tells it no
format.

### Not fatal

A fetched package that holds no `WebView2.h` prints the reason and builds one
library instead of two. This is [ADR-0441]'s rule, not a new one: everything
else in this build is required, because a toolkit that silently drops its text
shaping is the bug [ADR-0325] exists about — and a web view is a feature an
application opts into by calling for it. A layout change at Microsoft's end
should cost the second library, not the whole native build.

`-DGOLDBERRY_WEBVIEW2_INCLUDE_DIR=<path>` skips the download for a machine that
already has the SDK installed.

## Consequences

**The Windows superbuild compiles again**, and for the first time it produces
`goldberry-webview.dll` rather than dying on the way to it.

**Windows builds now download 1.7 MB from nuget.org.** It lands in
`FETCHCONTENT_BASE_DIR` with every other upstream, which lives outside `build/`
([ADR-0038]), so `clean` does not throw it away and only the first build pays.

**A Windows machine that cannot reach nuget.org fails to configure** — the same
way one that cannot reach github.com already fails, and for the same reason.
The escape hatch is the include-directory flag.

**The `Goldberry web view:` status line now names the engine on all three
platforms** rather than only the pkg-config module on Linux, because the reason
a build has no web view is now platform-specific and the old message told a
Windows reader to install a Debian package.

**Still UNVERIFIED at run time.** This is a compile and a link, which is more
than Windows had, and it is not a page that has been opened. [ADR-0441]'s
caveat stands for macOS and Windows both: the shim is written against webview's
C++ API, that API is the same on every backend, and nobody has run it here.
`natives`' layout tests do not reach it — they check `libgoldberry`, and this is
the other library.

**CI still does not ship it.** `upload-artifact` names `goldberry.dll` alone on
Windows, `libgoldberry.so` on Linux and `libgoldberry.dylib` on macOS, so the
second library is built and discarded on every platform. That is a packaging
decision this one does not make; it is written down in
`docs/todo-2026-09-21.md`.

[ADR-0035]: 0035-the-catalog-is-the-only-place-a-ref-lives.md
[ADR-0038]: 0038-the-superbuild-download-is-not-a-hang.md
[ADR-0325]: 0325-a-build-says-what-it-can-ask-the-desktop.md
[ADR-0441]: 0441-a-web-page-is-a-window-not-a-box.md
