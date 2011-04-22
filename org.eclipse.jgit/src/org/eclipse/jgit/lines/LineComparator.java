/*******************************************************************************
 *  Copyright (c) 2011 GitHub Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.jgit.lines;

import java.util.Comparator;

/**
 * Line comparator that orders overlapping lines by finding their location at a
 * common revision and comparing line numbers.
 * 
 * @author Kevin Sawicki (kevin@github.com)
 */
public class LineComparator implements Comparator<Line> {

	/**
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
	 */
	public int compare(Line line1, Line line2) {
		if (line1.overlaps(line2)) {
			int revision = Math.min(line1.getEnd(), line2.getEnd());
			return line1.getNumber(revision) - line2.getNumber(revision);
		} else
			return line1.getStart() - line2.getStart();
	}
}
