/*
 * Copyright (C) 2008-2010, Google Inc.
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

import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_ATOMIC;
import static org.eclipse.jgit.transport.GitProtocolConstants.CAPABILITY_REPORT_STATUS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.eclipse.jgit.errors.UnpackException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.RefAdvertiser.PacketLineOutRefAdvertiser;

/**
 * Implements the server side of a push connection, receiving objects.
 */
public class ReceivePack extends BaseReceivePack {
	/** Hook to validate the update commands before execution. */
	private PreReceiveHook preReceive;

	/** Hook to report on the commands after execution. */
	private PostReceiveHook postReceive;

	/** If {@link BasePackPushConnection#CAPABILITY_REPORT_STATUS} is enabled. */
	private boolean reportStatus;

	private boolean echoCommandFailures;

	/**
	 * Create a new pack receive for an open repository.
	 *
	 * @param into
	 *            the destination repository.
	 */
	public ReceivePack(final Repository into) {
		super(into);
		preReceive = PreReceiveHook.NULL;
		postReceive = PostReceiveHook.NULL;
	}

	/** @return the hook invoked before updates occur. */
	public PreReceiveHook getPreReceiveHook() {
		return preReceive;
	}

	/**
	 * Set the hook which is invoked prior to commands being executed.
	 * <p>
	 * Only valid commands (those which have no obvious errors according to the
	 * received input and this instance's configuration) are passed into the
	 * hook. The hook may mark a command with a result of any value other than
	 * {@link Result#NOT_ATTEMPTED} to block its execution.
	 * <p>
	 * The hook may be called with an empty command collection if the current
	 * set is completely invalid.
	 *
	 * @param h
	 *            the hook instance; may be null to disable the hook.
	 */
	public void setPreReceiveHook(final PreReceiveHook h) {
		preReceive = h != null ? h : PreReceiveHook.NULL;
	}

	/** @return the hook invoked after updates occur. */
	public PostReceiveHook getPostReceiveHook() {
		return postReceive;
	}

	/**
	 * Set the hook which is invoked after commands are executed.
	 * <p>
	 * Only successful commands (type is {@link Result#OK}) are passed into the
	 * hook. The hook may be called with an empty command collection if the
	 * current set all resulted in an error.
	 *
	 * @param h
	 *            the hook instance; may be null to disable the hook.
	 */
	public void setPostReceiveHook(final PostReceiveHook h) {
		postReceive = h != null ? h : PostReceiveHook.NULL;
	}

	/**
	 * @param echo
	 *            if true this class will report command failures as warning
	 *            messages before sending the command results. This is usually
	 *            not necessary, but may help buggy Git clients that discard the
	 *            errors when all branches fail.
	 */
	public void setEchoCommandFailures(boolean echo) {
		echoCommandFailures = echo;
	}

	/**
	 * Execute the receive task on the socket.
	 *
	 * @param input
	 *            raw input to read client commands and pack data from. Caller
	 *            must ensure the input is buffered, otherwise read performance
	 *            may suffer.
	 * @param output
	 *            response back to the Git network client. Caller must ensure
	 *            the output is buffered, otherwise write performance may
	 *            suffer.
	 * @param messages
	 *            secondary "notice" channel to send additional messages out
	 *            through. When run over SSH this should be tied back to the
	 *            standard error channel of the command execution. For most
	 *            other network connections this should be null.
	 * @throws IOException
	 */
	public void receive(final InputStream input, final OutputStream output,
			final OutputStream messages) throws IOException {
		init(input, output, messages);
		try {
			service();
		} finally {
			try {
				close();
			} finally {
				release();
			}
		}
	}

	@Override
	protected void enableCapabilities() {
		reportStatus = isCapabilityEnabled(CAPABILITY_REPORT_STATUS);
		super.enableCapabilities();
	}

	private void service() throws IOException {
		if (isBiDirectionalPipe()) {
			sendAdvertisedRefs(new PacketLineOutRefAdvertiser(pckOut));
			pckOut.flush();
		} else
			getAdvertisedOrDefaultRefs();
		if (hasError())
			return;
		recvCommands();
		if (hasCommands()) {
			enableCapabilities();

			Throwable unpackError = null;
			if (needPack()) {
				try {
					receivePackAndCheckConnectivity();
				} catch (IOException err) {
					unpackError = err;
				} catch (RuntimeException err) {
					unpackError = err;
				} catch (Error err) {
					unpackError = err;
				}
			}

			if (unpackError == null) {
				boolean atomic = isCapabilityEnabled(CAPABILITY_ATOMIC);
				validateCommands();
				if (atomic && anyRejects())
					failPendingCommands();

				preReceive.onPreReceive(this, filterCommands(Result.NOT_ATTEMPTED));
				if (atomic && anyRejects())
					failPendingCommands();
				executeCommands();
			}
			unlockPack();

			if (reportStatus) {
				if (echoCommandFailures && msgOut != null) {
					sendStatusReport(false, unpackError, new Reporter() {
						void sendString(final String s) throws IOException {
							msgOut.write(Constants.encode(s + "\n")); //$NON-NLS-1$
						}
					});
					msgOut.flush();
					try {
						Thread.sleep(500);
					} catch (InterruptedException wakeUp) {
						// Ignore an early wake up.
					}
				}
				sendStatusReport(true, unpackError, new Reporter() {
					void sendString(final String s) throws IOException {
						pckOut.writeString(s + "\n"); //$NON-NLS-1$
					}
				});
				pckOut.end();
			} else if (msgOut != null) {
				sendStatusReport(false, unpackError, new Reporter() {
					void sendString(final String s) throws IOException {
						msgOut.write(Constants.encode(s + "\n")); //$NON-NLS-1$
					}
				});
			}

			postReceive.onPostReceive(this, filterCommands(Result.OK));

			if (unpackError != null)
				throw new UnpackException(unpackError);
		}
	}

	@Override
	protected String getLockMessageProcessName() {
		return "jgit receive-pack"; //$NON-NLS-1$
	}
}
