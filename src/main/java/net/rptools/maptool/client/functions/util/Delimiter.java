package net.rptools.maptool.client.functions.util;

import javax.annotation.Nullable;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Delimiter {
    // TODO Can we attach any real functionality to this? Yes, the method should return a List or JsonArray, we the generated code will keep as json or change to string list.
}
