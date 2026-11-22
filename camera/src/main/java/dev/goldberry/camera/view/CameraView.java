package dev.goldberry.camera.view;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Goldberry;
import dev.goldberry.camera.Camera;
import dev.goldberry.camera.CameraStatus;
import dev.goldberry.i18n.Labels;
import dev.goldberry.i18n.ToolkitLabel;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.media.picture.PictureSource;
import dev.goldberry.media.view.PictureView;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.image.Fit;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;
import dev.goldberry.widgets.text.Text;

/// `camera-view`: a camera's pictures, or a line saying why there are none.
///
/// ```java
/// new CameraView(camera)
/// ```
///
/// ```kdl
/// camera-view camera="front" fit="cover" mirror="auto"
/// ```
///
/// While the camera is active its pictures are drawn as a `picture-view`
/// draws any source's: on the GPU where the window has one, converted on the
/// CPU where it has not. Otherwise the view says what the camera is doing:
/// waiting for the user's permission, refused, stopped, closed, or that there
/// is no camera at all. The view carries its camera's state as a class
/// (`asking`, `active`, `denied`, `lost`, `off`, `none`), and `mirrored` when
/// it mirrors.
///
/// **Mirrored** by [Mirror]: by default unless the camera faces away. The
/// mirror is the view's: the camera's own pictures, which a call sends, are
/// not touched.
///
/// The view does not own the camera, and does not close it.
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
///
/// @param camera     the camera to show, or null for none
/// @param fit        how a picture fills a box of another shape
/// @param mirror     whether the pictures are mirrored
/// @param attributes id, classes and key
@Markup("camera-view")
public record CameraView(@Nullable Camera camera, Fit fit, Mirror mirror, Attributes attributes)
        implements Widget.Stateful, Attributed<CameraView> {

    public CameraView {
        Objects.requireNonNull(fit, "fit");
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(attributes, "attributes");
    }

    /// `camera`, filling the view, mirrored unless it faces away.
    public CameraView(@Nullable Camera camera) {
        this(camera, Fit.COVER, Mirror.AUTO, Attributes.NONE);
    }

    @Override
    public State<?> createState() {
        return new CameraViewState();
    }

    @Override
    public CameraView withAttributes(Attributes attributes) {
        return new CameraView(camera, fit, mirror, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    /// `camera-view camera="name" fit="cover" mirror="off"`: the camera is a
    /// named object the application registered with the document. A name
    /// nothing is bound to shows "No camera".
    ///
    /// @throws IllegalArgumentException when the node has children, `fit` is
    ///         not one of the four, or `mirror` not one of the three
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("camera-view takes no children");
        }
        var fit = node.stringProperty("fit");
        return new CameraView(
                wiring.handle(node, "camera", Camera.class),
                fit == null ? Fit.COVER : Fit.named(fit),
                Mirror.named(node.stringProperty("mirror")),
                Attributes.of(node));
    }

    /// The class a view carries for `status`, or `none` with no camera.
    static String stateClass(@Nullable CameraStatus status) {
        return switch (status) {
            case null -> "none";
            case CameraStatus.Asking _ -> "asking";
            case CameraStatus.Active _ -> "active";
            case CameraStatus.Denied _ -> "denied";
            case CameraStatus.Lost _ -> "lost";
            case CameraStatus.Closed _ -> "off";
        };
    }

    /// What a view says for `status` instead of a picture.
    static ToolkitLabel message(@Nullable CameraStatus status) {
        return switch (status) {
            case null -> ToolkitLabel.CAMERA_NONE;
            case CameraStatus.Asking _ -> ToolkitLabel.CAMERA_ASKING;
            case CameraStatus.Active _ -> ToolkitLabel.CAMERA;
            case CameraStatus.Denied _ -> ToolkitLabel.CAMERA_DENIED;
            case CameraStatus.Lost _ -> ToolkitLabel.CAMERA_LOST;
            case CameraStatus.Closed _ -> ToolkitLabel.CAMERA_OFF;
        };
    }

    /// Follows the camera's status, and builds the picture or the message.
    static final class CameraViewState extends State<CameraView> {

        private final AtomicBoolean pending = new AtomicBoolean();
        private @Nullable AutoCloseable subscription;
        private @Nullable PictureSource mirrored;

        @Override
        protected void initState() {
            listen(widget().camera());
        }

        @Override
        protected void didUpdateWidget(CameraView previous) {
            if (previous.camera() != widget().camera()) {
                unlisten();
                mirrored = null;
                listen(widget().camera());
            }
        }

        @Override
        public Widget build(BuildContext context) {
            var view = widget();
            var camera = view.camera();
            var status = camera == null ? null : camera.status();
            var classes = new LinkedHashSet<>(view.attributes().classes());
            classes.add(stateClass(status));
            Widget content;
            if (camera != null && status instanceof CameraStatus.Active) {
                PictureSource source = camera;
                if (view.mirror().mirrors(camera.info())) {
                    classes.add("mirrored");
                    if (mirrored == null) {
                        mirrored = new MirroredPictures(camera);
                    }
                    source = mirrored;
                }
                content = new PictureView(source, view.fit());
            } else {
                content = new Text(Labels.text(message(status)), Attributes.NONE.classes("camera-message"));
            }
            return new CameraBox(content, view.attributes().id(), classes);
        }

        private void listen(@Nullable Camera camera) {
            if (camera != null) {
                subscription = camera.onStatus(_ -> {
                    if (pending.compareAndSet(false, true)) {
                        Goldberry.ui().execute(() -> {
                            pending.set(false);
                            if (isMounted()) {
                                setState(() -> {});
                            }
                        });
                    }
                });
            }
        }

        private void unlisten() {
            var current = subscription;
            subscription = null;
            if (current != null) {
                try {
                    current.close();
                } catch (Exception e) {
                    throw new IllegalStateException("the camera refused to let go of a listener", e);
                }
            }
        }

        @Override
        protected void dispose() {
            unlisten();
            super.dispose();
        }
    }
}
