package org.eclipse.jgit.errors;

import java.io.IOException;

import org.eclipse.jgit.JGitText;

/** Thrown when PackParser finds an object larger than a predefined limit */
public class TooLargeObjectInPackException extends IOException {
	private static final long serialVersionUID = 1L;


	/** Construct a too large object in pack exception */
	public TooLargeObjectInPackException() {
		super(JGitText.get().receivePackObjectTooLarge);
	}
}