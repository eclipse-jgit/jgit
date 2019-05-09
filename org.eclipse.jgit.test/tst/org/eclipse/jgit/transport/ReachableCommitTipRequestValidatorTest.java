package org.eclipse.jgit.transport;

import org.eclipse.jgit.transport.UploadPack.RequestValidator;

/**
 * Client may ask for any commit reachable from any reference, even if that
 * reference wasn't advertised.
 */
public class ReachableCommitTipRequestValidatorTest
		extends RequestValidatorTestCase {

	@Override
	protected RequestValidator createValidator() {
		return new UploadPack.ReachableCommitTipRequestValidator();
	}

	@Override
	protected boolean isReachableCommitValid() {
		return true;
	}

	@Override
	protected boolean isUnreachableCommitValid() {
		return false;
	}

	@Override
	protected boolean isAdvertisedTipValid() {
		return true;
	}

	@Override
	protected boolean isReachableBlobValid_withBitmaps() {
		return true;
	}

	@Override
	protected boolean isReachableBlobValid_withoutBitmaps() {
		return false;
	}

	@Override
	protected boolean isUnreachableBlobValid() {
		return false;
	}

	@Override
	protected boolean isUnadvertisedTipCommitValid() {
		return true;
	}

}
