# ADR-0634: The showcase hands logback its file

- **Status:** Accepted
- **Date:** 2026-10-18

## Context

ADR-0506 timed the native showcase's start from the kernel's clock and found
about 50 of the 70 ms before the toolkit's first line spent in a host name
lookup through `libnss_mdns4_minimal`. The image was stripped, so whose lookup
it was went unrecorded; it was not logback's `HOSTNAME`, and the JVM did no such
thing.

An strace of the image on 2026-10-18 named the host: `dev.goldberry.example`,
the showcase's module, asked for twice (A and AAAA) of the local DNS stub, each
answered NXDOMAIN after about 36 ms. With `-Dlogback.configurationFile` set to a
file path the lookups disappeared, which put it in logback's default search.
logback 1.6.5's `DefaultJoranConfigurator`, having found `logback.xml`, warns
when the class path holds more than one and counts them by putting every URL for
the name into a `HashSet<URL>`. `URL.hashCode` resolves the URL's host. A
resource in a named module of a native image has the module's name as its
host; on the JVM a module resource's URL has none, so nothing is resolved there.

## Decision

**The showcase configures logback itself.** `ShowcaseLogging`, in a new
`dev.goldberry.example.logging` package, is a logback `Configurator` provided
by the example's module descriptor. logback runs every provided configurator
before its default one and stops at the first that answers
`DO_NOT_INVOKE_NEXT_IF_ANY`. This one reads the module's own `logback.xml`
through the same `JoranConfigurator` the default uses, and answers that. A
file that is missing or does not parse answers `INVOKE_NEXT_IF_ANY`, so the
default runs and says why.

logback becomes a compile-time dependency of the showcase alone (`compileOnly`,
`requires static`); it stays `runtimeOnly` in what the build puts on the path,
and nothing the toolkit publishes depends on it.

Rejected:

- **Setting `logback.configurationFile`**: a classpath name goes through the
  same search and the same set; a file path does not exist inside an image;
  and a property set in `main` comes after the first logger a static
  initialiser makes.
- **An `InetAddressResolverProvider` that refuses module-shaped names**: it
  would hide one library's lookup by guessing at what a host name looks like.
- **Waiting for logback**: the check is a warning about duplicate files, and
  hashing URLs to count them is an old pattern; nothing upstream suggests it is
  going away.

## Consequences

- Traced again, the image asks the DNS stub nothing. Under strace, the time
  from `execve` to the toolkit's first line went from 146 ms to 14 ms; the one
  remaining `nsswitch.conf` read is the user's name, as on the JVM.
- `ShowcaseLoggingTest` reads the real file into a fresh context, falls back on
  a missing or broken one, and holds the module descriptor to the provider.
- Any application with a `logback.xml` in a named module pays the same lookups
  in its native image. The guide's native chapter says so at release, with the
  same remedy (`docs/snapshot/native-logback-lookup.md`).
