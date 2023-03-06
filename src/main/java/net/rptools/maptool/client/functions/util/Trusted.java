package net.rptools.maptool.client.functions.util;

import net.rptools.maptool.language.I18N;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Trusted {
    // TODO Some functions are trusted depending on parameters. Add some kind of value that can express that?
    // TODO When checked, fail with ParserException(I18N.getText("macro.function.general.noPerm", functionName));
}
