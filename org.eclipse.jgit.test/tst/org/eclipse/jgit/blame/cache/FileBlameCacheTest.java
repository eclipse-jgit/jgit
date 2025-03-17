package org.eclipse.jgit.blame.cache;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FileBlameCacheTest {
    Path cacheDir;

    @Before
    public void setUp() throws Exception {
        cacheDir = Files.createTempDirectory("tmp_");
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.delete(cacheDir.toFile(),
                FileUtils.RECURSIVE | FileUtils.SKIP_MISSING | FileUtils.RETRY);
    }

    @Test
    public void read_noEntry() throws IOException {
        FileBlameCache cache = new FileBlameCache(cacheDir.toFile());
        List<CacheRegion> result = cache.get(null, ObjectId.fromString("b53467c8d235766a0d2ba712020aca195889cee3"), "a/b");
        assertNull(result);
    }

    @Test
    public void write_read_samePath_results() throws IOException {
        ObjectId commitOne = ObjectId.fromString("e6a2d014374a0dfb1f97d2d05c668a21dc586175");
        ObjectId commitTwo = ObjectId.fromString("4957eeb00d8a560d4246201dab87a2db173d9f6b");
        ObjectId currentCommit = ObjectId.fromString("b53467c8d235766a0d2ba712020aca195889cee3");

        FileBlameCache cache = new FileBlameCache(cacheDir.toFile());
        FileBlameCache.FileBlameCacheWriter writer = cache.getWriter(currentCommit, "a/b");
        writer.add("a/b", commitOne, 0, 5);
        writer.add("a/b", commitOne, 10, 15 );
        writer.add("a/b.old", commitTwo, 5, 10);
        writer.flush();

        assertTrue(writer.getOutputFile().exists());
        List<CacheRegion> result = cache.get(null, currentCommit, "a/b");
        assertEquals(3, result.size());
    }

    @Test
    public void write_read_samePathWhiteSpace_results() throws IOException {
        ObjectId commitOne = ObjectId.fromString("e6a2d014374a0dfb1f97d2d05c668a21dc586175");
        ObjectId commitTwo = ObjectId.fromString("4957eeb00d8a560d4246201dab87a2db173d9f6b");
        ObjectId currentCommit = ObjectId.fromString("b53467c8d235766a0d2ba712020aca195889cee3");

        FileBlameCache cache = new FileBlameCache(cacheDir.toFile());
        FileBlameCache.FileBlameCacheWriter writer = cache.getWriter(currentCommit, "a/with spaces.txt");
        writer.add("a/b", commitOne, 0, 5);
        writer.add("a/b", commitOne, 10, 15 );
        writer.add("a/b.old", commitTwo, 5, 10);
        writer.flush();

        assertTrue(writer.getOutputFile().exists());
        List<CacheRegion> result = cache.get(null, currentCommit, "a/with spaces.txt");
        assertEquals(3, result.size());
    }

    @Test
    public void write_read_differentPath_noResult() throws IOException {
        ObjectId commitOne = ObjectId.fromString("e6a2d014374a0dfb1f97d2d05c668a21dc586175");
        ObjectId commitTwo = ObjectId.fromString("4957eeb00d8a560d4246201dab87a2db173d9f6b");
        ObjectId currentCommit = ObjectId.fromString("b53467c8d235766a0d2ba712020aca195889cee3");

        FileBlameCache cache = new FileBlameCache(cacheDir.toFile());
        FileBlameCache.FileBlameCacheWriter writer = cache.getWriter(currentCommit, "a/b");
        writer.add("a/b", commitOne, 0, 5);
        writer.add("a/b", commitOne, 10, 15 );
        writer.add("a/b.old", commitTwo, 5, 10);
        writer.flush();

        assertTrue(writer.getOutputFile().exists());
        List<CacheRegion> result = cache.get(null, currentCommit, "other/path");
        assertNull(result);
    }

    @Test
    public void write_read_differentCommit_noResult() throws IOException {
        ObjectId commitOne = ObjectId.fromString("e6a2d014374a0dfb1f97d2d05c668a21dc586175");
        ObjectId commitTwo = ObjectId.fromString("4957eeb00d8a560d4246201dab87a2db173d9f6b");
        ObjectId currentCommit = ObjectId.fromString("b53467c8d235766a0d2ba712020aca195889cee3");

        FileBlameCache cache = new FileBlameCache(cacheDir.toFile());
        FileBlameCache.FileBlameCacheWriter writer = cache.getWriter(currentCommit, "a/b");
        writer.add("a/b", commitOne, 0, 5);
        writer.add("a/b", commitOne, 10, 15 );
        writer.add("a/b.old", commitTwo, 5, 10);
        writer.flush();

        assertTrue(writer.getOutputFile().exists());
        // commitOne is not cached itself
        List<CacheRegion> result = cache.get(null, commitOne, "other/path");
        assertNull(result);
    }

    @Test
    public void write_flushNoData() {
        ObjectId currentCommit = ObjectId.fromString("b53467c8d235766a0d2ba712020aca195889cee3");

        FileBlameCache cache = new FileBlameCache(cacheDir.toFile());
        FileBlameCache.FileBlameCacheWriter writer = cache.getWriter(currentCommit, "a/b");
        writer.flush();
        assertFalse(writer.getOutputFile().exists());
    }
}
