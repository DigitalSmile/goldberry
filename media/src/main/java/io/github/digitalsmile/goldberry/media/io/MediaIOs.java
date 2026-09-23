package io.github.digitalsmile.goldberry.media.io;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/// Turns a [Source] into a [MediaIO], by URI scheme.
///
/// The order is: [MediaIOProvider]s that claim the scheme, highest priority first,
/// then the built-in protocols: `file:` ([FileIO]), and `http:` and `https:`
/// ([HttpIO], with ICY radio metadata when the server sends it).
public final class MediaIOs {

    private MediaIOs() {}

    /// Opens `source` with the providers [ServiceLoader] finds.
    ///
    /// The providers are looked up on each call rather than cached. Opening a
    /// source is rare against the cost of opening it, and an application that
    /// installs a provider after start-up still gets it.
    ///
    /// @throws UnsupportedSchemeException when nothing opens the scheme
    /// @throws IOException when the resource cannot be opened
    public static MediaIO open(Source source) throws IOException {
        return open(
                source,
                ServiceLoader.load(MediaIOProvider.class).stream()
                        .map(ServiceLoader.Provider::get)
                        .toList());
    }

    /// Opens `source` with exactly `providers`, and no service lookup.
    ///
    /// For a test, and for an application that wants to say which protocols
    /// exist rather than have the class path say it.
    ///
    /// @throws UnsupportedSchemeException when nothing opens the scheme
    /// @throws IOException when the resource cannot be opened
    public static MediaIO open(Source source, List<? extends MediaIOProvider> providers) throws IOException {
        var scheme = source.scheme();
        var claimed = providers.stream()
                .filter(provider -> provider.schemes().contains(scheme))
                .max(Comparator.comparingInt(MediaIOProvider::priority));
        if (claimed.isPresent()) {
            return claimed.get().open(source);
        }
        return switch (scheme) {
            case "file" -> FileIO.open(Path.of(source.uri()));
            case "http", "https" -> HttpIO.open(source);
            default -> throw new UnsupportedSchemeException(scheme);
        };
    }
}
