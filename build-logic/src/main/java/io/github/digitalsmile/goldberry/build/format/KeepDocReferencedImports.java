package io.github.digitalsmile.goldberry.build.format;

import java.io.File;
import java.util.Objects;
import java.util.function.Function;

import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Provisioner;
import com.diffplug.spotless.java.PalantirJavaFormatStep;

/**
 * A Spotless step that runs another and then puts back the imports it removed
 * that a {@code ///} doc comment still uses -- see {@link MarkdownDocImports} for
 * why that is needed and what counts as a use.
 *
 * <p>A record, so equality and serialization -- which Spotless uses to decide
 * whether {@code spotlessCheck} is up to date -- are exactly the delegate's.
 *
 * @param delegate the step whose output is repaired
 */
public record KeepDocReferencedImports(FormatterStep delegate) implements FormatterStep {

    public KeepDocReferencedImports {
        Objects.requireNonNull(delegate, "delegate");
    }

    /**
     * palantir-java-format at {@code version}, with its unused-import removal made
     * to respect {@code ///} comments. For {@code FormatExtension.addStep}, which
     * supplies the provisioner that resolves the formatter's jar.
     *
     * @param version the palantir-java-format version, from the catalog
     * @return a step factory
     */
    public static Function<Provisioner, FormatterStep> palantir(String version) {
        Objects.requireNonNull(version, "version");
        return provisioner -> new KeepDocReferencedImports(PalantirJavaFormatStep.create(version, provisioner));
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String format(String rawUnix, File file) throws Exception {
        var formatted = delegate.format(rawUnix, file);
        // Null is Spotless for "no change"; the delegate removed nothing then.
        return formatted == null ? null : MarkdownDocImports.restore(rawUnix, formatted);
    }

    @Override
    public void close() throws Exception {
        delegate.close();
    }
}
