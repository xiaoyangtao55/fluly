package eightbitlab.com.blurview;

/** Installs Fluly's manually scheduled controller without the library's eager capture. */
public final class FlulyBlurControllerAccess {
    private FlulyBlurControllerAccess() {}

    public static void install(BlurView view, BlurController controller) {
        view.blurController.destroy();
        view.blurController = controller;
        view.setWillNotDraw(false);
        view.setBlurAutoUpdate(false);
    }

    public static void clear(BlurView view) {
        view.blurController.destroy();
        view.blurController = new NoOpController();
    }
}
