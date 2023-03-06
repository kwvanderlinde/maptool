package net.rptools.maptool.client.functions.util;

import net.rptools.parser.Parser;
import net.rptools.parser.ParserException;
import net.rptools.parser.VariableResolver;
import net.rptools.parser.function.ParameterException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OverloadSet implements net.rptools.parser.function.Function {
	private final String canonicalName;
	private Map<Integer, Function> functions = new HashMap<>();

	public OverloadSet(String canonicalName) {
		this.canonicalName = canonicalName;
	}

	@Override
	public String[] getAliases() {
		return new String[0];
	}

	@Override
	public Object evaluate(Parser parser, VariableResolver resolver, String functionName, List<Object> parameters) throws ParserException {
		return null;
	}

	@Override
	public void checkParameters(String functionName, List<Object> parameters) throws ParameterException {

	}

	@Override
	public int getMinimumParameterCount() {
		return 0;
	}

	@Override
	public int getMaximumParameterCount() {
		return 0;
	}

	@Override
	public boolean isDeterministic() {
		return false;
	}
}
