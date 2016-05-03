package org.eclipse.jgit.util;

import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jgit.lib.Repository;

/**
 * The factory responsible for creating instances of {@link BuiltinCommand}.
 *
 * @since 4.5
 */
public interface BuiltinCommandFactory {
	/**
	 * Creates a new {@link BuiltinCommand}.
	 *
	 * @param db
	 *            the repository this command should work on
	 * @param in
	 *            the {@link InputStream} this command should read from
	 * @param out
	 *            the {@link OutputStream} this command should write to
	 * @return the create {@link BuiltinCommand}
	 */
	public BuiltinCommand create(Repository db, InputStream in,
			OutputStream out);

}
