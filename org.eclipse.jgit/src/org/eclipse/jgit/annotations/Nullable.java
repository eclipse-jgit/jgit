package org.eclipse.jgit.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.LOCAL_VARIABLE;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * JGit's replacement for the {@code javax.annotations.Nullable}.
 * <p>
 * Denotes that a local variable, parameter, field, method return value can be
 * {@code null}.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ FIELD, METHOD, PARAMETER, LOCAL_VARIABLE })
public @interface Nullable {
	// marker annotation with no members
}