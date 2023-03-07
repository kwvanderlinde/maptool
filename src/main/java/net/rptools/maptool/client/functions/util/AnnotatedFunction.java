package net.rptools.maptool.client.functions.util;

import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.AbstractFunction;

import java.util.List;

public class AnnotatedFunction extends AbstractFunction {
    public interface Callback {
        Object invoke(List<Object> parameters) throws ParserException;
    }

    private final int minParameters;
    private final int maxParameters;
    private final boolean isTrusted;
    private final Callback callback;

    public AnnotatedFunction(String name, int minParameters, int maxParameters, boolean isTrusted, Callback callback) {
        // TODO Annotation for non-deterministic.
        // We lie to the parser about the parameter counts because it does not give translated
        // errors. Instead, we'll check it ourselves.
        super(0, -1, true, name);
        this.minParameters = minParameters;
        this.maxParameters = maxParameters;
        this.isTrusted = isTrusted;
        this.callback = callback;
    }

    @Override
    public Object childEvaluate(Parser parser, VariableResolver resolver, String functionName, List<Object> parameters) throws ParserException {
        FunctionUtil.checkNumberParam(functionName, parameters, minParameters, maxParameters);
        if (isTrusted) {
            FunctionUtil.blockUntrustedMacro(functionName);
        }

        return this.callback.invoke(parameters);
    }
}
