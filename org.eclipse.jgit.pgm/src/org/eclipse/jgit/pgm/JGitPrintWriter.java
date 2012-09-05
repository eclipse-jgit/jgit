package org.eclipse.jgit.pgm;

import java.io.IOException;
import java.io.Writer;

import org.eclipse.jgit.util.SystemReader;

/**
 * An alternative PrintWriter that doesn't catch exceptions.
 */
public class JGitPrintWriter extends Writer {

	private final Writer out;

	JGitPrintWriter(Writer out) {
		this.out = out;
	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		out.write(cbuf, off, len);
	}

	@Override
	public void flush() throws IOException {
		out.flush();
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

	/**
	 * Print a string and terminate with a line feed.
	 *
	 * @param s
	 * @throws IOException
	 */
	public void println(String s) throws IOException {
		print(s + LF);
	}

	/**
	 * Print a platform dependent new line
	 *
	 * @throws IOException
	 */
	public void println() throws IOException {
		out.write(LF);
	}

	private String LF = SystemReader.getInstance().getenv("os.name")
			.equals("Windows") ? "\r\n" : "\n";

	/**
	 * Print a char
	 *
	 * @param value
	 * @throws IOException
	 */
	public void print(char value) throws IOException {
		out.write(String.valueOf(value));
	}

	/**
	 * Print an int as string
	 *
	 * @param value
	 * @throws IOException
	 */
	public void print(int value) throws IOException {
		out.write(String.valueOf(value));
	}

	/**
	 * Print a long as string
	 *
	 * @param value
	 * @throws IOException
	 */
	public void print(long value) throws IOException {
		out.write(String.valueOf(value));
	}

	/**
	 * Print a short as string
	 *
	 * @param value
	 * @throws IOException
	 */
	public void print(short value) throws IOException {
		out.write(String.valueOf(value));
	}

	/**
	 * Print a formatted message according to
	 * {@link String#format(String, Object...)}.
	 *
	 * @param fmt
	 * @param args
	 * @throws IOException
	 */
	public void format(String fmt, Object... args) throws IOException {
		String s = String.format(fmt, args);
		print(s);
	}

	/**
	 * Print a string
	 * 
	 * @param any
	 * @throws IOException
	 */
	public void print(String any) throws IOException {
		print(any);
	}

	/**
	 * Print an object's toString representations
	 * 
	 * @param any
	 * @throws IOException
	 */
	public void print(Object any) throws IOException {
		print(any.toString());
	}
}
