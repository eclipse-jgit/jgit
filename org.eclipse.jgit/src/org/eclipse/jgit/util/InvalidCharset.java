package org.eclipse.jgit.util;

import java.nio.*;
import java.nio.charset.*;

/**
 * @author Marc Strapetz
 */
public class InvalidCharset extends Charset {

	// Constants ==============================================================

	public static final InvalidCharset INSTANCE = new InvalidCharset();

	// Setup ==================================================================

	private InvalidCharset() {
		super("Invalid", new String[0]);
	}

	// Implemented ============================================================

	@Override
	public boolean contains(Charset cs) {
		return false;
	}

	@Override
	public CharsetDecoder newDecoder() {
		return new CharsetDecoder(this, 1, 1) {
			@Override
			protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
				if (in.remaining() > 0) {
					return CoderResult.unmappableForLength(1);
				}

				return CoderResult.UNDERFLOW;
			}
		};
	}

	@Override
	public CharsetEncoder newEncoder() {
		return new CharsetEncoder(this, 4, 4) {
			@Override
			protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out) {
				if (in.remaining() > 0) {
					return CoderResult.unmappableForLength(1);
				}

				return CoderResult.UNDERFLOW;
			}
		};
	}
}