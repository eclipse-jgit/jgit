package org.eclipse.jgit.util;

import org.eclipse.jgit.api.HookFailureHandler;

/**
 * An error handler to use for tests only.
 */
public class TestHookErrorHandler implements HookFailureHandler {

	Hook hook;

	ProcessResult processResult;

	String errorStreamContent;

	int invocationCount;

	@Override
	public void hookExecutionFailed(Hook aHook,
			ProcessResult aProcessResult, String anErrorStreamContent) {
		invocationCount++;
		this.hook = aHook;
		this.processResult = aProcessResult;
		this.errorStreamContent = anErrorStreamContent;
	}
}