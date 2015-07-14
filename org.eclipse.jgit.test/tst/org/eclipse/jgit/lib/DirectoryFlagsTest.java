package org.eclipse.jgit.lib;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.junit.Test;

import java.io.File;
import java.io.PrintWriter;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DirectoryFlagsTest extends LocalDiskRepositoryTestCase {
    private Repository createRepoWithNestedRepo() throws Exception {
        DirectoryFlags flags = new DirectoryFlags();
        flags.setNoGitLinks(true);

        File gitdir = createUniqueTestGitDir(false);
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.setGitDir(gitdir);
        builder.setDirectoryFlags(flags);
        Repository db = builder.build();
        db.create();
        // TODO: add cleanup logic?  This is based on `createRepository` in
        //  the parent class
        //toClose.add(db);


        File nestedRepoPath = new File(db.getWorkTree(), "sub/nested");

        FileRepositoryBuilder nestedBuilder = new FileRepositoryBuilder();
        nestedBuilder.setWorkTree(nestedRepoPath);
        nestedBuilder.build().create();


        File file = new File(nestedRepoPath, "a.txt");
        FileUtils.createNewFile(file);
        PrintWriter writer = new PrintWriter(file);
        writer.print("content");
        writer.close();

        return db;
    }
    @Test
    public void testAddFileWithNoGitLinks() throws Exception {
        Repository db = createRepoWithNestedRepo();

        System.out.println("Calling add command");
        Git git = new Git(db);
        git.add().addFilepattern("sub/nested/a.txt").call();
        System.out.println("Back from calling add command");

        assertEquals(
                "[sub/nested/a.txt, mode:100644, content:content]",
                indexState(db, CONTENT));
    }

    @Test
    public void testStatusWithNoGitLinks() throws Exception {
        Repository db = createRepoWithNestedRepo();

        Git git = new Git(db);

        Status initialStatus = git.status().call();
        assertTrue(initialStatus.getAdded().isEmpty());

        git.add().addFilepattern("sub/nested/a.txt").call();

        Status status = git.status().call();
        assertTrue(status.getAdded().contains("sub/nested/a.txt"));
    }

    @Test
    public void testCommitFileWithNoGitLinks() throws Exception {
        Repository db = createRepoWithNestedRepo();

        Git git = new Git(db);

        git.add().addFilepattern("sub/nested/a.txt").call();
        RevCommit commit = git.commit().setAuthor("false", "false@false.com").setMessage("test commit").call();

        // Calculate the diff for the initial commit to ensure the
        // subrepo's file was committed correctly
        DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE);
        df.setRepository(db);
        df.setDiffComparator(RawTextComparator.DEFAULT);
        // Parent commit is null since this is the initial commit
        DiffEntry diff = df.scan(null, commit.getTree()).get(0);

        assertEquals("sub/nested/a.txt", diff.getNewPath());
        assertEquals("100644", diff.getNewMode().toString());
        assertEquals("ADD", diff.getChangeType().name());
    }
}
