package annotatedmacros.builders;

import annotatedmacros.GeneratedFrom;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClassBuilder {
    private final ClassName parserType = ClassName.get("net.rptools.parser", "Parser");
    private final String parserVar = "parser";
    private final ClassName variableResolverType = ClassName.get("net.rptools.parser", "VariableResolver");
    private final String variableResolverVar = "resolver";
    private final ClassName parserExceptionType = ClassName.get("net.rptools.parser", "ParserException");
    private final String functionNameVar = "functionName";
    private final String parametersVar = "parameters";
    private final ClassName i18nType = ClassName.get("net.rptools.maptool.language", "I18N");

    private final ClassName originalType;  // TODO originalClass.
    private final ClassName selfType; // TODO generatedClass.
    private final Map<String, OverloadBuilder> overloads = new LinkedHashMap<>();

    public ClassBuilder(ClassName originalType) {
        this.originalType = originalType;
        // TODO Consider this.originalType.peerClass();
        this.selfType = ClassName.get(
                this.originalType.packageName() + ".__generated__",
                this.originalType.simpleName() + "$GENERATED$");
    }

    public ClassName generatedClass() {
        return this.selfType;
    }

    public void addOverload(String macroFunctionName, MethodBuilder methodBuilder) {
        overloads.computeIfAbsent(macroFunctionName, name -> new OverloadBuilder(macroFunctionName))
                .addOverload(methodBuilder);
    }

    public TypeSpec commit() {
        final var classBuilder = TypeSpec.classBuilder(selfType.simpleName())
                                         .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        classBuilder.addAnnotation(AnnotationSpec.builder(GeneratedFrom.class)
                                                 .addMember("value", "$T.class", originalType)
                                                 .build());
        classBuilder.superclass(ClassName.get("net.rptools.parser.function", "AbstractFunction"));
        classBuilder.addField(
                FieldSpec.builder(originalType, "original", Modifier.PRIVATE, Modifier.FINAL)
                         .initializer("new $T()", originalType)
                         .build());

        List<CodeBlock> allFunctionNames = new ArrayList<>();
        var dispatchMethodBuilder = MethodSpec.methodBuilder("childEvaluate")
                                              .addAnnotation(Override.class)
                                              .addModifiers(Modifier.PUBLIC)
                                              .returns(Object.class)
                                              .addParameter(parserType, "parser")
                                              .addParameter(variableResolverType, variableResolverVar)
                                              .addParameter(String.class, functionNameVar)
                                              .addParameter(ParameterizedTypeName.get(List.class, Object.class), "parameters")
                                              .addException(parserExceptionType);

        // Java Poet doesn't understand switch expressions.
        dispatchMethodBuilder.addCode("return switch (functionName) {\n$>");
        {
            int index = 0;
            for (final var builder : overloads.values()) {
                allFunctionNames.add(CodeBlock.of("$S", builder.macroFunctionName()));
                var overloadInfo = builder.commit(classBuilder, "function$" + index++);
                dispatchMethodBuilder.addCode("case $S -> $N.$N($N, $N, $N, $N);\n", builder.macroFunctionName(), "this", overloadInfo.methodName(), parserVar, variableResolverVar, functionNameVar, parametersVar);
            }

            dispatchMethodBuilder.addCode("default -> throw new $T($T.$N($S, $N));\n", parserExceptionType, i18nType, "getText", "macro.function.general.unknownFunction", "functionName");
        }
        dispatchMethodBuilder.addCode("$<};");
        classBuilder.addMethod(dispatchMethodBuilder.build());

        // TODO Generate a constructor with the actual maximum number of arguments.
        classBuilder.addMethod(MethodSpec.constructorBuilder()
                                         .addModifiers(Modifier.PRIVATE)
                                         .addStatement("super(0, -1, $L)", CodeBlock.join(allFunctionNames, ", "))
                                         .build());

        return classBuilder.build();
    }
}
