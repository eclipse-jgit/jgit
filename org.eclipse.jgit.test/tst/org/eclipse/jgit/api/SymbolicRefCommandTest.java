/*
 * Copyright (C) 2012, Denis Bardadym <bardadymchik@gmail.com>
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
package org.eclipse.jgit.api;

import org.eclipse.jgit.lib.*;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 *
 * @author denis.bardadym
 */
public class SymbolicRefCommandTest extends RepositoryTestCase {

    private Git git;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        git = new Git(db);
        // commit something
        writeTrashFile("Test.txt", "Hello world");
        git.add().addFilepattern("Test.txt").call();
        git.commit().setMessage("Initial commit").call();

        // create a master branch
        git.branchCreate().setName("test").call();
    }

    @Test
    public void testSimpleSetHeadBranch() throws Exception {
        String longTestBranchName = "refs/heads/test";

        git.symbolicRef().setName(Constants.HEAD).setNewRef(longTestBranchName).call();

        assertEquals(git.symbolicRef().setName(Constants.HEAD).call(), longTestBranchName);
    }

    @Test
    public void testNewSymbolicRefCreationAndUpdate() throws Exception {
        String newRef = "refs/other/ref";
        String ref = "SOME_REF";

        git.symbolicRef().setName(ref).setNewRef(newRef).call();

        assertEquals(git.symbolicRef().setName(ref).call(), newRef);

        String newRefOther = "refs/other-other/ref";

        git.symbolicRef().setName(ref).setNewRef(newRefOther).call();

        assertEquals(git.symbolicRef().setName(ref).call(), newRefOther);
    }

    @Test
    public void testNotSymbolicRefRead() throws Exception {

        try {
            git.symbolicRef().setName("refs/heads/test").call();
            fail("Must throw exception on not a symbolic ref");
        } catch (Exception e) {
        }

    }
    
    @Test
    public void testNotSymbolicRefUpdate() throws Exception {

        try {
            git.symbolicRef().setName("refs/heads/test").setNewRef("refs/other/ref").call();
            fail("Must throw exception on not a symbolic ref");
        } catch (Exception e) {
        }

    }
}
