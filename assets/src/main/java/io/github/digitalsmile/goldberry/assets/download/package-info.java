/// Fetching a pinned asset over HTTP, retrying when the failure is the network's
/// rather than the request's: a 5xx, a 408, a 429, or a dropped connection.
///
/// Checksums are not this package's business. The asset cache in
/// `…assets.prepare` refuses a download that arrives whole and hashes wrong,
/// without retrying.
package io.github.digitalsmile.goldberry.assets.download;
