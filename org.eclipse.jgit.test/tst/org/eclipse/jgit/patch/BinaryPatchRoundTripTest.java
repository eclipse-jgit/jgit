package org.eclipse.jgit.patch;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.ApplyCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.DiffCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BinaryPatchRoundTripTest
{
    private File testDir = null;

    @Before
    public void setUp() throws Exception
    {
        testDir = Files.createTempDirectory("jgit_binary_patch").toFile();
    }

    @Test
    public void testLiteralBinaryPatch() throws Exception
    {
        File patchFile = new File(testDir, "binary.patch");
        File repoDir = new File(testDir, "repo-dir");
        repoDir.mkdirs();
        File deployDir = new File(testDir, "deploy-dir");
        deployDir.mkdirs();

        InitCommand initCommand = Git.init();
        initCommand.setDirectory(repoDir);
        Git git = initCommand.call();

        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage("Initial commit");
        commitCommand.call();

        TagCommand tagCommand = git.tag();
        tagCommand.setName("CLEAN_REPO");
        tagCommand.call();

        File testFile = getResourceFileByName("Git-logo.bmp");
        Files.copy(testFile.toPath(), new File(repoDir, testFile.getName()).toPath());

        AddCommand addCommand = git.add();
        addCommand.addFilepattern("Git-logo.bmp");
        addCommand.call();

        commitCommand = git.commit();
        commitCommand.setMessage("Second commit");
        commitCommand.call();

        tagCommand = git.tag();
        tagCommand.setName("FILE_COMMITED");
        tagCommand.call();

        // Create binary patch
		DiffCommand diffCommand = git.diff();
        diffCommand.setOldName("CLEAN_REPO");
        diffCommand.setNewName("FILE_COMMITED");
        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(patchFile)))
        {
            diffCommand.setOutputStream(outputStream);
            diffCommand.call();
        }

        // Apply binary patch
        initCommand = Git.init();
        initCommand.setDirectory(deployDir);
        git = initCommand.call();

		ApplyCommand applyCommand = git.apply();

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(patchFile)))
        {
            applyCommand.setPatch(inputStream);
            applyCommand.call();
        }

        File generatedFie = new File(deployDir, "Git-logo.bmp");

        Assert.assertTrue(generatedFie.exists());
        Assert.assertArrayEquals(Files.readAllBytes(getResourceFileByName("Git-logo.bmp").toPath()), Files.readAllBytes(generatedFie.toPath()));
    }

    @Test
    public void testDeltaBinaryPatch() throws Exception
    {
        File patchFile = new File(testDir, "binary.patch");
        File repoDir = new File(testDir, "repo-dir");
        repoDir.mkdirs();
        File deployDir = new File(testDir, "deploy-dir");
        deployDir.mkdirs();

        InitCommand initCommand = Git.init();
        initCommand.setDirectory(repoDir);
        Git git = initCommand.call();

        File testFile = getResourceFileByName("Git-logo.bmp");
        Files.copy(testFile.toPath(), new File(repoDir, testFile.getName()).toPath());

        AddCommand addCommand = git.add();
        addCommand.addFilepattern("Git-logo.bmp");
        addCommand.call();

        CommitCommand commitCommand = git.commit();
        commitCommand.setMessage("Initial commit");
        commitCommand.call();

        TagCommand tagCommand = git.tag();
        tagCommand.setName("VersionA");
        tagCommand.call();

        File modifiedTestFile = getResourceFileByName("Modified-Git-logo.bmp");
        Files.copy(modifiedTestFile.toPath(), new File(repoDir, "Git-logo.bmp").toPath(), StandardCopyOption.REPLACE_EXISTING);

        addCommand = git.add();
        addCommand.addFilepattern("Git-logo.bmp");
        addCommand.call();

        commitCommand = git.commit();
        commitCommand.setMessage("Second commit");
        commitCommand.call();

        tagCommand = git.tag();
        tagCommand.setName("VersionB");
        tagCommand.call();

        // Create binary patch
		DiffCommand diffCommand = git.diff();
        diffCommand.setOldName("VersionA");
        diffCommand.setNewName("VersionB");
        try (OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(patchFile)))
        {
            diffCommand.setOutputStream(outputStream);
            diffCommand.call();
        }

        Files.copy(testFile.toPath(), new File(deployDir, testFile.getName()).toPath());

        // Apply binary patch
        initCommand = Git.init();
        initCommand.setDirectory(deployDir);
        git = initCommand.call();

		ApplyCommand applyCommand = git.apply();

        try (InputStream inputStream = new BufferedInputStream(new FileInputStream(patchFile)))
        {
            applyCommand.setPatch(inputStream);
            applyCommand.call();
        }

        File generatedFie = new File(deployDir, "Git-logo.bmp");

        Assert.assertTrue(generatedFie.exists());
        Assert.assertArrayEquals(Files.readAllBytes(getResourceFileByName("Modified-Git-logo.bmp").toPath()), Files.readAllBytes(generatedFie.toPath()));
    }

    @After
    public void shutDown() throws Exception
    {
        if (testDir != null)
        {
			FileUtils.delete(testDir, FileUtils.RECURSIVE);
        }
    }

	private File getResourceFileByName(String fileName) throws Exception
    {
		URI uri = getClass().getClassLoader()
				.getResource("org/eclipse/jgit/patch/" + fileName).toURI();
        return new File(uri.getPath());
    }

}
