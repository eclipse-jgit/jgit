/*
 * Copyright (C) 2018, 2024 Thomas Wolf <twolf@apache.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.internal.transport.sshd;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;

/**
 * Externalized text messages for localization.
 */
@SuppressWarnings("MissingSummary")
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
	/***/ public String authenticationOnClosedSession;
	/***/ public String authGssApiAttempt;
	/***/ public String authGssApiExhausted;
	/***/ public String authGssApiFailure;
	/***/ public String authGssApiNotTried;
	/***/ public String authGssApiPartialSuccess;
	/***/ public String authPasswordAttempt;
	/***/ public String authPasswordChangeAttempt;
	/***/ public String authPasswordExhausted;
	/***/ public String authPasswordFailure;
	/***/ public String authPasswordNotTried;
	/***/ public String authPasswordPartialSuccess;
	/***/ public String authPubkeyAttempt;
	/***/ public String authPubkeyAttemptAgent;
	/***/ public String authPubkeyExhausted;
	/***/ public String authPubkeyFailure;
	/***/ public String authPubkeyNoKeys;
	/***/ public String authPubkeyPartialSuccess;
	/***/ public String closeListenerFailed;
	/***/ public String cannotReadPublicKey;
	/***/ public String configInvalidPath;
	/***/ public String configInvalidPattern;
	/***/ public String configInvalidPositive;
	/***/ public String configInvalidProxyJump;
	/***/ public String configNoKnownAlgorithms;
	/***/ public String configProxyJumpNotSsh;
	/***/ public String configProxyJumpWithPath;
	/***/ public String configUnknownAlgorithm;
	/***/ public String ftpCloseFailed;
	/***/ public String gssapiFailure;
	/***/ public String gssapiInitFailure;
	/***/ public String gssapiUnexpectedMechanism;
	/***/ public String gssapiUnexpectedMessage;
	/***/ public String identityFileCannotDecrypt;
	/***/ public String identityFileNoKey;
	/***/ public String identityFileMultipleKeys;
	/***/ public String identityFileNotFound;
	/***/ public String identityFileUnsupportedFormat;
	/***/ public String invalidSignatureAlgorithm;
	/***/ public String kexServerKeyInvalid;
	/***/ public String keyEncryptedMsg;
	/***/ public String keyEncryptedPrompt;
	/***/ public String keyEncryptedRetry;
	/***/ public String keyLoadFailed;
	/***/ public String knownHostsCouldNotUpdate;
	/***/ public String knownHostsFileLockedUpdate;
	/***/ public String knownHostsFileReadFailed;
	/***/ public String knownHostsInvalidLine;
	/***/ public String knownHostsInvalidPath;
	/***/ public String knownHostsKeyFingerprints;
	/***/ public String knownHostsModifiedKeyAcceptPrompt;
	/***/ public String knownHostsModifiedKeyDenyMsg;
	/***/ public String knownHostsModifiedKeyStorePrompt;
	/***/ public String knownHostsModifiedKeyWarning;
	/***/ public String knownHostsRevokedCertificateMsg;
	/***/ public String knownHostsRevokedKeyMsg;
	/***/ public String knownHostsUnknownKeyMsg;
	/***/ public String knownHostsUnknownKeyPrompt;
	/***/ public String knownHostsUnknownKeyType;
	/***/ public String knownHostsUserAskCreationMsg;
	/***/ public String knownHostsUserAskCreationPrompt;
	/***/ public String loginDenied;
	/***/ public String passwordPrompt;
	/***/ public String pkcs11Error;
	/***/ public String pkcs11FailedInstantiation;
	/***/ public String pkcs11GeneralMessage;
	/***/ public String pkcs11NoKeys;
	/***/ public String pkcs11NonExisting;
	/***/ public String pkcs11NotAbsolute;
	/***/ public String pkcs11Unsupported;
	/***/ public String pkcs11Warning;
	/***/ public String proxyCannotAuthenticate;
	/***/ public String proxyHttpFailure;
	/***/ public String proxyHttpInvalidUserName;
	/***/ public String proxyHttpUnexpectedReply;
	/***/ public String proxyHttpUnspecifiedFailureReason;
	/***/ public String proxyJumpAbort;
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
	/***/ public String pubkeyAuthAddKeyToAgentError;
	/***/ public String pubkeyAuthAddKeyToAgentQuestion;
	/***/ public String pubkeyAuthWrongCommand;
	/***/ public String pubkeyAuthWrongKey;
	/***/ public String pubkeyAuthWrongSignatureAlgorithm;
	/***/ public String serverIdNotReceived;
	/***/ public String serverIdTooLong;
	/***/ public String serverIdWithNul;
	/***/ public String sessionCloseFailed;
	/***/ public String sessionWithoutUsername;
	/***/ public String sshAgentEdDSAFormatError;
	/***/ public String sshAgentPayloadLengthError;
	/***/ public String sshAgentReplyLengthError;
	/***/ public String sshAgentReplyUnexpected;
	/***/ public String sshAgentShortReadBuffer;
	/***/ public String sshAgentUnknownKey;
	/***/ public String sshAgentWrongKeyLength;
	/***/ public String sshAgentWrongNumberOfKeys;
	/***/ public String sshClosingDown;
	/***/ public String sshCommandTimeout;
	/***/ public String sshProcessStillRunning;
	/***/ public String sshProxySessionCloseFailed;
	/***/ public String signAllowedSignersCertAuthorityError;
	/***/ public String signAllowedSignersEmptyIdentity;
	/***/ public String signAllowedSignersEmptyNamespaces;
	/***/ public String signAllowedSignersFormatError;
	/***/ public String signAllowedSignersInvalidDate;
	/***/ public String signAllowedSignersLineFormat;
	/***/ public String signAllowedSignersMultiple;
	/***/ public String signAllowedSignersNoIdentities;
	/***/ public String signAllowedSignersPublicKeyParsing;
	/***/ public String signAllowedSignersUnterminatedQuote;
	/***/ public String signCertAlgorithmMismatch;
	/***/ public String signCertAlgorithmUnknown;
	/***/ public String signCertificateExpired;
	/***/ public String signCertificateInvalid;
	/***/ public String signCertificateNotForName;
	/***/ public String signCertificateRevoked;
	/***/ public String signCertificateTooEarly;
	/***/ public String signCertificateWithoutPrincipals;
	/***/ public String signDefaultKeyEmpty;
	/***/ public String signDefaultKeyFailed;
	/***/ public String signDefaultKeyInterrupted;
	/***/ public String signGarbageAtEnd;
	/***/ public String signInvalidAlgorithm;
	/***/ public String signInvalidKeyDSA;
	/***/ public String signInvalidMagic;
	/***/ public String signInvalidNamespace;
	/***/ public String signInvalidSignature;
	/***/ public String signInvalidVersion;
	/***/ public String signKeyExpired;
	/***/ public String signKeyRevoked;
	/***/ public String signKeyTooEarly;
	/***/ public String signKrlBlobLeftover;
	/***/ public String signKrlBlobLengthInvalid;
	/***/ public String signKrlBlobLengthInvalidExpected;
	/***/ public String signKrlCaKeyLengthInvalid;
	/***/ public String signKrlCertificateLeftover;
	/***/ public String signKrlCertificateSubsectionLeftover;
	/***/ public String signKrlCertificateSubsectionLength;
	/***/ public String signKrlEmptyRange;
	/***/ public String signKrlInvalidBitSetLength;
	/***/ public String signKrlInvalidKeyIdLength;
	/***/ public String signKrlInvalidMagic;
	/***/ public String signKrlInvalidReservedLength;
	/***/ public String signKrlInvalidVersion;
	/***/ public String signKrlNoCertificateSubsection;
	/***/ public String signKrlSerialZero;
	/***/ public String signKrlShortRange;
	/***/ public String signKrlUnknownSection;
	/***/ public String signKrlUnknownSubsection;
	/***/ public String signLogFailure;
	/***/ public String signMismatchedSignatureAlgorithm;
	/***/ public String signNoAgent;
	/***/ public String signNoPrincipalMatched;
	/***/ public String signNoPublicKey;
	/***/ public String signNoSigningKey;
	/***/ public String signNotUserCertificate;
	/***/ public String signPublicKeyError;
	/***/ public String signSeeLog;
	/***/ public String signSignatureError;
	/***/ public String signStderr;
	/***/ public String signTooManyPrivateKeys;
	/***/ public String signTooManyPublicKeys;
	/***/ public String signUnknownHashAlgorithm;
	/***/ public String signUnknownSignatureAlgorithm;
	/***/ public String signWrongNamespace;
	/***/ public String unknownProxyProtocol;

}
