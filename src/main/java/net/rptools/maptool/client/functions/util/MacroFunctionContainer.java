package net.rptools.maptool.client.functions.util;

import java.util.HashMap;
import java.util.Map;

public class MacroFunctionContainer {
	private Map<String, OverloadSet> overloadSets = new HashMap<>();

	public void addOverload(String functionName) {
		overloadSets.computeIfAbsent(functionName, name -> new OverloadSet());
	}
}
