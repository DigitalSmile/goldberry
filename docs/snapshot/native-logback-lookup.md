# logback's default search resolves a host name in a native image

Held back until the release (ADR-0634). One piece.

## `book/src/native.md`, `### What the trace does not record`, before the heading (at the end of `## Two metadata directories: traced, and written`)

**logback's own search costs a DNS round trip in an image.** Having found
`logback.xml`, logback checks for a second one by putting every URL for the
name into a hash set, and a URL's hash code resolves its host. In a native image
a resource in a named module has the module's name as its host, so a start asks
the resolver for something like `com.example.app`, twice, and waits for the
answer that it does not exist. On the JVM the URL has no host and nothing is
asked. The showcase avoids it by handing logback its file itself: a
`ch.qos.logback.classic.spi.Configurator`, provided by the module, that runs
`JoranConfigurator` on the module's `logback.xml` and answers
`DO_NOT_INVOKE_NEXT_IF_ANY`, so the default search never runs.
