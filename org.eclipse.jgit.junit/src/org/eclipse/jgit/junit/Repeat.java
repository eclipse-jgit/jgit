/*
 * Copyright (C) 2016, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.junit;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation enabling to run tests repeatedly
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ java.lang.annotation.ElementType.METHOD })
public @interface Repeat {
	/**
	 * Number of repetitions
	 */
	public abstract int n();

	/**
	 * Whether to abort execution on first test failure
	 *
	 * @return {@code true} if execution should be aborted on the first failure,
	 *         otherwise count failures and continue execution
	 * @since 5.1.9
	 */
	public boolean abortOnFailure() default true;
}
