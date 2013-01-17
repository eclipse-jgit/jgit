/*******************************************************************************
 * Copyright (C) 2013, Tomasz Zarna <tomasz.zarna@gmail.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.jgit.lib;

/**
 * TODO
 *
 */
public interface ConfigEnum {
	/**
	 * TODO
	 *
	 * @return TODO
	 */
	String toConfigValue();

	/**
	 * TODO
	 *
	 * @param in
	 * @return TODO
	 */
	boolean matchConfigValue(String in);
}