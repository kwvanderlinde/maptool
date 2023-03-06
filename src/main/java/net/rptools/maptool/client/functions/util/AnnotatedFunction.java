package net.rptools.maptool.client.functions.util;

import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;

import java.util.List;

public class AnnotatedFunction extends AbstractFunction {
    public interface Callback {
        Object invoke(List<Object> parameters) throws ParserException;
    }

private final Callback callback;

    public AnnotatedFunction(String name, int minParameters, int maxParameters, Callback callback) {
        // TODO Annotation for non-deterministic.
        super(minParameters, maxParameters, true, name);
        this.callback = callback;
    }

    @Override
    public Object childEvaluate(Parser parser, VariableResolver resolver, String functionName, List<Object> parameters) throws ParserException {
        return this.callback.invoke(parameters);
    }
}
