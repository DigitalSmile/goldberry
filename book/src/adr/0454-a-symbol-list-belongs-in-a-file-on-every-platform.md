# 454. A symbol list belongs in a file, on every platform

Date: 2026-09-21

## Status

Accepted.

## Context

With [ADR-0450] in, the Windows superbuild compiled. It then failed at the
last step but one, linking the library it had just built every object for:

```
[679/681] Linking CXX shared library goldberry.dll
FAILED: [code=1] goldberry.dll goldberry.lib
C:\Windows\system32\cmd.exe /C ""...link.exe" ... /INCLUDE:goldberry_abi_version
/INCLUDE:SDL_Init ... (253 of them) ... /DEF:.../goldberry.def"
The command line is too long.
```

Two numbers explain it. The command is 8541 characters, of which 7697 are the
253 `/INCLUDE:` flags. `CreateProcess` would take 32767 — but Ninja runs the
link through `cmd.exe /C`, and `cmd` stops at **8191**. We were 350 over.

The flags are not optional. Every exported symbol needs one, or the static
archives contribute nothing: `SDL_Init` is referenced by no Goldberry source,
so without `/INCLUDE:` the linker never pulls it out of `SDL3-static.lib` and
the `.def` exports a name that is not in the image. That is the same
force-link problem Linux solves with `-u` and macOS with `-u _name`.

What made this hard to see is that **`windows.yml` was green on the same
commit**. It uses the Visual Studio generator, which drives MSBuild, which
passes the link through a response file of its own; `showcase.yml` uses Ninja,
which does not. One commit, two Windows jobs, opposite results, and nothing in
the failure naming `/INCLUDE:` as the thing that was too long.

It had also been true for a while and only just crossed the line. `eb30c1bf`
renamed 25 exported HarfBuzz symbols from `hb_*` to `goldberry_hb_*`, adding
ten characters each, and [ADR-0385] added six WebP animation entries. The list
had been growing toward 8191 for months and the build that crossed it is not
the build that caused it.

## Decision

**The MSVC force-link list goes in a response file**, which is what the other
two platforms already do with theirs.

That symmetry is the argument. Look at what each platform writes:

| platform | force-link | export list |
|---|---|---|
| Linux | `-u name` per symbol, inline | `goldberry.map`, a file |
| macOS | `-u _name` per symbol, inline | `goldberry.exported_symbols`, a file |
| Windows | `/INCLUDE:name` per symbol, **inline** | `goldberry.def`, a file |

Every export list is a file, on every platform. Windows was the only one whose
force-link list was also the longest thing on the command line, and it is the
only platform with a command-line limit low enough to care. So it becomes
`goldberry.force`, handed to `link.exe` as `@goldberry.force` — link.exe takes
as many response files as it is given, and CMake has already put the objects in
one of its own.

Linux and macOS keep their inline flags. `ld` and `ld64` are not invoked
through `cmd.exe`, the limit there is `ARG_MAX` in the megabytes, and changing
a thing that works to match a thing that had to change is not symmetry worth
having.

### Not "drop the flags, the .def already forces them"

Plausible, and not taken. MSVC does resolve a `.def` export by pulling the
defining object out of an archive, so most of the 253 would probably survive.
"Probably" is the problem: the failure mode is a symbol silently missing from
the DLL, which surfaces as an `UnsatisfiedLinkError` in Java on the first call
through it, on Windows only. A response file changes how the flags are
delivered and nothing about what they mean.

## Consequences

**The Windows Ninja link drops from 8541 characters to roughly 630**, and will not
approach the limit again as the export list grows — which it does with every
upstream symbol added, and nothing was watching.

**`windows.yml` and `showcase.yml` agree on Windows again.** They are the same
build through two generators, and a green one beside a red one on the same
commit is worse than two red: it reads as flakiness.

**A test holds the shape**, not the length. `msvcForceLinkListIsAResponseFile`
refuses an inline `/INCLUDE:${_symbol}` and requires the `@file`, and reports
the current inline size in its failure message — 7950 bytes today — so whoever
trips it learns the number without the test having to assert one that moves
every time a symbol is added.

**The response file is a `LINK_DEPENDS`**, so a changed export list relinks
rather than quietly reusing the old DLL.

[ADR-0385]: 0385-webp-is-written-and-animated.md
[ADR-0450]: 0450-the-webview2-runtime-ships-with-windows-its-headers-do-not.md
