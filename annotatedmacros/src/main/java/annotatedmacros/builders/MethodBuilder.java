package annotatedmacros.builders;

import annotatedmacros.GenerationException;
import annotatedmacros.annotations.FunctionName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;

import javax.annotation.Nullable;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MethodBuilder {
    public record MethodInfo(int parameterCount) {
    }

    private final ClassName parserType = ClassName.get("net.rptools.parser", "Parser");
    private final String parserVar = "parser";
    private final ClassName variableResolverType = ClassName.get("net.rptools.parser", "VariableResolver");
    private final String variableResolverVar = "resolver";
    private final ClassName parserExceptionType = ClassName.get("net.rptools.parser", "ParserException");
    private final String functionNameVar = "functionName";
    private final String parametersVar = "parameters";
    private final ClassName i18nType = ClassName.get("net.rptools.maptool.language", "I18N");
    private final ClassName generatedSupportType = ClassName.get("net.rptools.maptool.client.functions", "GeneratedSupport");
    private final ClassName functionUtilType = ClassName.get("net.rptools.maptool.util", "FunctionUtil");

    private final Types typeUtils;
    private final Elements elementUtils;
    private final Messager messager;

    private final ExecutableElement original;
    private final TypeElement sourceClass;
    private final boolean isTrusted;

    public MethodBuilder(Types typeUtils, Elements elementUtils, Messager messager, ExecutableElement original, boolean isTrusted) {
        this.typeUtils = typeUtils;
        this.elementUtils = elementUtils;
        this.messager = messager;

        this.original = original;

        // Enclosing element should always be a class, right? Right?
        final var enclosingElement = this.original.getEnclosingElement();
        if (enclosingElement.getKind() != ElementKind.CLASS) {
            throw new RuntimeException("Enclosing element is somehow not a class!");
        }
        if (!(enclosingElement instanceof TypeElement sourceClass)) {
            throw new RuntimeException(String.format("Enclosing element is a class but not a TypeElement! Actually get %s", enclosingElement.getClass()));
        }
        this.sourceClass = sourceClass;

        this.isTrusted = isTrusted;
    }

    public MethodInfo commit(MethodSpec.Builder methodBuilder) {
        final var parameters = original.getParameters();

        final var executable = (ExecutableType) original.asType();
        final var variableResolverVar = "resolver";
        final var functionNameVar = "functionName";

        methodBuilder
                .addModifiers(Modifier.PRIVATE)
                .returns(Object.class)
                .addParameter(parserType, "parser")
                .addParameter(variableResolverType, variableResolverVar)
                .addParameter(String.class, "functionName")
                .addParameter(ParameterizedTypeName.get(List.class, Object.class), "parameters")
                .addException(parserExceptionType);

        if (isTrusted) {
            methodBuilder.addStatement("$T.$N($N)", functionUtilType, "blockUntrustedMacro", "functionName");
        }

        final var delegationBuilder = CodeBlock.builder();
        List<CodeBlock> parameterList = new ArrayList<>();

        var nextParameterIndex = 0;
        for (final var parameter : parameters) {
            final var parameterType = parameter.asType();
            if (typeUtils.isSameType(parameterType, elementUtils.getTypeElement("net.rptools.parser.VariableResolver").asType())) {
                parameterList.add(CodeBlock.of("$N", variableResolverVar));
                continue;
            }
            if (typeUtils.isSameType(parameterType, elementUtils.getTypeElement("net.rptools.parser.Parser").asType())) {
                parameterList.add(CodeBlock.of("$N", parserVar));
                continue;
            }
            if (parameter.getAnnotation(FunctionName.class) != null) {
                if (!typeUtils.isSameType(parameterType, elementUtils.getTypeElement(String.class.getCanonicalName()).asType())) {
                    throw new GenerationException("Parameter annotated with @FunctionName must be a String", original);
                }

                parameterList.add(CodeBlock.of("$N", functionNameVar));
                continue;
            }

            final var parameterIndex = nextParameterIndex++;
            final var varName = "param$" + parameterIndex;
            // TODO Enable contextualized converters.
            final var converterName = getConverterName(parameterType, original);

            if (converterName == null) {
                // No conversion need, so save the field lookup and method call.
                delegationBuilder.addStatement("final var $N = parameters.get($L)", varName, parameterIndex);
            }
            else {
                delegationBuilder.addStatement("final var $N = $T.$N.fromObject($N, parameters.get($L))", varName, generatedSupportType, converterName, functionNameVar, parameterIndex);
            }
            parameterList.add(CodeBlock.of("$N", varName));
        }

        delegationBuilder.add("\n");

        final var call =
                original.getModifiers().contains(Modifier.STATIC)
                        ? CodeBlock.of("$T.$L($L)", ClassName.get(sourceClass), original.getSimpleName(), CodeBlock.join(parameterList, ", "))
                        : CodeBlock.of("$N.$N.$L($L)", "this", "original", original.getSimpleName(), CodeBlock.join(parameterList, ", "));

        final var returnType = executable.getReturnType();
        if (returnType.getKind() == TypeKind.VOID) {
            delegationBuilder.addStatement(call);

            delegationBuilder.add("\n");
            delegationBuilder.addStatement("return $S", "");
        }
        else {
            final var resultVar = "result$";
            delegationBuilder.addStatement("final var $N = $L", resultVar, call);

            delegationBuilder.add("\n");
            final var converterName = getConverterName(returnType, original);
            if (converterName == null) {
                delegationBuilder.addStatement("return $N", resultVar);
            }
            else {
                delegationBuilder.addStatement("return $T.$N.toObject($N, $N)", generatedSupportType, converterName, functionNameVar, resultVar);
            }
        }

        // TODO I just pass the parameter list for now, but will need to optionally pass Parser and VariableResolver.
        // TODO Convert the return value into Object based on whether it is a boolean, Number, etc.
        // TODO Also do per-parameter conversions.
        methodBuilder.addCode(delegationBuilder.build());

        return new MethodInfo(nextParameterIndex);
    }

    private @Nullable String getConverterName(TypeMirror type, Element element) {
        // Note: we deliberately only support boolean as a primitive type. For numbers, we expect to
        // use BigDecimal (not that all use it currently). For char, macro functions have no such
        // concept, instead using string.
        // TODO Kind.DECLARED cases: convert to String, List<Object> (maybe List<String>, but there's cost).
        if (type.getKind() == TypeKind.BOOLEAN) {
            return "booleanConverter";
        }
        else if (type.getKind() == TypeKind.DECLARED) {
            if (typeUtils.isSameType(type, elementUtils.getTypeElement(String.class.getCanonicalName()).asType())) {
                return "stringConverter";
            }
            if (typeUtils.isSameType(type, elementUtils.getTypeElement(BigDecimal.class.getCanonicalName()).asType())) {
                return "bigDecimalConverter";
            }
            if (typeUtils.isSameType(type, elementUtils.getTypeElement("net.rptools.maptool.model.Zone").asType())) {
                return "zoneConverter";
            }
            if (typeUtils.isSameType(type, elementUtils.getTypeElement("net.rptools.maptool.model.Token").asType())) {
                return "tokenConverter";
            }
            if (typeUtils.isSameType(type, elementUtils.getTypeElement(Object.class.getCanonicalName()).asType())) {
                // Objects need no conversion.
                return null;
            }
        }

        messager.printMessage(Diagnostic.Kind.ERROR, String.format("Found an unexpected type %s", type), element);
        return null;
    }
}
