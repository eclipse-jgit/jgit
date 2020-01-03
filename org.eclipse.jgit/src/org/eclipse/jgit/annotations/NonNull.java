/*
 * Copyright (C) 2015, Andrey Loskutov <loskutov@gmx.de> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

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
 * JGit's replacement for the {@code javax.annotation.Nonnull}.
 * <p>
 * Denotes that a local variable, parameter, field, method return value is expected
 * to be non {@code null}.
 *
 * @since 4.2
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ FIELD, METHOD, PARAMETER, LOCAL_VARIABLE })
public @interface NonNull {
	// marker annotation with no members
}
