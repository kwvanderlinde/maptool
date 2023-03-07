package net.rptools.maptool.client.functions.util;

import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;

import java.util.List;

public class TransitionalFunction extends AbstractFunction {
    private final int minParameters;
    private final int maxParameters;
    private final AnnotatedMacroFunctionImplementation callback;

    public TransitionalFunction(String name, int minParameters, int maxParameters, AnnotatedMacroFunctionImplementation callback) {
        // TODO Annotation for non-deterministic.
        super(0, -1, true, name);

        this.minParameters = minParameters;
        this.maxParameters = maxParameters;
        this.callback = callback;
    }

    @Override
    public Object childEvaluate(Parser parser, VariableResolver resolver, String functionName, List<Object> parameters) throws ParserException {
        FunctionUtil.checkNumberParam(functionName, parameters, minParameters, maxParameters);

        return this.callback.invoke(parameters);
    }
}
