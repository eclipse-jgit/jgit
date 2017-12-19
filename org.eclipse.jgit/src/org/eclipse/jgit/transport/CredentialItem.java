/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.transport;

import java.util.Arrays;

import org.eclipse.jgit.internal.JGitText;

/**
 * A credential requested from a
 * {@link org.eclipse.jgit.transport.CredentialsProvider}.
 *
 * Most users should work with the specialized subclasses:
 * <ul>
 * <li>{@link org.eclipse.jgit.transport.CredentialItem.Username} for
 * usernames</li>
 * <li>{@link org.eclipse.jgit.transport.CredentialItem.Password} for
 * passwords</li>
 * <li>{@link org.eclipse.jgit.transport.CredentialItem.StringType} for other
 * general string information</li>
 * <li>{@link org.eclipse.jgit.transport.CredentialItem.CharArrayType} for other
 * general secret information</li>
 * </ul>
 *
 * This class is not thread-safe. Applications should construct their own
 * instance for each use, as the value is held within the CredentialItem object.
 */
public abstract class CredentialItem {
	private final String promptText;

	private final boolean valueSecure;

	/**
	 * Initialize a prompt.
	 *
	 * @param promptText
	 *            prompt to display to the user alongside of the input field.
	 *            Should be sufficient text to indicate what to supply for this
	 *            item.
	 * @param maskValue
	 *            true if the value should be masked from displaying during
	 *            input. This should be true for passwords and other secrets,
	 *            false for names and other public data.
	 */
	public CredentialItem(String promptText, boolean maskValue) {
		this.promptText = promptText;
		this.valueSecure = maskValue;
	}

	/**
	 * Get prompt to display to the user.
	 *
	 * @return prompt to display to the user.
	 */
	public String getPromptText() {
		return promptText;
	}

	/**
	 * Whether the value should be masked when entered.
	 *
	 * @return true if the value should be masked when entered.
	 */
	public boolean isValueSecure() {
		return valueSecure;
	}

	/**
	 * Clear the stored value, destroying it as much as possible.
	 */
	public abstract void clear();

	/**
	 * An item whose value is stored as a string.
	 *
	 * When working with secret data, consider {@link CharArrayType} instead, as
	 * the internal members of the array can be cleared, reducing the chances
	 * that the password is left in memory after authentication is completed.
	 */
	public static class StringType extends CredentialItem {
		private String value;

		/**
		 * Initialize a prompt for a single string.
		 *
		 * @param promptText
		 *            prompt to display to the user alongside of the input
		 *            field. Should be sufficient text to indicate what to
		 *            supply for this item.
		 * @param maskValue
		 *            true if the value should be masked from displaying during
		 *            input. This should be true for passwords and other
		 *            secrets, false for names and other public data.
		 */
		public StringType(String promptText, boolean maskValue) {
			super(promptText, maskValue);
		}

		@Override
		public void clear() {
			value = null;
		}

		/** @return the current value */
		public String getValue() {
			return value;
		}

		/**
		 *
		 * @param newValue
		 */
		public void setValue(String newValue) {
			value = newValue;
		}
	}

	/** An item whose value is stored as a char[] and is therefore clearable. */
	public static class CharArrayType extends CredentialItem {
		private char[] value;

		/**
		 * Initialize a prompt for a secure value stored in a character array.
		 *
		 * @param promptText
		 *            prompt to display to the user alongside of the input
		 *            field. Should be sufficient text to indicate what to
		 *            supply for this item.
		 * @param maskValue
		 *            true if the value should be masked from displaying during
		 *            input. This should be true for passwords and other
		 *            secrets, false for names and other public data.
		 */
		public CharArrayType(String promptText, boolean maskValue) {
			super(promptText, maskValue);
		}

		/** Destroys the current value, clearing the internal array. */
		@Override
		public void clear() {
			if (value != null) {
				Arrays.fill(value, (char) 0);
				value = null;
			}
		}

		/**
		 * Get the current value.
		 *
		 * The returned array will be cleared out when {@link #clear()} is
		 * called. Callers that need the array elements to survive should delay
		 * invoking {@code clear()} until the value is no longer necessary.
		 *
		 * @return the current value array. The actual internal array is
		 *         returned, reducing the number of copies present in memory.
		 */
		public char[] getValue() {
			return value;
		}

		/**
		 * Set the new value, clearing the old value array.
		 *
		 * @param newValue
		 *            if not null, the array is copied.
		 */
		public void setValue(char[] newValue) {
			clear();

			if (newValue != null) {
				value = new char[newValue.length];
				System.arraycopy(newValue, 0, value, 0, newValue.length);
			}
		}

		/**
		 * Set the new value, clearing the old value array.
		 *
		 * @param newValue
		 *            the new internal array. The array is <b>NOT</b> copied.
		 */
		public void setValueNoCopy(char[] newValue) {
			clear();
			value = newValue;
		}
	}

	/** An item whose value is a boolean choice, presented as Yes/No. */
	public static class YesNoType extends CredentialItem {
		private boolean value;

		/**
		 * Initialize a prompt for a single boolean answer.
		 *
		 * @param promptText
		 *            prompt to display to the user alongside of the input
		 *            field. Should be sufficient text to indicate what to
		 *            supply for this item.
		 */
		public YesNoType(String promptText) {
			super(promptText, false);
		}

		@Override
		public void clear() {
			value = false;
		}

		/** @return the current value */
		public boolean getValue() {
			return value;
		}

		/**
		 * Set the new value.
		 *
		 * @param newValue
		 */
		public void setValue(boolean newValue) {
			value = newValue;
		}
	}

	/** An advice message presented to the user, with no response required. */
	public static class InformationalMessage extends CredentialItem {
		/**
		 * Initialize an informational message.
		 *
		 * @param messageText
		 *            message to display to the user.
		 */
		public InformationalMessage(String messageText) {
			super(messageText, false);
		}

		@Override
		public void clear() {
			// Nothing to clear.
		}
	}

	/** Prompt for a username, which is not masked on input. */
	public static class Username extends StringType {
		/** Initialize a new username item, with a default username prompt. */
		public Username() {
			super(JGitText.get().credentialUsername, false);
		}
	}

	/** Prompt for a password, which is masked on input. */
	public static class Password extends CharArrayType {
		/** Initialize a new password item, with a default password prompt. */
		public Password() {
			super(JGitText.get().credentialPassword, true);
		}

		/**
		 * Initialize a new password item, with given prompt.
		 *
		 * @param msg
		 *            prompt message
		 */
		public Password(String msg) {
			super(msg, true);
		}
	}
}
