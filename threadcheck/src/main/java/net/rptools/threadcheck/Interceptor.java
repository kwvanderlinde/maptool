package net.rptools.threadcheck;

import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

public class Interceptor {
    @RuntimeType
    public static Object intercept(@Origin Method method,
                                   @SuperCall Callable<?> callable) throws Exception {
        final var clazz = method.getDeclaringClass();
        final var annotation = clazz.getAnnotation(RequiresThread.class);
        // TODO I actually want this to be a regex I think.
        final var requiredThreadName = annotation == null ? "AWT-EventQueue" : annotation.value();
        final var threadName = Thread.currentThread().getName();
        if (!threadName.startsWith(requiredThreadName)) {
            // Yes, this is noisy. How observant.
            new Exception(String.format("Call to method %s.%s() must be made on the %s thread, but this one is on thread %s", clazz.getCanonicalName(), method.getName(), requiredThreadName, threadName)).printStackTrace(System.err);
        }

        return callable.call();
    }
}
