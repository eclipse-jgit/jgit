/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation to document a {@link org.eclipse.jgit.pgm.TextBuiltin}.
 * <p>
 * This is an optional annotation for TextBuiltin subclasses and it carries
 * documentation forward into the runtime system describing what the command is
 * and why users may want to invoke it.
 */
@Retention(RUNTIME)
@Target( { TYPE })
public @interface Command {
	/**
	 * Get the command name
	 *
	 * @return name the command is invoked as from the command line. If the
	 *         (default) empty string is supplied the name will be generated
	 *         from the class name.
	 */
	public String name() default "";

	/**
	 * Get command description
	 *
	 * @return one line description of the command's feature set.
	 */
	public String usage() default "";

	/**
	 * If this command is considered to be commonly used
	 *
	 * @return true if this command is considered to be commonly used.
	 */
	public boolean common() default false;
}
