package ap.interceptors;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

public class Interceptor {
    @RuntimeType
    public static Object intercept(@Origin Method method,
                                   @SuperCall Callable<?> callable) throws Exception {
        final var threadName = Thread.currentThread().getName();
        if (!threadName.startsWith("AWT-EventQueue")) {
            // Yes, this is noisy. How observant.
            new Exception(String.format("Call to method %s must be made on the EDT thread, but this one is on thread %s", method, threadName)).printStackTrace(System.err);
        }

        return callable.call();
    }
}
