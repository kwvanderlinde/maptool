package net.rptools.threadcheck;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.instrument.Instrumentation;

public class ThreadSafetyAgent {
    public static void premain(String arguments, Instrumentation instrumentation) {
        new AgentBuilder.Default()
                .type(ElementMatchers.isAnnotatedWith(RequiresThread.class).or(ElementMatchers.nameStartsWith("net.rptools.maptool.model").and(ElementMatchers.not(ElementMatchers.nameContains(".proto.")))))
                .transform((builder, type, classLoader, module) ->
                                   builder.method(ElementMatchers.any())
                                          .intercept(MethodDelegation.to(Interceptor.class)))
                .installOn(instrumentation);
    }
}
