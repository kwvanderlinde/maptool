package net.rptools.maptool.client.functions.util;

import net.rptools.maptool.client.functions.Topology_Functions;

public class MacroFunctionProcessor {
	private MacroFunctionContainer container = new MacroFunctionContainer();

	public void process() {
		// TODO Run on all function classes.
		final var type = Topology_Functions.class;

		for (final var method : type.getDeclaredMethods()) {
			final var annotation = method.getAnnotation(MacroFunction.class);
			if (annotation == null) {
				continue;
			}

			// TODO Allow the annotation to override the name.
			final var name = method.getName();

			container.addOverload(method.getName());
		}
	}
}
