package org.eclipse.jgit.internal.storage.dfs;

import java.io.IOException;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_PACK_SECTION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.security.MessageDigest;
import java.util.zip.Deflater;
import org.eclipse.jgit.internal.storage.dfs.DfsObjDatabase.PackList;
import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.junit.Before;
import org.junit.Test;

public class DfsPackParserTest {
  private InMemoryRepository repo;


  @Before
  public void setUp() throws Exception {
    DfsRepositoryDescription desc = new DfsRepositoryDescription("test");
    repo = new InMemoryRepository(desc);
    repo.getConfig().setInt(CONFIG_PACK_SECTION, null,
        CONFIG_KEY_MIN_BYTES_OBJ_SIZE_INDEX, 0);
  }

  @Test
  public void parse_writeObjSizeIdx() throws IOException {
    TemporaryBuffer.Heap pack = new TemporaryBuffer.Heap(1024);

    // Sha1 of the blob "a"
    ObjectId blobA = ObjectId.fromString("2e65efe2a145dda7ee51d1741299f848e5bf752e");

    packHeader(pack, 2);
    pack.write((Constants.OBJ_BLOB) << 4 | 1);
    deflate(pack, new byte[] { 'a' });

    pack.write((Constants.OBJ_REF_DELTA) << 4 | 4);
    blobA.copyRawTo(pack);
    deflate(pack, new byte[] { 0x1, 0x1, 0x1, 'b' });
    digest(pack);

    try (ObjectInserter ins = repo.newObjectInserter()) {
      PackParser parser = ins.newPackParser(pack.openInputStream());
      parser.parse(NullProgressMonitor.INSTANCE, NullProgressMonitor.INSTANCE);
      ins.flush();
    }

    DfsReader reader = repo.getObjectDatabase().newReader();
    PackList packList = repo.getObjectDatabase().getPackList();
    assertEquals(1, packList.packs.length);
    assertEquals(1, packList.packs[0].getIndexedObjectSize(reader, blobA));
  }


  private static void packHeader(TemporaryBuffer.Heap tinyPack, int cnt)
      throws IOException {
    final byte[] hdr = new byte[8];
    NB.encodeInt32(hdr, 0, 2);
    NB.encodeInt32(hdr, 4, cnt);

    tinyPack.write(Constants.PACK_SIGNATURE);
    tinyPack.write(hdr, 0, 8);
  }


  private static void deflate(TemporaryBuffer.Heap tinyPack,
      final byte[] content)
      throws IOException {
    final Deflater deflater = new Deflater();
    final byte[] buf = new byte[128];
    deflater.setInput(content, 0, content.length);
    deflater.finish();
    do {
      final int n = deflater.deflate(buf, 0, buf.length);
      if (n > 0)
        tinyPack.write(buf, 0, n);
    } while (!deflater.finished());
  }

  private static void digest(TemporaryBuffer.Heap buf) throws IOException {
    MessageDigest md = Constants.newMessageDigest();
    md.update(buf.toByteArray());
    buf.write(md.digest());
  }
}
