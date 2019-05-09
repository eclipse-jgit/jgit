package org.eclipse.jgit.transport;

import org.eclipse.jgit.transport.UploadPack.RequestValidator;

/**
 * Client may ask for objects that are the tip of any reference, even if not
 * advertised.
 */
public class TipRequestValidatorTest extends RequestValidatorTestCase {

	@Override
	protected RequestValidator createValidator() {
		return new UploadPack.TipRequestValidator();
	}

	@Override
	protected boolean isReachableCommitValid() {
		return false;
	}

	@Override
	protected boolean isUnreachableCommitValid() {
		return false;
	}

	@Override
	protected boolean isReachableBlobValid_withBitmaps() {
		return false;
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
	protected boolean isAdvertisedTipValid() {
		return true;
	}

	@Override
	protected boolean isUnadvertisedTipCommitValid() {
		return true;
	}

}
