package annotatedmacros.builders;

import annotatedmacros.GenerationException;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class OverloadBuilder {
    public record OverloadInfo(String methodName) {
    }

    private final ClassName parserType = ClassName.get("net.rptools.parser", "Parser");
    private final String parserVar = "parser";
    private final ClassName variableResolverType = ClassName.get("net.rptools.parser", "VariableResolver");
    private final String variableResolverVar = "resolver";
    private final ClassName parserExceptionType = ClassName.get("net.rptools.parser", "ParserException");
    private final String functionNameVar = "functionName";
    private final String parametersVar = "parameters";
    private final ClassName i18nType = ClassName.get("net.rptools.maptool.language", "I18N");

    private final String macroFunctionName;
    private final List<MethodBuilder> methodBuilder = new ArrayList<>();

    public OverloadBuilder(String macroFunctionName) {
        this.macroFunctionName = macroFunctionName;
    }

    public String macroFunctionName() {
        return macroFunctionName;
    }

    public void addOverload(MethodBuilder methodBuilder) {
        this.methodBuilder.add(methodBuilder);
    }

    public OverloadInfo commit(TypeSpec.Builder classBuilder, String name) {
        // TOOD Make sure two overloads don't have the same number of parameters.

        final var paramCountVar = "parameterCount$";
        int maxParameterCount = -1;
        int minParameterCount = Integer.MAX_VALUE;

        // The dispatch method will pick an overload based on the parameter count.
        var dispatchMethodBuilder = MethodSpec.methodBuilder(name)
                                              .addModifiers(Modifier.PRIVATE)
                                              .returns(Object.class)
                                              .addParameter(parserType, parserVar)
                                              .addParameter(variableResolverType, variableResolverVar)
                                              .addParameter(String.class, functionNameVar)
                                              .addParameter(ParameterizedTypeName.get(List.class, Object.class), parametersVar)
                                              .addException(parserExceptionType);

        dispatchMethodBuilder.addStatement("final var $N = $N.$N()", paramCountVar, "parameters", "size");
        // Java Poet doesn't understand switch expressions.
        dispatchMethodBuilder.addCode("return switch ($N) {\n$>", paramCountVar);
        {
            int index = 0;
            for (final var generator : methodBuilder) {
                var methodName = name + "$" + index++;
                var builder = MethodSpec.methodBuilder(methodName);

                MethodBuilder.MethodInfo methodInfo;
                try {
                    methodInfo = generator.commit(builder);
                }
                catch (GenerationException e) {
                    continue;
                }
                maxParameterCount = Math.max(maxParameterCount, methodInfo.parameterCount());
                minParameterCount = Math.min(minParameterCount, methodInfo.parameterCount());

                // Note: dispatch on number of macro function parameters, not number of Java
                // parameters the implementation accepts.
                dispatchMethodBuilder.addCode("case $L -> $N.$N($N, $N, $N, $N);\n", methodInfo.parameterCount(), "this", methodName, parserVar, variableResolverVar, functionNameVar, parametersVar);

                classBuilder.addMethod(builder.build());
            }

            // Error cases.
            if (minParameterCount == maxParameterCount) {
                // Only need a single case, here: user supplied wrong number of parameters.
                dispatchMethodBuilder.addCode("default -> throw new $T($T.$N($S, $N, $L, $N));\n",
                                              parserExceptionType,
                                              i18nType,
                                              "getText",
                                              "macro.function.general.wrongNumParam",
                                              functionNameVar,
                                              maxParameterCount,
                                              paramCountVar);
            }
            else {
                // We want to show users a different message for too few vs too many parameters.

                // For too few, add a case for each one. There won't be many in practice.
                if (minParameterCount > 0) {
                    final var is = IntStream.range(0, minParameterCount).mapToObj(i -> CodeBlock.of("$L", i));
                    dispatchMethodBuilder.addCode("case $L -> throw new $T($T.$N($S, $N, $L, $N));\n", CodeBlock.join(is::iterator, ", "),
                                                  parserExceptionType,
                                                  i18nType,
                                                  "getText",
                                                  "macro.function.general.notEnoughParam",
                                                  functionNameVar,
                                                  minParameterCount,
                                                  paramCountVar);
                }

                // Anything else is too many.
                dispatchMethodBuilder.addCode("default -> throw new $T($T.$N($S, $N, $L, $N));\n", parserExceptionType, i18nType, "getText", "macro.function.general.tooManyParam", functionNameVar, maxParameterCount, paramCountVar);
            }
        }
        dispatchMethodBuilder.addCode("$<};");

        classBuilder.addMethod(dispatchMethodBuilder.build());

        return new OverloadInfo(name);
    }
}
