/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.util;

import java.util.Collection;
import java.util.Map;

/**
 * Helper class for parameters validation, such as not null arguments.
 */
public class Preconditions {

	/**
	 * Should not be instantiated
	 */
	protected Preconditions() {
		throw new IllegalStateException("This class should be not instantiated!");
	}

	/**
	 * Assert that this parameter is marked as valid by the condition passed as
	 * parameter.
	 * 
	 * @param name      of parameter
	 * @param condition itself
	 */
	public static void checkCondition(final String name, final boolean condition) {
		if (!condition) {
			throw new IllegalStateException("Condition '" + name + "' is invalid!");
		}
	}

	/**
	 * Assert that this parameter is not null, as also each item of the array is not
	 * null.
	 * 
	 * @param <T>        parameter type
	 * @param name       of parameter
	 * @param parameters itself
	 */
	public static <T> void checkEachParameterNotNull(final String name, final T... parameters) {
		if (parameters == null) {
			throw new IllegalArgumentException("Parameter named '" + name + "' should be not null!");
		}
		for (final Object parameter : parameters) {
			if (parameter == null) {
				throw new IllegalArgumentException("Parameter named '" + name + "' should be not null!");
			}
		}
	}

	/**
	 * Assert that this parameter is not empty. It will test for null and also the
	 * size of this array.
	 * 
	 * @param name      of parameter
	 * @param parameter itself
	 */
	public static <T extends Collection<?>> T checkNotEmpty(final String name, final T parameter) {
		if (parameter == null || parameter.size() == 0) {
			throw new IllegalArgumentException("Parameter named '" + name + "' should be filled!");
		}
		return parameter;
	}

	/**
	 * Assert that this parameter is not empty. It will test for null and also the
	 * size of this array.
	 * 
	 * @param name      of parameter
	 * @param parameter itself
	 */
	public static void checkNotEmpty(final String name, final Map<?, ?> parameter) {
		if (parameter == null || parameter.size() == 0) {
			throw new IllegalArgumentException("Parameter named '" + name + "' should be filled!");
		}
	}

	/**
	 * Assert that this parameter is not empty. It trims the parameter to see if
	 * have any valid data on that.
	 * 
	 * @param name      of parameter
	 * @param parameter itself
	 */
	public static String checkNotEmpty(final String name, final String parameter) {
		if (parameter == null || parameter.trim().length() == 0) {
			throw new IllegalArgumentException("Parameter named '" + name + "' should be filled!");
		}
		return parameter;
	}

	/**
	 * Assert that this parameter is not empty. It will test for null and also the
	 * size of this array.
	 * 
	 * @param <T>       type of the array
	 * @param name      of parameter
	 * @param parameter itself
	 */
	public static <T> T[] checkNotEmpty(final String name, final T[] parameter) {
		if (parameter == null || parameter.length == 0) {
			throw new IllegalArgumentException("Parameter named '" + name + "' should be filled!");
		}
		return parameter;
	}

	/**
	 * Assert that this parameter is not null.
	 * 
	 * @param name      of parameter
	 * @param parameter itself
	 */
	public static <T> T checkNotNull(final String name, final T parameter) {
		if (parameter == null) {
			throw new IllegalArgumentException("Parameter named '" + name + "' should be not null!");
		}
		return parameter;
	}

	/**
	 * Assert that this parameter is null.
	 * 
	 * @param name      of parameter
	 * @param parameter itself
	 */
	public static void checkNullMandatory(final String name, final Object parameter) {
		if (parameter != null) {
			throw new IllegalArgumentException("Parameter named '" + name + "' should be null!");
		}
	}

	public static <T> T checkInstanceOf(final String name, Object parameter, final Class<T> clazz) {
		checkNotNull(name, parameter);
		checkNotNull("clazz", clazz);
		if (!clazz.isInstance(parameter)) {
			throw new IllegalArgumentException(
					"Parameter named '" + name + "' is not instance of clazz '" + clazz.getName() + "'!");
		}

		return clazz.cast(parameter);
	}
}
