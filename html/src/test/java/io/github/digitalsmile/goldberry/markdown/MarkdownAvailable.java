package io.github.digitalsmile.goldberry.markdown;

import net.jqwik.api.lifecycle.LifecycleContext;
import net.jqwik.api.lifecycle.SkipExecutionHook;

/// [MarkdownRequirement], for jqwik.
///
/// A JUnit test skips by aborting. A jqwik property cannot: an abort thrown from a
/// property counts as a rejected try, and one thrown from `@BeforeContainer` is
/// reported as an execution *error* -- which is what CI's Java job, with no native
/// library, turned red on (ADR-0338). jqwik's own way to skip is this hook.
public final class MarkdownAvailable implements SkipExecutionHook {

    @Override
    public SkipResult shouldBeSkipped(LifecycleContext context) {
        return MarkdownRequirement.missing().map(SkipResult::skip).orElseGet(SkipResult::doNotSkip);
    }
}
