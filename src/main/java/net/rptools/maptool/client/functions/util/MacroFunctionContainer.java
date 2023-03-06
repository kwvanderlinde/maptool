package net.rptools.maptool.client.functions.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MacroFunctionContainer {
	private Map<String, OverloadSet> overloadSets = new HashMap<>();

	public void addOverload(String canonicalFunctionName) {
		overloadSets.computeIfAbsent(canonicalFunctionName.toLowerCase(), name -> new OverloadSet(canonicalFunctionName));
	}

	/*
	 * TODO When trying to execute a function, these are the steps I envision:
	 *  1. Look up the macro name to get a set of candidates (overload sets). If no match, error.
	 *  2. Filter overload sets by number of parameters to pick a match. If no match, error.
	 *  3. Do type finagling to distinguish between members of an overload set.
	 *     - This is probably the hardest part. By I say we proceed parameter-by-parameter, checking
	 *       for a match and discording if not.
	 *     - If no matching overload, error.
	 *     - If multiple matching overloads, pick the first.
	 *  4. Invoke the selected overload.
	 */

	public void invoke(String functionName, List<Object> parameters) {
		final var overloadSet = overloadSets.get(functionName.toLowerCase());
		if (overloadSet == null) {

		}
	}
}
