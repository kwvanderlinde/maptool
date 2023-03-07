package net.rptools.maptool.client.functions.util;

public class AnnotatedFunctionException extends Exception {
    private final String key;
    private final Object[] parameters;

    public AnnotatedFunctionException(String key, Object ...parameters) {
        this.key = key;
        this.parameters = parameters;
    }

    public String getKey() {
        return key;
    }

    public Object[] getParameters() {
        return parameters;
    }
}
