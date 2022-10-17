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
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.engine.execution.BeforeEachMethodAdapter;
import org.junit.jupiter.engine.extension.ExtensionRegistry;

/**
 * JUnit 5 ParameterResolver allowing to inject test parameters declared on test
 * methods of a ParameterizedTest also into methods annotated with @BeforeEach
 * or @AfterEach.
 *
 * @since 6.4
 */
public class CustomParameterResolver
		implements BeforeEachMethodAdapter, ParameterResolver {

	private ParameterResolver parameterisedTestParameterResolver = null;

	@Override
	public void invokeBeforeEachMethod(ExtensionContext context,
			ExtensionRegistry registry) throws Throwable {
		Optional<ParameterResolver> resolverOptional = registry
				.getExtensions(ParameterResolver.class).stream()
				.filter(parameterResolver -> parameterResolver.getClass()
						.getName()
						.contains("ParameterizedTestParameterResolver"))
				.findFirst();
		if (!resolverOptional.isPresent()) {
			throw new IllegalStateException(
					"ParameterizedTestParameterResolver not found in the registry. "
							+ "Probably it's not a Parameterized Test");
		}
		parameterisedTestParameterResolver = resolverOptional.get();
	}

	@Override
	public boolean supportsParameter(ParameterContext parameterContext,
			ExtensionContext extensionContext)
			throws ParameterResolutionException {
		if (parameterContext.getParameter().getType() == TestInfo.class) {
			return false;
		}
		if (isExecutedOnAfterOrBeforeMethod(parameterContext)) {
			ParameterContext pContext = getMappedContext(parameterContext,
					extensionContext);
			return parameterisedTestParameterResolver
					.supportsParameter(pContext, extensionContext);
		}
		return false;
	}

	@Override
	public Object resolveParameter(ParameterContext parameterContext,
			ExtensionContext extensionContext)
			throws ParameterResolutionException {
		return parameterisedTestParameterResolver.resolveParameter(
				getMappedContext(parameterContext, extensionContext),
				extensionContext);
	}

	private MappedParameterContext getMappedContext(
			ParameterContext parameterContext,
			ExtensionContext extensionContext) {
		Method method = extensionContext.getRequiredTestMethod();
		Parameter[] params = method
				.getParameters();
		int i = parameterContext.getIndex();
		Parameter p = null;
		if (i < params.length) {
			p = params[i];
		}
		return new MappedParameterContext(parameterContext.getIndex(),
				p,
				Optional.of(parameterContext.getTarget()));
	}

	private boolean isExecutedOnAfterOrBeforeMethod(
			ParameterContext parameterContext) {
		return Arrays
				.stream(parameterContext.getDeclaringExecutable()
						.getDeclaredAnnotations())
				.anyMatch(this::isAfterEachOrBeforeEachAnnotation);
	}

	private boolean isAfterEachOrBeforeEachAnnotation(Annotation annotation) {
		return annotation.annotationType() == BeforeEach.class
				|| annotation.annotationType() == AfterEach.class;
	}
}
