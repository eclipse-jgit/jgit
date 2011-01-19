/*
 * Copyright (C) 2011, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.storage.dht;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_STRING_LENGTH;

import java.text.MessageFormat;

import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.RawParseUtils;

/**
 * A tiny implementation of a subset of the Google Protocol Buffers format.
 * <p>
 * For more information on the network format, see the canonical documentation
 * at <a href="http://code.google.com/p/protobuf/">Google Protocol Buffers</a>.
 */
public class TinyProtobuf {
	private static final int WIRE_VARINT = 0;

	private static final int WIRE_FIXED_64 = 1;

	private static final int WIRE_LEN_DELIM = 2;

	private static final int WIRE_FIXED_32 = 5;

	/**
	 * Create a new encoder.
	 *
	 * @param estimatedSize
	 *            estimated size of the message. If the size is accurate,
	 *            copying of the result can be avoided during
	 *            {@link Encoder#asByteArray()}. If the size is too small, the
	 *            buffer will grow dynamically.
	 * @return a new encoder.
	 */
	public static Encoder encode(int estimatedSize) {
		return new Encoder(new byte[estimatedSize]);
	}

	/**
	 * Create an encoder that estimates size.
	 *
	 * @return a new encoder.
	 */
	public static Encoder size() {
		return new Encoder(null);
	}

	/**
	 * Decode a buffer.
	 *
	 * @param buf
	 *            the buffer to read.
	 * @return a new decoder.
	 */
	public static Decoder decode(byte[] buf) {
		return decode(buf, 0, buf.length);
	}

	/**
	 * Decode a buffer.
	 *
	 * @param buf
	 *            the buffer to read.
	 * @param off
	 *            offset to begin reading from {@code buf}.
	 * @param len
	 *            total number of bytes to read from {@code buf}.
	 * @return a new decoder.
	 */
	public static Decoder decode(byte[] buf, int off, int len) {
		return new Decoder(buf, off, len);
	}

	/** An enumerated value that encodes/decodes as int32. */
	public static interface Enum {
		/** @return the wire value. */
		public int value();
	}

	/** Decode fields from a binary protocol buffer. */
	public static class Decoder {
		private final byte[] buf;

		private final int end;

		private int ptr;

		private int field;

		private int type;

		private Decoder(byte[] buf, int off, int len) {
			this.buf = buf;
			this.ptr = off;
			this.end = off + len;
		}

		/** @return get the field tag number, 0 on end of buffer. */
		public int next() {
			if (ptr == end)
				return 0;

			int fieldAndType = varint32();
			field = fieldAndType >>> 3;
			type = fieldAndType & 7;
			return field;
		}

		/** Skip the current field's value. */
		public void skip() {
			switch (type) {
			case WIRE_VARINT:
				varint64();
				break;
			case WIRE_FIXED_64:
				ptr += 8;
				break;
			case WIRE_LEN_DELIM:
				ptr += varint32();
				break;
			case WIRE_FIXED_32:
				ptr += 4;
				break;
			default:
				throw new IllegalStateException(MessageFormat.format(DhtText
						.get().protobufUnsupportedFieldType, Integer
						.valueOf(type)));
			}
		}

		/** @return decode the current field as an int32. */
		public int int32() {
			checkFieldType(WIRE_VARINT);
			return varint32();
		}

		/** @return decode the current field as an int64. */
		public long int64() {
			checkFieldType(WIRE_VARINT);
			return varint64();
		}

		/**
		 * @param <T>
		 *            the type of enumeration.
		 * @param all
		 *            all of the supported values.
		 * @return decode the current field as an enumerated value.
		 */
		public <T extends Enum> T intEnum(T[] all) {
			checkFieldType(WIRE_VARINT);
			int value = varint32();
			for (T t : all) {
				if (t.value() == value)
					return t;
			}
			throw new IllegalStateException(MessageFormat.format(
					DhtText.get().protobufWrongFieldType, Integer
							.valueOf(field), Integer.valueOf(type), all[0]
							.getClass().getSimpleName()));
		}

