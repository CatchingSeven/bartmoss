package io.github.catchingseven.bartmoss.support;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;

public final class FxTestSupport {
    private FxTestSupport() { }

    public static void startToolkit() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                ready.countDown();
            });
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(ready::countDown);
        }
        if (!ready.await(15, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX toolkit did not start within 15 seconds");
        }
    }

    public static <T> T fx(Callable<T> action) throws Exception {
        if (Platform.isFxApplicationThread()) return action.call();
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        try {
            return task.get(15, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
            // Preserve JUnit assertion failures rather than reporting them as harness errors.
            if (failure.getCause() instanceof Error error) throw error;
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw failure;
        }
    }
}
