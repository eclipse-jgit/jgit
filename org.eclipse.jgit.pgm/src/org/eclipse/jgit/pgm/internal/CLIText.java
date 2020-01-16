/*
 * Copyright (C) 2010, 2013 Sasa Zivkov <sasa.zivkov@sap.com>
 * Copyright (C) 2013, Obeo and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.internal;

import java.text.MessageFormat;
import java.util.Locale;

import org.eclipse.jgit.nls.NLS;
import org.eclipse.jgit.nls.TranslationBundle;
import org.kohsuke.args4j.Localizable;

/**
 * Translation bundle for JGit command line interface
 */
public class CLIText extends TranslationBundle {
	/**
	 * Formats text strings using {@code Localizable}.
	 *
	 */
	public static class Format implements Localizable {
		final String text;

		Format(String text) {
			this.text = text;
		}

		@Override
		public String formatWithLocale(Locale locale, Object... args) {
			// we don't care about Locale for now
			return format(args);
		}

		@Override
		public String format(Object... args) {
			return MessageFormat.format(text, args);
		}
	}

	/**
	 * Format text
	 *
	 * @param text
	 *            the text to format.
	 * @return a new Format instance.
	 */
	public static Format format(String text) {
		return new Format(text);
	}

	/**
	 * Get an instance of this translation bundle
	 *
	 * @return an instance of this translation bundle
	 */
	public static CLIText get() {
		return NLS.getBundleFor(CLIText.class);
	}

	/**
	 * Format the given line for using the format defined by {@link #lineFormat}
	 * ("# " by default).
	 *
	 * @param line
	 *            the line to format
	 * @return the formatted line
	 */
	public static String formatLine(String line) {
		return MessageFormat.format(get().lineFormat, line);
	}

	/**
	 * Format the given argument as fatal error using the format defined by
	 * {@link #fatalError} ("fatal: " by default).
	 *
	 * @param message
	 *            the message to format
	 * @return the formatted line
	 */
	public static String fatalError(String message) {
		return MessageFormat.format(get().fatalError, message);
	}

