package annotatedmacros.builders;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class RootBuilder {
    private final Filer filer;
    private final Messager messager;
    private final Map<String, ClassBuilder> classBuilders = new LinkedHashMap<>();

    public RootBuilder(Filer filer, Messager messager) {
        this.filer = filer;
        this.messager = messager;
    }

    public void addOverload(ClassName class_, String macroFunctionName, MethodBuilder methodBuilder) {
        classBuilders.computeIfAbsent(class_.toString(), class2 -> new ClassBuilder(class_)).addOverload(macroFunctionName, methodBuilder);
    }

    public void commit() {
        for (final var classBuilder : classBuilders.values()) {
            JavaFile javaFile = JavaFile.builder(classBuilder.generatedClass().packageName(), classBuilder.commit()).build();
            try {
                javaFile.writeTo(System.out);
                javaFile.writeTo(filer);
            }
            catch (IOException e) {
                messager.printMessage(Diagnostic.Kind.ERROR, String.format("Unexpected error writing class %s: %s", classBuilder.generatedClass().toString(), e));
            }
        }
    }
}
