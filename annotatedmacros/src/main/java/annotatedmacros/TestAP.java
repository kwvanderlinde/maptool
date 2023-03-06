package annotatedmacros;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Set;

@SupportedAnnotationTypes("net.rptools.maptool.client.functions.util.MacroFunction")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class TestAP extends AbstractProcessor {
    private Types typeUtils;
    private Elements elementUtils;
    private Filer filer;
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        typeUtils = processingEnv.getTypeUtils();
        elementUtils = processingEnv.getElementUtils();
        filer = processingEnv.getFiler();
        messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.printf("Processing %s annotations%n", annotations.size());
        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            System.out.printf("Processing %s elements%n", annotatedElements.size());
            for (final Element element : annotatedElements) {
                if (element.getKind() != ElementKind.METHOD) {
                    messager.printMessage(Diagnostic.Kind.ERROR, String.format("@%s annotation must be applied to classes", Test.class), element);
                    continue;
                }

                final var executable = (ExecutableType) element.asType();

                try {
                    generateCode(element, executable);
                }
                catch (IOException e) {
                    messager.printMessage(Diagnostic.Kind.ERROR, String.format("Unexpected error writing class %s: %s", Test.class, e), element);
                    continue;
                }
//
//                // Our goal is to inject a threading check at the start of each method.
//                for (final var enclosed : element.getEnclosedElements()) {
//                    if (enclosed.getKind() != ElementKind.METHOD) {
//                        // We only want to modify methods.
//                        continue;
//                    }
//
//                    messager.printMessage(Diagnostic.Kind.NOTE, String.format("Found method %s in type %s", enclosed, element));
//                }
            }
        }

        return true;
    }

    private void generateCode(Element element, ExecutableType methodType) throws IOException {
        // TODO We actually want to collect all definitions into a centralized class.
        final var newTypeName = "AllMacroFunctions";

        TypeSpec macroFunctionContainer = TypeSpec.classBuilder(newTypeName)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                                 .build();

        JavaFile javaFile = JavaFile.builder("net.rptools.maptool.client.functions", macroFunctionContainer).build();
        //javaFile.writeTo(filer);
        javaFile.writeTo(System.out);
    }
}
