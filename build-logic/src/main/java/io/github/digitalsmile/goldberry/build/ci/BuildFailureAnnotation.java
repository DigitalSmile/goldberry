package io.github.digitalsmile.goldberry.build.ci;

import org.gradle.api.flow.BuildWorkResult;
import org.gradle.api.flow.FlowAction;
import org.gradle.api.flow.FlowParameters;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.Optional;

/**
 * Writes a failed build's "What went wrong" as an error annotation on the GitHub
 * Actions run (ADR-0338).
 *
 * <p>The failures {@link TestFailureAnnotations} cannot see: a {@code jlink} that
 * exits non-zero, a native-image link, a CMake configure. The showcase's Windows
 * image had failed on every run for a month with nothing public to say why.
 *
 * <p>A flow action rather than {@code buildFinished}, which is deprecated and
 * incompatible with the configuration cache.
 */
public abstract class BuildFailureAnnotation implements FlowAction<BuildFailureAnnotation.Parameters> {

    /** What the action is handed once the build's work is done. */
    public interface Parameters extends FlowParameters {

        /** @return the build's failure, empty if it succeeded */
        @Input
        Property<Optional<Throwable>> getFailure();
    }

    @Override
    public void execute(Parameters parameters) {
        parameters.getFailure().get()
                .map(failure -> WorkflowCommand.error("Gradle build failed", FailureText.of(failure)))
                .ifPresent(command -> System.out.println(command.render()));
    }

    /**
     * Registers the action. Instantiated through {@code objects.newInstance} so
     * Gradle injects the two services.
     */
    public abstract static class Registrar {

        @Inject
        protected abstract FlowScope getFlowScope();

        @Inject
        protected abstract FlowProviders getFlowProviders();

        /** Adds the action to this build, to run once its work has finished. */
        public void register() {
            getFlowScope().always(BuildFailureAnnotation.class, spec -> spec.getParameters().getFailure()
                    .set(getFlowProviders().getBuildWorkResult().map(BuildWorkResult::getFailure)));
        }
    }
}
