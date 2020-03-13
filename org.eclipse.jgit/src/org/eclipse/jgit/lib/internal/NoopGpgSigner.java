/*
 * Copyright 2020 Eclipse JGit Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.eclipse.jgit.lib.internal;

import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.GpgSigner;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 *
 * GPG Signer doing nothing.
 */
public class NoopGpgSigner implements GpgSigner{

    @Override
    public void sign(CommitBuilder commit, String gpgSigningKey, PersonIdent committer, CredentialsProvider credentialsProvider) throws CanceledException {
    }

    @Override
    public boolean canLocateSigningKey(String gpgSigningKey, PersonIdent committer, CredentialsProvider credentialsProvider) throws CanceledException {
	return true;
    }
    
}