		/** @return decode the current field as a bool. */
		public boolean bool() {
			checkFieldType(WIRE_VARINT);
			int val = varint32();
			switch (val) {
			case 0:
				return false;
			case 1:
				return true;
			default:
				throw new IllegalStateException(MessageFormat.format(DhtText
						.get().protobufNotBooleanValue, Integer.valueOf(field),
						Integer.valueOf(val)));
			}
		}

		/** @return decode a fixed 64 bit value. */
		public long fixed64() {
			checkFieldType(WIRE_FIXED_64);
			long val = buf[ptr + 0] & 0xff;
			val |= ((long) (buf[ptr + 1] & 0xff)) << (1 * 8);
			val |= ((long) (buf[ptr + 2] & 0xff)) << (2 * 8);
			val |= ((long) (buf[ptr + 3] & 0xff)) << (3 * 8);
			val |= ((long) (buf[ptr + 4] & 0xff)) << (4 * 8);
			val |= ((long) (buf[ptr + 5] & 0xff)) << (5 * 8);
			val |= ((long) (buf[ptr + 6] & 0xff)) << (6 * 8);
			val |= ((long) (buf[ptr + 7] & 0xff)) << (7 * 8);
			ptr += 8;
			return val;
		}

		/** @return decode the current field as a string. */
		public String string() {
			checkFieldType(WIRE_LEN_DELIM);
			int len = varint32();
			String s = RawParseUtils.decode(buf, ptr, ptr + len);
			ptr += len;
			return s;
		}

		/** @return decode the current hex string to an ObjectId. */
		public ObjectId stringObjectId() {
			checkFieldType(WIRE_LEN_DELIM);
			int len = varint32();
			if (len != OBJECT_ID_STRING_LENGTH)
				throw new IllegalStateException(MessageFormat.format(DhtText
						.get().protobufWrongFieldLength,
						Integer.valueOf(field), Integer
								.valueOf(OBJECT_ID_STRING_LENGTH), Integer
								.valueOf(len)));

			ObjectId id = ObjectId.fromString(buf, ptr);
			ptr += OBJECT_ID_STRING_LENGTH;
			return id;
		}

		/** @return decode a string from 8 hex digits. */
		public int stringHex32() {
			checkFieldType(WIRE_LEN_DELIM);
			int len = varint32();
			if (len != 8)
				throw new IllegalStateException(MessageFormat.format(DhtText
						.get().protobufWrongFieldLength,
						Integer.valueOf(field), Integer.valueOf(8), Integer
								.valueOf(len)));
			int val = KeyUtils.parse32(buf, ptr);
			ptr += 8;
			return val;
		}

		/** @return decode the current field as an array of bytes. */
		public byte[] bytes() {
			checkFieldType(WIRE_LEN_DELIM);
			byte[] r = new byte[varint32()];
			System.arraycopy(buf, ptr, r, 0, r.length);
			ptr += r.length;
			return r;
		}

		/** @return decode the current raw bytes to an ObjectId. */
		public ObjectId bytesObjectId() {
			checkFieldType(WIRE_LEN_DELIM);
			int len = varint32();
			if (len != OBJECT_ID_LENGTH)
				throw new IllegalStateException(MessageFormat.format(DhtText
						.get().protobufWrongFieldLength,
						Integer.valueOf(field), Integer
								.valueOf(OBJECT_ID_LENGTH), Integer
								.valueOf(len)));

			ObjectId id = ObjectId.fromRaw(buf, ptr);
			ptr += OBJECT_ID_LENGTH;
			return id;
		}

		/** @return decode the current field as a nested message. */
		public Decoder message() {
			checkFieldType(WIRE_LEN_DELIM);
			int len = varint32();
			Decoder msg = decode(buf, ptr, len);
			ptr += len;
			return msg;
		}

		private int varint32() {
			long v = varint64();
			if (Integer.MAX_VALUE < v)
				throw new IllegalStateException(MessageFormat.format(DhtText
						.get().protobufWrongFieldType, Integer.valueOf(field),
						"int64", "int32"));
			return (int) v;
		}

