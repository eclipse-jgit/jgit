/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revplot;

import java.io.Serializable;

/**
 * A line space within the graph.
 * <p>
 * Commits are strung onto a lane. For many UIs a lane represents a column.
 */
public class PlotLane implements Serializable {
	private static final long serialVersionUID = 1L;

	int position;

	/**
	 * Logical location of this lane within the graphing plane.
	 *
	 * @return location of this lane, 0 through the maximum number of lanes.
	 */
	public int getPosition() {
		return position;
	}
}
