package dev.goldberry.widgets.core.image.style;

import java.util.concurrent.CompletableFuture;

import dev.goldberry.css.image.StyleImages;
import dev.goldberry.image.Image;
import dev.goldberry.image.ImageAddress;
import dev.goldberry.widgets.core.image.ImageLoader;
import dev.goldberry.widgets.core.image.ImageSource;
import dev.goldberry.widgets.core.image.ImageView;

/// The pictures a stylesheet names, read through the `image` widget's shared
/// loader: one bounded cache for the process, so a `url()` in a stylesheet and
/// an `image` showing the same file share one decode, and a region of a sheet
/// is cut from the sheet's.
///
/// An address is read as an `image`'s `src` is: `classpath:` on the
/// application's class loader, a file otherwise.
///
/// The provider the module announces; nothing names it.
public final class SharedStyleImages implements StyleImages {

    /// What the service loader calls.
    public SharedStyleImages() {}

    @Override
    public CompletableFuture<Image> load(ImageAddress address) {
        var loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ImageView.class.getClassLoader();
        }
        return ImageLoader.shared().load(ImageSource.parse(address.toString(), loader));
    }
}