		private long varint64() {
			int c = buf[ptr++];
			long r = c & 0x7f;
			int shift = 7;
			while ((c & 0x80) != 0) {
				c = buf[ptr++];
				r |= ((long) (c & 0x7f)) << shift;
				shift += 7;
			}
			return r;
		}

		private void checkFieldType(int expected) {
			if (type != expected)
				throw new IllegalStateException(MessageFormat.format(DhtText
						.get().protobufWrongFieldType, Integer.valueOf(field),
						Integer.valueOf(type), Integer.valueOf(expected)));
		}
	}

	/** Encode values into a binary protocol buffer. */
	public static class Encoder {
		private byte[] buf;

		private int ptr;

		private Encoder(byte[] buf) {
			this.buf = buf;
		}

		/**
		 * Encode a variable length positive integer.
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store. Must be >= 0.
		 */
		public void int32(int field, int value) {
			int64(field, value);
		}

		/**
		 * Encode a variable length positive integer.
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store; omitted if 0.
		 */
		public void int32IfNotZero(int field, int value) {
			int64IfNotZero(field, value);
		}

		/**
		 * Encode a variable length positive integer.
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store; omitted if negative.
		 */
		public void int32IfNotNegative(int field, int value) {
			int64IfNotNegative(field, value);
		}

		/**
		 * Encode a variable length positive integer.
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store. Must be >= 0.
		 */
		public void int64(int field, long value) {
			if (value < 0)
				throw new IllegalArgumentException(
						DhtText.get().protobufNegativeValuesNotSupported);

			field(field, WIRE_VARINT);
			varint(value);
		}

		/**
		 * Encode a variable length positive integer.
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store; omitted if 0.
		 */
		public void int64IfNotZero(int field, long value) {
			if (0 != value)
				int64(field, value);
		}

		/**
		 * Encode a variable length positive integer.
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store; omitted if negative.
		 */
		public void int64IfNotNegative(int field, long value) {
			if (0 <= value)
				int64(field, value);
		}

		/**
		 * Encode an enumerated value.
		 *
		 * @param <T>
		 *            type of the enumerated values.
		 * @param field
		 *            field tag number.
		 * @param value
		 *            value to store; if null the field is omitted.
		 */
		public <T extends Enum> void intEnum(int field, T value) {
			if (value != null) {
				field(field, WIRE_VARINT);
				varint(value.value());
			}
		}

		/**
		 * Encode a boolean value.
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store.
		 */
		public void bool(int field, boolean value) {
			field(field, WIRE_VARINT);
			varint(value ? 1 : 0);
		}

		/**
		 * Encode a boolean value, only if true.
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store.
		 */
		public void boolIfTrue(int field, boolean value) {
			if (value)
				bool(field, value);
		}

		/**
		 * Encode a fixed 64 value.
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store.
		 */
		public void fixed64(int field, long value) {
			field(field, WIRE_FIXED_64);
			if (buf != null) {
				ensureSpace(8);

				buf[ptr + 0] = (byte) value;
				value >>>= 8;

				buf[ptr + 1] = (byte) value;
				value >>>= 8;

				buf[ptr + 3] = (byte) value;
				value >>>= 8;

				buf[ptr + 3] = (byte) value;
				value >>>= 8;

				buf[ptr + 4] = (byte) value;
				value >>>= 8;

				buf[ptr + 5] = (byte) value;
				value >>>= 8;

				buf[ptr + 6] = (byte) value;
				value >>>= 8;

				buf[ptr + 7] = (byte) value;
			}
			ptr += 8;
		}

		/**
		 * Encode a length delimited bytes field.
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store; if null the field is omitted.
		 */
		public void bytes(int field, byte[] value) {
			if (value != null)
				bytes(field, value, 0, value.length);
		}

		/**
		 * Encode a length delimited bytes field.
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store; if null the field is omitted.
		 * @param off
		 *            position to copy from.
		 * @param len
		 *            number of bytes to copy.
		 */
		public void bytes(int field, byte[] value, int off, int len) {
			if (value != null) {
				field(field, WIRE_LEN_DELIM);
				varint(len);
				copy(value, off, len);
			}
		}

