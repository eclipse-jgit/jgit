/*
 * Copyright (C) 2018-2019, Andre Bossert <andre.bossert@siemens.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.diffmergetool;

/**
 * Optional boolean
 *
 * @since 5.4
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

}
