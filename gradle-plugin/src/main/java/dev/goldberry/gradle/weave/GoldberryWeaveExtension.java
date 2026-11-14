package dev.goldberry.gradle.weave;

import org.gradle.api.provider.Property;

/**
 * The {@code goldberry} block a build configures the plugin with.
 *
 * <pre>{@code
 * goldberry {
 *     weaverVersion = '2026.2'   // the plugin's own version unless set
 *     nativeImage = true         // weave the models into every build
 * }
 * }</pre>
 */
public abstract class GoldberryWeaveExtension {

    /** The block's name in a build script. */
    public static final String NAME = "goldberry";

    /**
     * Which {@code dev.goldberry:goldberry-weaver} runs: the version released
     * with this plugin unless a build names another.
     */
    public abstract Property<String> getWeaverVersion();

    /**
     * Whether every build weaves the models, as a native image needs. Off by
     * default, when a jar binds the same annotations at run time; the property
     * {@code -Pgoldberry.nativeImage=true} turns it on for one build.
     */
    public abstract Property<Boolean> getNativeImage();
}
