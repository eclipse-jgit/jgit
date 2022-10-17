/*
 * Copyright (C) 2022, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.junit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.platform.commons.util.AnnotationUtils;

/**
 * Maps parameters of parameterized tests
 *
 * @since 6.4
 */
public class MappedParameterContext implements ParameterContext {

	private final int index;

	private final Parameter parameter;

	private final Optional<Object> target;

	/**
	 * @param index
	 * @param parameter
	 * @param target
	 */
	public MappedParameterContext(int index, Parameter parameter,
			Optional<Object> target) {
		this.index = index;
		this.parameter = parameter;
		this.target = target;
	}

	@Override
	public boolean isAnnotated(Class<? extends Annotation> annotationType) {
		return AnnotationUtils.isAnnotated(parameter, annotationType);
	}

	@Override
	public <A extends Annotation> Optional<A> findAnnotation(
			Class<A> annotationType) {
		return AnnotationUtils.findAnnotation(parameter, annotationType);
	}

	@Override
	public <A extends Annotation> List<A> findRepeatableAnnotations(
			Class<A> annotationType) {
		return AnnotationUtils.findRepeatableAnnotations(parameter,
				annotationType);
	}

	@Override
	public int getIndex() {
		return index;
	}

	@Override
	public Parameter getParameter() {
		return parameter;
	}

	@Override
	public Optional<Object> getTarget() {
		return target;
	}
}
