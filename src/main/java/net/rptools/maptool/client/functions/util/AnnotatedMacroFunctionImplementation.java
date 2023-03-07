package net.rptools.maptool.client.functions.util;

import net.rptools.parser.ParserException;

import java.util.List;

public interface AnnotatedMacroFunctionImplementation {
    Object invoke(List<Object> parameters) throws ParserException;
}
