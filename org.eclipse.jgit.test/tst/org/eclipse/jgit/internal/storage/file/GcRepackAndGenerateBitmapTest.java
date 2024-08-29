/*
 * Copyright (C) 2024, Antonio Barone <syntonyze@gmail.com>
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
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GcRepackAndGenerateBitmapTest extends GcTestCase {

    @Test
    public void shouldGenerateBitmapWhenMissing() throws Exception {
        pushCommitToRef("refs/heads/master");
        stats = gc.getStatistics();
        assertEquals(1, stats.numberOfPackFiles);
        assertEquals(0, stats.numberOfBitmaps);

        gc.repackAndGenerateBitmap();

        stats = gc.getStatistics();
        assertEquals(1, stats.numberOfPackFiles);
        assertEquals(1, stats.numberOfBitmaps);
    }

    @Test
    public void shouldGenerateBitmapForHeadsOnly() throws Exception {
        pushCommitToRef("refs/heads/master");
        gc.repackAndGenerateBitmap();
        Optional<Pack> headPack = repo.getObjectDatabase().getPacks().stream().findFirst();
        assertTrue(headPack.isPresent());
        pushCommitToRef("refs/non-heads/test-branch");

        gc.repackAndGenerateBitmap();

        stats = gc.getStatistics();
        assertEquals(2, stats.numberOfPackFiles);
        assertEquals(1, stats.numberOfBitmaps);
        Optional<Pack> packWithBitmap = repo.getObjectDatabase().getPacks().stream().filter(Pack::hasBitmapIndex).findFirst();
        assertTrue(packWithBitmap.isPresent());
        assertEquals(headPack.get().getPackChecksum(), packWithBitmap.get().getPackChecksum());
    }


    private void pushCommitToRef(String ref) throws IOException, GitAPIException {

        String repoUri = repo.getDirectory().getAbsolutePath();
        Git git = Git.cloneRepository().setURI(repoUri).setDirectory(createTempDirectory(UUID.randomUUID().toString())).call();

        git.commit()
                .setAuthor(author)
                .setMessage("Test commit message")
                .call();
        git.push().setForce(true).setRefSpecs(new RefSpec(HEAD + ":" + ref))
                .setRemote(repoUri).call();
    }
}
