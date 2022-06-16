package annotatedmacros;

import annotatedmacros.annotations.MacroFunction;
import annotatedmacros.annotations.Trusted;
import annotatedmacros.builders.MethodBuilder;
import annotatedmacros.builders.RootBuilder;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;

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
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.Set;

@SupportedAnnotationTypes("annotatedmacros.annotations.MacroFunction")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
// TODO I keep flip-flopping on this, but I think there is value in supporting some kind of @Default
//  parameter. It's not that I'm against overloads, but one of my goals is to hopefully even
//  generate documentation based on the doc strings. Having overloads complicates that, and it's not
//  clear that we need it in the majority of cases. Let's keep in mind that most of our
//  "overloading" just boils down to checking parameter counts, and mostly to support default
//  values.
public class MacroFunctionAnnotationProcessor extends AbstractProcessor {
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
        // TODO
        //  warning: File for type 'net.rptools.maptool.client.functions.MapFunctions_New$GENERATED$' created in the last round will not be subject to annotation processing.
        //  Now that I understand rounds better, I can just emit immediately. It's not going to be the case that I partially process a class because we aren't allowed to overwrite classes.

        var builder = new RootBuilder(filer, messager);

        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(MacroFunction.class);
        for (final Element element : annotatedElements) {
            final var annotation = element.getAnnotation(MacroFunction.class);

            if (element.getKind() != ElementKind.METHOD) {
                messager.printMessage(Diagnostic.Kind.ERROR, String.format("@%s annotation must be applied to methods", MacroFunction.class), element);
                continue;
            }
            final var executableElement = (ExecutableElement) element;

            // Enclosing element should always be a class, right? Right?
            final var enclosingElement = element.getEnclosingElement();
            if (enclosingElement.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Enclosing element is somehow not a class!", element);
                continue;
            }
            if (!(enclosingElement instanceof TypeElement sourceClass)) {
                messager.printMessage(Diagnostic.Kind.ERROR, String.format("Enclosing element is a class but not a TypeElement! Actually get %s", enclosingElement.getClass()), element);
                continue;
            }

            final var macroFunctionName = "".equals(annotation.name()) ? element.getSimpleName().toString() : annotation.name();
            final var trustAnnotation = element.getAnnotation(Trusted.class);
            final var trusted = trustAnnotation != null && trustAnnotation.value();
            try {
                builder.addOverload(
                        ClassName.get(sourceClass),
                        macroFunctionName,
                        new MethodBuilder(typeUtils, elementUtils, messager, executableElement, trusted)
                );
            }
            catch (Exception e) {
                messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), element);
            }
        }

        builder.commit();

        return true;
    }
}
