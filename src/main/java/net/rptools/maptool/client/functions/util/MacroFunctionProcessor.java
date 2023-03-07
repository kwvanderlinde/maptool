package net.rptools.maptool.client.functions.util;

import net.rptools.maptool.client.functions.MapFunctions_New;
import net.rptools.maptool.language.I18N;
import net.rptools.maptool.util.FunctionUtil;
import net.rptools.parser.ParserException;
import net.rptools.parser.function.Function;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
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

		Map<String, List<Method>> overloadSets = new HashMap<>();

		for (final var method : type.getDeclaredMethods()) {
			final var annotation = method.getAnnotation(MacroFunction.class);
			if (annotation == null) {
				continue;
			}

			// TODO Allow the @MacroFunction annotation to override the name.
			final var name = method.getName();
			overloadSets.computeIfAbsent(name, n -> new ArrayList<>()).add(method);
		}

		for (final var entry : overloadSets.entrySet()) {
			final var name = entry.getKey();
			final var methods = entry.getValue();
			final var function = processOverloadSet(name, instance, type, methods);
			add.accept(function);
		}
	}

	private <T> @Nullable Function processOverloadSet(String name, T instance, Class<T> type, List<Method> methods) {
		boolean anyTransitional = methods.stream().anyMatch(method -> method.getAnnotation(Transitional.class) != null);
		if (anyTransitional) {
			if (methods.size() > 1) {
				System.err.println("Macro function marked @Transitional but has overloads. Skipping.");
				return null;
			}

			return processTransitional(name, instance, type, methods.get(0));
		}
		else {
			return processTypedOverload(name, instance, type, methods);
		}
	}

	private <T> @Nullable Function processTransitional(String name, T instance, Class<T> type, Method method) {
		final var trusted = method.getAnnotation(Trusted.class);
		final var transitional = method.getAnnotation(Transitional.class);

		final int minParameters = transitional.minParameters();
		final int maxParameters = transitional.maxParameters();
		final var isTrusted = trusted != null;

		// Note that we lie to the parser about argument counts because it does not use
		// translated error messages.
		return new TransitionalFunction(name, minParameters, maxParameters, parameters -> {
			try {
				if (isTrusted) {
					FunctionUtil.blockUntrustedMacro(name);
				}

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

	private <T> @Nullable Function processTypedOverload(String name, T instance, Class<?> type, List<Method> methods) {
		SortedMap<Integer, AnnotatedMacroFunctionImplementation> callbacksByParameterCount = new TreeMap<>(Integer::compare);
		for (final var method : methods) {
			final int parameterCount = method.getParameterCount();
			// For now we just assume that all parameters are Object. I.e., no conversions.
			if (callbacksByParameterCount.containsKey(parameterCount)) {
				// TODO Invent a system where we care more about types than parameter counts.
				System.err.printf("Overloaded @MacroFunction %s has multiple overloads with %d parameters. I can't deal with this right now.%n", name, parameterCount);
				return null;
			}

			final var isTrusted = method.getAnnotation(Trusted.class) != null;

			// Note that we lie to the parser about argument counts because it does not use
			// translated error messages.
			final AnnotatedMacroFunctionImplementation callback = parameters -> {
				try {
					if (isTrusted) {
						FunctionUtil.blockUntrustedMacro(name);
					}

					return method.invoke(instance, parameters.toArray(Object[]::new));
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
			};

			callbacksByParameterCount.put(parameterCount, callback);
		}

		return new OverloadedFunction(
				name,
				callbacksByParameterCount.firstKey(),
				callbacksByParameterCount.values().toArray(AnnotatedMacroFunctionImplementation[]::new)
		);
	}
}
