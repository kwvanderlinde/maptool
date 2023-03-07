package net.rptools.maptool.client.functions.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonPrimitive;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.functions.MapFunctions_New;
import net.rptools.maptool.client.functions.StringFunctions;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.ParserException;
import net.rptools.parser.function.Function;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class MacroFunctionProcessor {
	private static final List<Class<?>> functionDefiningClasses = List.of(
			MapFunctions_New.class
	);

	public void process(Consumer<Function> add) {
		for (final var type : functionDefiningClasses) {
			processType(type, add);
		}
	}

	private <T> void processType(Class<T> type, Consumer<Function> add) {
		final T instance;
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

			final var trusted = method.getAnnotation(Trusted.class);
			final var transitional = method.getAnnotation(Transitional.class);
			final var delimited = method.getAnnotation(Delimited.class);

			Function function = null;
			if (transitional != null) {
				if (delimited == null) {
					System.err.println("@Delimited cannot be used with @Transitional functions");
					continue;
				}

				function = processTransitional(instance, type, method, trusted, transitional);
			}
			else {
				function = processTyped(instance, type, method, trusted);
			}


			add.accept(function);
		}
	}

	private <T> @Nullable Function processTransitional(T instance, Class<T> type, Method method, Trusted trusted, Transitional transitional) {
		// TODO Allow the annotation to override the name.
		final var name = method.getName();
		final int minParameters = transitional.minParameters();
		final int maxParameters = transitional.maxParameters();
		final var isTrusted = trusted != null;

		// Note that we lie to the parser about argument counts because it does not use
		// translated error messages.
		return new AnnotatedFunction(name, minParameters, maxParameters, isTrusted, parameters -> {
			try {
				return method.invoke(instance, parameters);
			}
			catch (IllegalAccessException e) {
				throw new ParserException(e);
			}
			catch (InvocationTargetException e) {
				if (e.getTargetException() instanceof AnnotatedFunctionException afe) {
					throw new ParserException(I18N.getText(afe.getKey(), name, afe.getParameters()));
				}
				if (e.getTargetException() instanceof ParserException pe) {
					throw pe;
				}
				throw new ParserException(e);
			}
		});
	}

	private <T> @Nullable Function processTyped(T instance, Class<?> type, Method method, Trusted trusted) {
		// TODO Support more than nullary functions.
		// TODO Allow the annotation to override the name.
		final var name = method.getName();
		final int minParameters = 0;
		final int maxParameters = 0;
		final var isTrusted = trusted != null;

		final var delimited = method.getAnnotation(Delimited.class);

		BiFunction<Object, List<Object>, Object> resultTransformation;
		if (delimited != null) {
			// TODO Allow Iterable? Collection?
			var isValid = false;
			if (method.getGenericReturnType() instanceof ParameterizedType returnType) {
				final var containerType = returnType.getRawType();
				final var typeArgs = returnType.getActualTypeArguments();
				isValid = List.class.equals(containerType)
						&& typeArgs.length == 1 && String.class.equals(typeArgs[0]);
			}

			if (!isValid) {
				System.err.printf("Found @Delimited method %s, but the return type is not a list. Skipping this function", name);
				return null;
			}
			final var index = delimited.parameterIndex();
			final var defaultDelim = delimited.ifMissing();

			resultTransformation = (list, parameters) -> {
				// TODO Can we enforce that this is List<String> somehow?
				final var strings = (List<String>) list;
				// TODO This lookup will need to be robust when we have overloads.
				final var delim = (parameters.size() > index ? parameters.get(index) : defaultDelim).toString();
				if ("json".equals(delim)) {
					JsonArray jarr = new JsonArray();
					strings.forEach(m -> jarr.add(new JsonPrimitive(m)));
					return jarr;
				}
				else {
					return StringFunctions.getInstance().join(strings, delim);
				}
			};
		}
		else {
			resultTransformation = (result, parameters) -> result;
		}

		// Note that we lie to the parser about argument counts because it does not use
		// translated error messages.
		return new AnnotatedFunction(name, minParameters, maxParameters, isTrusted, parameters -> {
			try {
				return resultTransformation.apply(method.invoke(instance, parameters), parameters);
			}
			catch (IllegalAccessException e) {
				throw new ParserException(e);
			}
			catch (InvocationTargetException e) {
				if (e.getTargetException() instanceof AnnotatedFunctionException afe) {
					throw new ParserException(I18N.getText(afe.getKey(), name, afe.getParameters()));
				}
				if (e.getTargetException() instanceof ParserException pe) {
					throw pe;
				}
				throw new ParserException(e);
			}
		});

	}
}