		/**
		 * Encode an ObjectId as a bytes (in raw binary format).
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store, as a raw binary; if null the field is
		 *            omitted.
		 */
		public void bytes(int field, AnyObjectId value) {
			if (value != null) {
				field(field, WIRE_LEN_DELIM);
				varint(OBJECT_ID_LENGTH);
				if (buf != null) {
					ensureSpace(OBJECT_ID_LENGTH);
					value.copyRawTo(buf, ptr);
				}
				ptr += OBJECT_ID_LENGTH;
			}
		}

		/**
		 * Encode an ObjectId as a string (in hex format).
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store, as a hex string; if null the field is
		 *            omitted.
		 */
		public void string(int field, AnyObjectId value) {
			if (value != null) {
				field(field, WIRE_LEN_DELIM);
				varint(OBJECT_ID_STRING_LENGTH);
				if (buf != null) {
					ensureSpace(OBJECT_ID_STRING_LENGTH);
					value.copyTo(buf, ptr);
				}
				ptr += OBJECT_ID_STRING_LENGTH;
			}
		}

		/**
		 * Encode a plain Java string.
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            the value to store; if null the field is omitted.
		 */
		public void string(int field, String value) {
			if (value != null)
				bytes(field, Constants.encode(value));
		}

		/**
		 * Encode a row key as a string.
		 *
		 * @param field
		 *            field tag number.
		 * @param key
		 *            the row key to store as a string; if null the field is
		 *            omitted.
		 */
		public void string(int field, RowKey key) {
			if (key != null)
				bytes(field, key.toBytes());
		}

		/**
		 * Encode an integer as an 8 byte hex string.
		 *
		 * @param field
		 *            field tag number.
		 * @param value
		 *            value to encode.
		 */
		public void stringHex32(int field, int value) {
			field(field, WIRE_LEN_DELIM);
			varint(8);
			if (buf != null) {
				ensureSpace(8);
				KeyUtils.format32(buf, ptr, value);
			}
			ptr += 8;
		}

		/**
		 * Encode a nested message.
		 *
		 * @param field
		 *            field tag number.
		 * @param msg
		 *            message to store; if null or empty the field is omitted.
		 */
		public void message(int field, Encoder msg) {
			if (msg != null && msg.ptr > 0)
				bytes(field, msg.buf, 0, msg.ptr);
		}

		private void field(int field, int type) {
			varint((field << 3) | type);
		}

		private void varint(long value) {
			if (buf != null) {
				if (buf.length - ptr < 10)
					ensureSpace(varintSize(value));

				do {
					byte b = (byte) (value & 0x7f);
					value >>>= 7;
					if (value != 0)
						b |= 0x80;
					buf[ptr++] = b;
				} while (value != 0);
			} else {
				ptr += varintSize(value);
			}
		}

		private static int varintSize(long value) {
			value >>>= 7;
			int need = 1;
			for (; value != 0; value >>>= 7)
				need++;
			return need;
		}

		private void copy(byte[] src, int off, int cnt) {
			if (buf != null) {
				ensureSpace(cnt);
				System.arraycopy(src, off, buf, ptr, cnt);
			}
			ptr += cnt;
		}

		private void ensureSpace(int need) {
			if (buf.length - ptr < need) {
				byte[] n = new byte[Math.max(ptr + need, buf.length * 2)];
				System.arraycopy(buf, 0, n, 0, ptr);
				buf = n;
			}
		}

		/** @return size of the protocol buffer message, in bytes. */
		public int size() {
			return ptr;
		}

		/** @return the current buffer, as a byte array. */
		public byte[] asByteArray() {
			if (ptr == buf.length)
				return buf;
			byte[] r = new byte[ptr];
			System.arraycopy(buf, 0, r, 0, ptr);
			return r;
		}
	}

	private TinyProtobuf() {
		// Don't make instances.
	}
}
