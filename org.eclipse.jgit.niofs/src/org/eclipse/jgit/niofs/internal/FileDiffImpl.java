/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal;

import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotEmpty;
import static org.eclipse.jgit.niofs.internal.util.Preconditions.checkNotNull;

import java.util.List;

import org.eclipse.jgit.niofs.fs.attribute.FileDiff;

/**
 * Represents difference between two files. This is just a segment of the file,
 * not necessary the differences of the whole file.
 */
public class FileDiffImpl implements FileDiff {

	private List<String> linesA;
	private List<String> linesB;
	private String changeType;
	private String nameA;
	private String nameB;
	private int startA;
	private int endA;
	private int startB;
	private int endB;

	public FileDiffImpl(final String nameA, final String nameB, final int startA, final int endA, final int startB,
			final int endB, final String changeType, final List<String> linesA, final List<String> linesB) {

		this.nameA = checkNotEmpty("nameA", nameA);
		this.nameB = checkNotEmpty("nameB", nameB);
		this.startA = startA;
		this.endA = endA;
		this.startB = startB;
		this.endB = endB;
		this.changeType = checkNotEmpty("nameA", changeType);
		this.linesA = checkNotNull("linesA", linesA);
		this.linesB = checkNotNull("linesB", linesB);
	}

	@Override
	public List<String> getLinesA() {
		return linesA;
	}

	@Override
	public List<String> getLinesB() {
		return linesB;
	}

	@Override
	public String getChangeType() {
		return changeType;
	}

	@Override
	public String getNameA() {
		return nameA;
	}

	@Override
	public String getNameB() {
		return nameB;
	}

	@Override
	public int getStartA() {
		return startA;
	}

	@Override
	public int getEndA() {
		return endA;
	}

	@Override
	public int getStartB() {
		return startB;
	}

	@Override
	public int getEndB() {
		return endB;
	}

	@Override
	public String toString() {

		final String linesFromA = this.getLinesA().stream().reduce("",
				(acum, elem) -> acum += "-" + new String(elem.getBytes()) + "\n");
		final String linesFromB = this.getLinesB().stream().reduce("",
				(acum, elem) -> acum += "+" + new String(elem.getBytes()) + "\n");

		StringBuilder builder = new StringBuilder();
		builder.append("FileDiff { \n");
		builder.append(this.getChangeType());
		builder.append(" , \n");

		builder.append(this.getNameA());
		builder.append(" -> ");
		builder.append("( " + this.getStartA() + " , " + this.getEndA() + " )");
		builder.append("[ " + linesFromA + " ]");
		builder.append(" || ");
		builder.append(this.getNameB());
		builder.append(" -> ");
		builder.append("( " + this.getStartB() + " , " + this.getEndB() + " )");
		builder.append("[ " + linesFromB + " ]");
		builder.append("}");

		return builder.toString();
	}

	@Override
	public int hashCode() {
		int result = Integer.hashCode(startA);
		result = ~~result;
		result = 31 * result + (Integer.hashCode(endA));
		result = ~~result;
		result = 31 * result + (Integer.hashCode(startB));
		result = ~~result;
		result = 31 * result + (Integer.hashCode(endB));
		result = ~~result;
		result = 31 * result + (nameA.hashCode());
		result = ~~result;
		result = 31 * result + (nameB.hashCode());
		result = ~~result;
		result = 31 * result + (changeType.hashCode());
		result = ~~result;
		result = 31 * result + (linesA.hashCode());
		result = ~~result;
		result = 31 * result + (linesB.hashCode());
		result = ~~result;
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof FileDiffImpl) {
			FileDiffImpl external = (FileDiffImpl) obj;
			return this.startA == external.startA && this.endA == external.endA && this.startB == external.startB
					&& this.endB == external.endB && this.changeType.equals(external.changeType)
					&& this.nameA.equals(external.nameA) && this.nameB.equals(external.nameB)
					&& this.linesA.equals(external.linesA) && this.linesB.equals(external.getLinesB());
		} else {
			return super.equals(obj);
		}
	}
}
