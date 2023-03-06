package net.rptools.maptool.client.functions.util;

import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.functions.MapFunctions;
import net.rptools.maptool.client.functions.MapFunctions_New;
import net.rptools.maptool.language.I18N;
import net.rptools.parser.ParserException;
import net.rptools.parser.function.Function;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.function.Consumer;

public class MacroFunctionProcessor {
	private static final List<Class<?>> functionDefiningClasses = List.of(
			MapFunctions_New.class
	);

	public void process(Consumer<Function> add) {
		for (final var type : functionDefiningClasses) {
			final Object instance;
			try {
				instance = type.getDeclaredConstructor().newInstance();
			}
			catch (InstantiationException | NoSuchMethodException | InvocationTargetException |
			       IllegalAccessException e) {
				return;
			}

			// We build a separate Function implementation for each method so that the parser can
			// enforce parameter counts for us... in the future once we have those. Also in the
			// future we will need to support multiple overloads for a given name, all part of the
			// same Function presumably.

			for (final var method : type.getDeclaredMethods()) {
				final var annotation = method.getAnnotation(MacroFunction.class);
				if (annotation == null) {
					continue;
				}

				final var isTrusted = method.getAnnotation(Trusted.class) != null;

				// TODO Allow the annotation to override the name.
				final var name = method.getName();
				// TODO Read from annotations or the method signature.
				final var minParameters = 0;
				final var maxParameters = -1;

				// We build this per-function name so the parser can enforce arguments for us.
				final var function = new AnnotatedFunction(name, minParameters, maxParameters, parameters -> {
					try {
						if (isTrusted && !MapTool.getParser().isMacroTrusted()) {
							throw new ParserException(I18N.getText("macro.function.general.noPerm", name));
						}
						return method.invoke(instance, parameters);
					}
					catch (IllegalAccessException | InvocationTargetException e) {
						throw new ParserException(e);
					}
				});

				add.accept(function);
			}
		}
	}
}
