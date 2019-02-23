/*
 * Copyright (C) 2018-2019, Andre Bossert <andre.bossert@siemens.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diffmergetool;

import java.util.Optional;

/**
 * Optional boolean
 *
 * @since 5.7
 */
public enum BooleanOption {
	/**
	 * the option is not defined and is default false
	 */
	NOT_DEFINED_FALSE(),

	/**
	 * the option is not defined and is default true
	 */
	NOT_DEFINED_TRUE(),

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
		case NOT_DEFINED_TRUE:
			return true;
		default:
			return false;
		}
	}

	/**
	 * @return true if the the option was defined and false if not
	 */
	public boolean isDefined() {
		switch (this) {
		case TRUE:
		case FALSE:
			return true;
		default:
			return false;
		}
	}

	/**
	 * @param value
	 *            the boolean value as input
	 * @return the defined boolean option
	 */
	public static BooleanOption defined(boolean value) {
		return value ? BooleanOption.TRUE : BooleanOption.FALSE;
	}

	/**
	 * @param value
	 *            the boolean value as default input
	 * @return the not defined boolean option
	 */
	public static BooleanOption notDefined(boolean value) {
		return value ? BooleanOption.NOT_DEFINED_TRUE
				: BooleanOption.NOT_DEFINED_FALSE;
	}

	/**
	 * From an optional
	 * 
	 * @param orig
	 *            the optional
	 * @return the boolean option
	 */
	public static BooleanOption from(Optional<Boolean> orig) {
		if (!orig.isPresent())
			return NOT_DEFINED_FALSE;
		return defined(orig.get().booleanValue());
	}

}
