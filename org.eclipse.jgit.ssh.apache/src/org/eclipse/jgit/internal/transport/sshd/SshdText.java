package org.eclipse.jgit.internal.transport.sshd;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Externalized text messages for localization.
 */
public final class SshdText extends TranslationBundle {

	/**
	 * Get an instance of this translation bundle.
	 *
	 * @return an instance of this translation bundle
	 */
	public static SshdText get() {
		return NLS.getBundleFor(SshdText.class);
	}

	// @formatter:off
	/***/ public String authenticationCanceled;
	/***/ public String closeListenerFailed;
	/***/ public String configInvalidPath;
	/***/ public String configInvalidPositive;
	/***/ public String ftpCloseFailed;
	/***/ public String gssapiFailure;
	/***/ public String gssapiInitFailure;
	/***/ public String gssapiUnexpectedMechanism;
	/***/ public String gssapiUnexpectedMessage;
	/***/ public String keyEncryptedMsg;
	/***/ public String keyEncryptedPrompt;
	/***/ public String keyLoadFailed;
	/***/ public String knownHostsCouldNotUpdate;
	/***/ public String knownHostsFileLockedRead;
	/***/ public String knownHostsFileLockedUpdate;
	/***/ public String knownHostsFileReadFailed;
	/***/ public String knownHostsInvalidLine;
	/***/ public String knownHostsInvalidPath;
	/***/ public String knownHostsKeyFingerprints;
	/***/ public String knownHostsModifiedKeyAcceptPrompt;
	/***/ public String knownHostsModifiedKeyDenyMsg;
	/***/ public String knownHostsModifiedKeyStorePrompt;
	/***/ public String knownHostsModifiedKeyWarning;
	/***/ public String knownHostsRevokedKeyMsg;
	/***/ public String knownHostsUnknownKeyMsg;
	/***/ public String knownHostsUnknownKeyPrompt;
	/***/ public String knownHostsUnknownKeyType;
	/***/ public String knownHostsUserAskCreationMsg;
	/***/ public String knownHostsUserAskCreationPrompt;
	/***/ public String sessionCloseFailed;
	/***/ public String sshClosingDown;
	/***/ public String sshCommandTimeout;
	/***/ public String sshProcessStillRunning;

}
