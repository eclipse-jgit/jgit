package org.eclipse.jgit.errors;

/**
 * BinaryBlobException is used to signal that binary data was found
 * in a context that requires text (eg. for generating textual diffs).
 */
public class BinaryBlobException extends Exception {
	private static final long serialVersionUID = 1L;

	public BinaryBlobException() {}
}
