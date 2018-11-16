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
	/***/ public String configInvalidPattern;
	/***/ public String configInvalidPositive;
	/***/ public String configNoKnownHostKeyAlgorithms;
	/***/ public String configNoRemainingHostKeyAlgorithms;
	/***/ public String ftpCloseFailed;
	/***/ public String gssapiFailure;
	/***/ public String gssapiInitFailure;
	/***/ public String gssapiUnexpectedMechanism;
	/***/ public String gssapiUnexpectedMessage;
	/***/ public String identityFileCannotDecrypt;
	/***/ public String identityFileNoKey;
	/***/ public String identityFileMultipleKeys;
	/***/ public String identityFileUnsupportedFormat;
	/***/ public String kexServerKeyInvalid;
	/***/ public String keyEncryptedMsg;
	/***/ public String keyEncryptedPrompt;
	/***/ public String keyEncryptedRetry;
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
	/***/ public String passwordPrompt;
	/***/ public String proxyCannotAuthenticate;
	/***/ public String proxyHttpFailure;
	/***/ public String proxyHttpInvalidUserName;
	/***/ public String proxyHttpUnexpectedReply;
	/***/ public String proxyHttpUnspecifiedFailureReason;
	/***/ public String proxyPasswordPrompt;
	/***/ public String proxySocksAuthenticationFailed;
	/***/ public String proxySocksFailureForbidden;
	/***/ public String proxySocksFailureGeneral;
	/***/ public String proxySocksFailureHostUnreachable;
	/***/ public String proxySocksFailureNetworkUnreachable;
	/***/ public String proxySocksFailureRefused;
	/***/ public String proxySocksFailureTTL;
	/***/ public String proxySocksFailureUnspecified;
	/***/ public String proxySocksFailureUnsupportedAddress;
	/***/ public String proxySocksFailureUnsupportedCommand;
	/***/ public String proxySocksGssApiFailure;
	/***/ public String proxySocksGssApiMessageTooShort;
	/***/ public String proxySocksGssApiUnknownMessage;
	/***/ public String proxySocksGssApiVersionMismatch;
	/***/ public String proxySocksNoRemoteHostName;
	/***/ public String proxySocksPasswordTooLong;
	/***/ public String proxySocksUnexpectedMessage;
	/***/ public String proxySocksUnexpectedVersion;
	/***/ public String proxySocksUsernameTooLong;
	/***/ public String sessionCloseFailed;
	/***/ public String sshClosingDown;
	/***/ public String sshCommandTimeout;
	/***/ public String sshProcessStillRunning;
	/***/ public String unknownProxyProtocol;

}
