/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.niofs.internal.daemon.filter;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.RefFilter;

/**
 * This RefFilter is used to exclude hidden branches from
 * {@link org.eclipse.jgit.transport.UploadPack}. Check
 * {@link org.eclipse.jgit.niofs.internal.daemon.git.Daemon}
 */
public class HiddenBranchRefFilter implements RefFilter {

	private static final String HIDDEN_BRANCH_REGEXP = "PR-\\d+-\\S+-\\S+";
	private static Pattern pattern = Pattern.compile(HIDDEN_BRANCH_REGEXP);

	@Override
	public Map<String, Ref> filter(final Map<String, Ref> refs) {
		return refs.entrySet().stream().filter(ref -> !HiddenBranchRefFilter.isHidden(ref.getKey()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	/**
	 * Checks if a branch name matches the hidden branch regexp
	 * 
	 * @param branch the branch you want to check.
	 * @return return if the branch is hidden or not
	 */
	public static boolean isHidden(String branch) {
		return pattern.matcher(branch).matches();
	}
}
