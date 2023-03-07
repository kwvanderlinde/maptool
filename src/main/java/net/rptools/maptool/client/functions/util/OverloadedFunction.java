package net.rptools.maptool.client.functions.util;

import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;

import java.util.List;

public class OverloadedFunction extends AbstractFunction {
    private final int minParameters;
    private final AnnotatedMacroFunctionImplementation[] callbacks;

    public OverloadedFunction(String name, int minParameters, AnnotatedMacroFunctionImplementation[] callbacks) {
        // TODO Annotation for non-deterministic.
        super(0, -1, true, name);

        this.minParameters = minParameters;
        this.callbacks = callbacks;
    }

    @Override
    public Object childEvaluate(Parser parser, VariableResolver resolver, String functionName, List<Object> parameters) throws ParserException {
        FunctionUtil.checkNumberParam(functionName, parameters, minParameters, minParameters + callbacks.length);

        // Note: if there are any "holes" in parameter count, we require the @MacroFunction to
        // provide an erroring overload. We're not going to check it here.
        final var callback = callbacks[parameters.size() - minParameters];
        return callback.invoke(parameters);
    }
}