	// @formatter:off
	/***/ public String alreadyOnBranch;
	/***/ public String alreadyUpToDate;
	/***/ public String answerNo;
	/***/ public String answerYes;
	/***/ public String authorInfo;
	/***/ public String averageMSPerRead;
	/***/ public String branchAlreadyExists;
	/***/ public String branchCreatedFrom;
	/***/ public String branchDetachedHEAD;
	/***/ public String branchIsNotAnAncestorOfYourCurrentHEAD;
	/***/ public String branchNameRequired;
	/***/ public String branchNotFound;
	/***/ public String cacheTreePathInfo;
	/***/ public String configFileNotFound;
	/***/ public String cannotBeRenamed;
	/***/ public String cannotCombineSquashWithNoff;
	/***/ public String cannotCreateCommand;
	/***/ public String cannotCreateOutputStream;
	/***/ public String cannotDeatchHEAD;
	/***/ public String cannotDeleteFile;
	/***/ public String cannotDeleteTheBranchWhichYouAreCurrentlyOn;
	/***/ public String cannotGuessLocalNameFrom;
	/***/ public String cannotLock;
	/***/ public String cannotReadBecause;
	/***/ public String cannotReadPackageInformation;
	/***/ public String cannotRenameDetachedHEAD;
	/***/ public String cannotResolve;
	/***/ public String cannotSetupConsole;
	/***/ public String cannotUseObjectsWithGlog;
	/***/ public String cantFindGitDirectory;
	/***/ public String cantWrite;
	/***/ public String changesNotStagedForCommit;
	/***/ public String changesToBeCommitted;
	/***/ public String checkingOut;
	/***/ public String checkoutConflict;
	/***/ public String checkoutConflictPathLine;
	/***/ public String cleanRequireForce;
	/***/ public String clonedEmptyRepository;
	/***/ public String cloningInto;
	/***/ public String commitLabel;
	/***/ public String configOnlyListOptionSupported;
	/***/ public String conflictingUsageOf_git_dir_andArguments;
	/***/ public String couldNotCreateBranch;
	/***/ public String dateInfo;
	/***/ public String deletedBranch;
	/***/ public String deletedRemoteBranch;
	/***/ public String doesNotExist;
	/***/ public String dontOverwriteLocalChanges;
	/***/ public String everythingUpToDate;
	/***/ public String expectedNumberOfbytes;
	/***/ public String exporting;
	/***/ public String failedToCommitIndex;
	/***/ public String failedToLockIndex;
	/***/ public String fatalError;
	/***/ public String fatalThisProgramWillDestroyTheRepository;
	/***/ public String fetchingSubmodule;
	/***/ public String fileIsRequired;
	/***/ public String ffNotPossibleAborting;
	/***/ public String forcedUpdate;
	/***/ public String fromURI;
	/***/ public String initializedEmptyGitRepositoryIn;
	/***/ public String invalidHttpProxyOnlyHttpSupported;
	/***/ public String invalidRecurseSubmodulesMode;
	/***/ public String invalidUntrackedFilesMode;
	/***/ public String jgitVersion;
	/***/ public String lfsNoAccessKey;
	/***/ public String lfsNoSecretKey;
	/***/ public String lfsProtocolUrl;
	/***/ public String lfsStoreDirectory;
	/***/ public String lfsStoreUrl;
	/***/ public String lfsUnknownStoreType;
	/***/ public String lineFormat;
	/***/ public String listeningOn;
	/***/ public String mergeCheckoutConflict;
	/***/ public String mergeConflict;
	/***/ public String mergeFailed;
	/***/ public String mergeCheckoutFailed;
	/***/ public String mergeMadeBy;
	/***/ public String mergedSquashed;
	/***/ public String mergeWentWellStoppedBeforeCommitting;
	/***/ public String metaVar_KEY;
	/***/ public String metaVar_archiveFormat;
	/***/ public String metaVar_archivePrefix;
	/***/ public String metaVar_arg;
	/***/ public String metaVar_author;
	/***/ public String metaVar_bucket;
	/***/ public String metaVar_command;
	/***/ public String metaVar_commandDetail;
	/***/ public String metaVar_commitOrTag;
	/***/ public String metaVar_commitPaths;
	/***/ public String metaVar_commitish;
	/***/ public String metaVar_configFile;
	/***/ public String metaVar_connProp;
	/***/ public String metaVar_diffAlg;
	/***/ public String metaVar_directory;
	/***/ public String metaVar_file;
	/***/ public String metaVar_filepattern;
	/***/ public String metaVar_gitDir;
	/***/ public String metaVar_hostName;
	/***/ public String metaVar_lfsStorage;
	/***/ public String metaVar_linesOfContext;
	/***/ public String metaVar_message;
	/***/ public String metaVar_n;
	/***/ public String metaVar_name;
	/***/ public String metaVar_object;
	/***/ public String metaVar_op;
	/***/ public String metaVar_pass;
	/***/ public String metaVar_path;
	/***/ public String metaVar_paths;
	/***/ public String metaVar_pattern;
	/***/ public String metaVar_port;
	/***/ public String metaVar_ref;
	/***/ public String metaVar_refs;
	/***/ public String metaVar_refspec;
	/***/ public String metaVar_remoteName;
	/***/ public String metaVar_s3Bucket;
	/***/ public String metaVar_s3Region;
	/***/ public String metaVar_s3StorageClass;
	/***/ public String metaVar_seconds;
	/***/ public String metaVar_service;
	/***/ public String metaVar_treeish;
	/***/ public String metaVar_uriish;
	/***/ public String metaVar_url;
	/***/ public String metaVar_user;
	/***/ public String metaVar_values;
	/***/ public String metaVar_version;
	/***/ public String mostCommonlyUsedCommandsAre;
	/***/ public String needApprovalToDestroyCurrentRepository;
	/***/ public String needSingleRevision;
	/***/ public String noGitRepositoryConfigured;
	/***/ public String noNamesFound;
	/***/ public String noSuchFile;
	/***/ public String noSuchPathInRef;
	/***/ public String noSuchRef;
	/***/ public String noTREESectionInIndex;
	/***/ public String nonFastForward;
	/***/ public String noSystemConsoleAvailable;
	/***/ public String notABranch;
	/***/ public String notACommit;
	/***/ public String notAGitRepository;
	/***/ public String notAJgitCommand;
	/***/ public String notARevision;
	/***/ public String notATree;
	/***/ public String notAValidRefName;
	/***/ public String notAValidCommitName;
	/***/ public String notAnIndexFile;
	/***/ public String notAnObject;
	/***/ public String notFound;
	/***/ public String notOnAnyBranch;
	/***/ public String noteObjectTooLargeToPrint;
	/***/ public String nothingToSquash;
	/***/ public String onBranchToBeBorn;
	/***/ public String onBranch;
	/***/ public String onlyOneMetaVarExpectedIn;
	/***/ public String onlyOneCommitOptionAllowed;
	/***/ public String password;
	/***/ public String pathspecDidNotMatch;
	/***/ public String pushTo;
	/***/ public String pathsRequired;
	/***/ public String refDoesNotExistOrNoCommit;
	/***/ public String remoteMessage;
	/***/ public String remoteRefObjectChangedIsNotExpectedOne;
	/***/ public String remoteSideDoesNotSupportDeletingRefs;
	/***/ public String removing;
	/***/ public String repaint;
	/***/ public String resetNoMode;
	/***/ public String s3InvalidBucket;
	/***/ public String serviceNotSupported;
	/***/ public String skippingObject;
	/***/ public String statusFileListFormat;
	/***/ public String statusFileListFormatWithPrefix;
	/***/ public String statusFileListFormatUnmerged;
	/***/ public String statusModified;
	/***/ public String statusNewFile;
	/***/ public String statusRemoved;
	/***/ public String statusBothDeleted;
	/***/ public String statusAddedByUs;
	/***/ public String statusDeletedByThem;
	/***/ public String statusAddedByThem;
	/***/ public String statusDeletedByUs;
	/***/ public String statusBothAdded;
	/***/ public String statusBothModified;
	/***/ public String submoduleRegistered;
	/***/ public String switchedToNewBranch;
	/***/ public String switchedToBranch;
	/***/ public String tagAlreadyExists;
	/***/ public String tagLabel;
	/***/ public String tagNotFound;
	/***/ public String taggerInfo;
	/***/ public String timeInMilliSeconds;
	/***/ public String tooManyRefsGiven;
	/***/ public String treeIsRequired;
	/***/ public char[] unknownIoErrorStdout;
	/***/ public String unknownMergeStrategy;
	/***/ public String unknownSubcommand;
	/***/ public String unmergedPaths;
	/***/ public String unsupportedOperation;
	/***/ public String untrackedFiles;
	/***/ public String updating;
	/***/ public String usernameFor;
}
