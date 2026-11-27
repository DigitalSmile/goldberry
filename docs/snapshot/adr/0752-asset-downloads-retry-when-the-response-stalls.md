# ADR-0752: Asset downloads retry when the response stalls

- **Status:** Accepted
- **Date:** 2026-11-28
- **Relates to:** ADR-0357, ADR-0739

## Context

A cold Showcase run could wait indefinitely in `:example:prepareAssets`.
The asset downloader used a connection timeout. A server could accept the
connection and stall before sending headers or during the response body.
JDK 25 returns a streaming response once its headers arrive.
[JDK 25 HTTP body subscribers](https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpResponse.BodySubscribers.html#ofInputStream())

## Decision

Set a 30-second request timeout for the response headers. Give each blocked
body read the same limit. If it expires, close the response stream to cancel
the HTTP exchange and let the retry policy handle the timeout. A transfer can
take longer overall while it continues to deliver bytes.

The build prints the asset name and URL before requesting it. Transient
failures still get four attempts with waits of 2, 4 and 8 seconds. The cache
verifies the completed archive before moving it into place.

## Verification

Loopback tests stall before headers and after the first body byte. They verify
that a second request completes the file and that an exhausted body timeout
reports the URL.

A Linux checkout with an empty cache completed `:example:prepareAssets`
with the spelling dictionary and Unicode emoji table from their pinned URLs.
