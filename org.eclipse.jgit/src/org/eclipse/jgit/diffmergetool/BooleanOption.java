/*
 * Copyright (C) 2018-2020, Andre Bossert <andre.bossert@siemens.com>
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diffmergetool;

/**
 * Optional boolean that can be configured to true or false; or be default true
 * or false
 *
 * @since 5.7
 */
public enum BooleanOption {
	/**
	 * the option is not defined and is default false
	 */
	DEFAULT_FALSE(),

	/**
	 * the option is not defined and is default true
	 */
	DEFAULT_TRUE(),

	/**
	 * the option is disabled
	 */
	FALSE(),

	/**
	 * the option is enabled
	 */
	TRUE();

	BooleanOption() {
	}

	/**
	 * @return boolean value of the option
	 */
	public boolean toBoolean() {
		switch (this) {
		case TRUE:
		case DEFAULT_TRUE:
			return true;
		default:
			return false;
		}
	}

	/**
	 * @return true if the the option is configured and false if not
	 */
	public boolean isConfigured() {
		switch (this) {
		case TRUE:
		case FALSE:
			return true;
		default:
			return false;
		}
	}

	/**
	 * @return true if the the option is default and false if not
	 */
	public boolean isDefault() {
		return !isConfigured();
	}

	/**
	 * @param value
	 *            the boolean value as configured input
	 * @return the configured boolean option
	 */
	public static BooleanOption toConfigured(boolean value) {
		return value ? BooleanOption.TRUE : BooleanOption.FALSE;
	}

	/**
	 * @param value
	 *            the boolean value as default input
	 * @return the default boolean option
	 */
	public static BooleanOption toDefault(boolean value) {
		return value ? BooleanOption.DEFAULT_TRUE
				: BooleanOption.DEFAULT_FALSE;
	}

}
