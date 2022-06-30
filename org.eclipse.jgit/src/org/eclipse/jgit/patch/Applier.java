package org.eclipse.jgit.patch;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.InflaterInputStream;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout.CheckoutMetadata;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.BinaryBlobException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.FileHeader.PatchType;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.NotIgnoredFilter;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.RepoIOHandler;
import org.eclipse.jgit.util.RepoIOHandler.StreamLoader;
import org.eclipse.jgit.util.InCoreSupport;
import org.eclipse.jgit.util.LfsFactory;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;
import org.eclipse.jgit.util.io.BinaryDeltaInputStream;
import org.eclipse.jgit.util.io.BinaryHunkInputStream;
import org.eclipse.jgit.util.sha1.SHA1;

public class Applier implements InCoreSupport {

  private final boolean inCore;
  private RepoIOHandler ioHandler;
  @Nullable
  protected final RevTree tip;

  @Nullable
  protected final Repository repo;

  protected final ObjectInserter inserter;
  protected ObjectReader reader;

  // DO NOT SUBMIT - check what should be the attributes.
  protected Attributes attributes = new Attributes();

  public Applier(Repository local) {
    this.inCore = false;
    if (local == null) {
      throw new NullPointerException(JGitText.get().repositoryIsRequired);
    }
    repo = local;
    inserter = local.newObjectInserter();
    reader = inserter.newReader();
    this.tip = null; // Not needed
  }

  public Applier(Repository remote, RevTree tip, ObjectInserter oi) {
    repo = remote;
    inCore = true;
    this.tip = tip;
    inserter = oi;
    reader = oi.newReader();
  }

  public static class ApplyPatchResult {

    public ObjectId appliedTree;
    public List<File> appliedFiles;
  }

  /**
   * Set patch
   *
   * @param patchInput the patch to apply.
   * @return the new tree ID if index was updated.
   */
  public ApplyPatchResult applyPatch(InputStream patchInput)
      throws PatchFormatException, PatchApplyException {
    ApplyPatchResult result = new ApplyPatchResult();
    result.appliedFiles = new ArrayList<>();
    try {
      final org.eclipse.jgit.patch.Patch p = new org.eclipse.jgit.patch.Patch();
      p.parse(patchInput);
      if (!p.getErrors().isEmpty()) {
        throw new PatchFormatException(p.getErrors());
      }
      ioHandler = InCoreSupport.initRepoIOHandler(repo, inCore, inserter, reader, attributes);
      DirCache cache = ioHandler.getLockedDirCache();
      for (org.eclipse.jgit.patch.FileHeader fh : p.getFiles()) {
        ChangeType type = fh.getChangeType();
        File f = null;
        switch (type) {
          case ADD:
            f = inCore ? null : getFile(fh.getNewPath(), true);
            apply(fh.getNewPath(), cache, f, fh);
            break;
          case MODIFY:
            f = inCore ? null : getFile(fh.getOldPath(), false);
            apply(fh.getOldPath(), cache, f, fh);
            break;
          case DELETE:
            f = getFile(fh.getOldPath(), false);
            ioHandler.deleteFile(fh.getOldPath(), f, attributes);
            break;
          case RENAME:
            // DO NOT SUBMIT - should handle properly for inCore
            f = inCore ? null : getFile(fh.getOldPath(), false);
            File dest = getFile(fh.getNewPath(), false);
            try {
              FileUtils.mkdirs(dest.getParentFile(), true);
              FileUtils.rename(f, dest, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
              throw new PatchApplyException(
                  MessageFormat.format(JGitText.get().renameFileFailed, f, dest), e);
            }
            apply(fh.getOldPath(), cache, dest, fh);
            break;
          case COPY:
            f = inCore ? null : getFile(fh.getOldPath(), false);
            File target = getFile(fh.getNewPath(), false);
            FileUtils.mkdirs(target.getParentFile(), true);
            Files.copy(f.toPath(), target.toPath());
            apply(fh.getOldPath(), cache, target, fh);
        }
        if (f != null) {
          result.appliedFiles.add(f);
        }
      }
      patchInput.close();
      result.appliedTree = ioHandler.writeChangesAndCleanUp();
    } catch (IOException e) {
      throw new PatchApplyException(
          MessageFormat.format(JGitText.get().patchApplyException, e.getMessage()), e);
    }
    return result;
  }

  private File getFile(String path, boolean create) throws PatchApplyException {
    File f = new File(repo.getWorkTree(), path);
    if (create) {
      try {
        File parent = f.getParentFile();
        FileUtils.mkdirs(parent, true);
        FileUtils.createNewFile(f);
      } catch (IOException e) {
        throw new PatchApplyException(MessageFormat.format(JGitText.get().createNewFileFailed, f),
            e);
      }
    }
    return f;
  }

  private void apply(String path, DirCache cache, File f, org.eclipse.jgit.patch.FileHeader fh)
      throws PatchApplyException {
    if (PatchType.BINARY.equals(fh.getPatchType())) {
      return;
    }
    try {
      TreeWalk walk = inCore ? TreeWalk.forPath(repo, path, tip) : new TreeWalk(repo);
      // Use a TreeWalk with a DirCacheIterator to pick up the correct
      // clean/smudge filters. CR-LF handling is completely determined by
      // whether the file or the patch have CR-LF line endings.
      boolean convertCrLf = false;
      int fileIdx = -1;
      if (!inCore) {
        int cacheIdx = walk.addTree(new DirCacheIterator(cache));
        FileTreeIterator files = new FileTreeIterator(repo);
        convertCrLf = needsCrLfConversion(f, fh);
        fileIdx = walk.addTree(files);
        walk.setFilter(AndTreeFilter.create(PathFilterGroup.createFromStrings(path),
            new NotIgnoredFilter(fileIdx)));
        walk.setOperationType(OperationType.CHECKIN_OP);
        walk.setRecursive(true);
        files.setDirCacheIterator(walk, cacheIdx);
      }

      boolean loaded = false;
      EolStreamType streamType = EolStreamType.DIRECT;
      String smudgeFilterCommand = null;
      RepoIOHandler.StreamSupplier fileStreamSupplier = null;
      ObjectId fileId = ObjectId.zeroId();
      if (inCore && walk != null) {
        // For new files, @TreeWalk.forPath is null.
        fileId = walk != null ? walk.getObjectId(0) : ObjectId.zeroId();
        ObjectLoader loader = LfsFactory.getInstance()
            .applySmudgeFilter(repo, reader.open(fileId, OBJ_BLOB), null);
        fileStreamSupplier = () -> loader.openStream();
        loaded = loader != null && loader.getSize() != 0;
      } else if (walk != null) {
        if (walk.next()) {
          // If the file on disk has no newline characters, convertCrLf
          // will be false. In that case we want to honor the normal git
          // settings.
          streamType = convertCrLf ? EolStreamType.TEXT_CRLF
              : walk.getEolStreamType(OperationType.CHECKOUT_OP);
          smudgeFilterCommand = walk.getFilterCommand(Constants.ATTR_FILTER_TYPE_SMUDGE);
          FileTreeIterator file = walk.getTree(fileIdx, FileTreeIterator.class);
          if (file != null) {
            fileId = file.getEntryObjectId();
            fileStreamSupplier = file::openEntryStream;
            loaded = true;
          }
        }
      }
      if (!loaded) {
        fileStreamSupplier = () -> new FileInputStream(f);
        if (convertCrLf) {
          streamType = EolStreamType.TEXT_CRLF;
        }
      }

      FileMode fileMode = fh.getNewMode() != null ? fh.getNewMode() : FileMode.MISSING;
      StreamLoader resultStreamLoader;
      boolean safeWrite;
      SHA1 hash = null;
      if (PatchType.GIT_BINARY.equals(fh.getPatchType())) {
        hash = SHA1.newInstance();
        resultStreamLoader = applyBinary(path, f, fh, fileStreamSupplier, fileId, hash);
        safeWrite = true;
      } else {
        String filterCommand = walk.getFilterCommand(Constants.ATTR_FILTER_TYPE_CLEAN);
        RawText raw = ioHandler.getRawText(f, fileStreamSupplier, fileId, path, loaded,
            filterCommand, convertCrLf);
        resultStreamLoader = applyText(raw, fh);
        safeWrite = false;
        if (!inCore) {
          repo.getFS().setExecute(f, fileMode == FileMode.EXECUTABLE_FILE);
        }
      }

      CheckoutMetadata checkOutMetadata = new CheckoutMetadata(streamType, smudgeFilterCommand);
      if (resultStreamLoader != null) {
        ioHandler.updateFileWithContent(resultStreamLoader, checkOutMetadata, fh.getNewPath(), f,
            safeWrite, fileMode);
      }
      if (hash != null) {
        if (!fh.getNewId().toObjectId().equals(hash.toObjectId())) {
          throw new PatchApplyException(
              MessageFormat.format(JGitText.get().applyBinaryResultOidWrong, path));
        }
      }
    } catch (IOException | UnsupportedOperationException e) {
      throw new PatchApplyException(
          MessageFormat.format(JGitText.get().patchApplyException, e.getMessage()), e);
    }
  }

  private boolean needsCrLfConversion(File f, org.eclipse.jgit.patch.FileHeader fileHeader)
      throws IOException {
    if (PatchType.GIT_BINARY.equals(fileHeader.getPatchType())) {
      return false;
    }
    if (!hasCrLf(fileHeader)) {
      try (InputStream input = new FileInputStream(f)) {
        return RawText.isCrLfText(input);
      }
    }
    return false;
  }

  private static boolean hasCrLf(org.eclipse.jgit.patch.FileHeader fileHeader) {
    if (PatchType.GIT_BINARY.equals(fileHeader.getPatchType())) {
      return false;
    }
    for (org.eclipse.jgit.patch.HunkHeader header : fileHeader.getHunks()) {
      byte[] buf = header.getBuffer();
      int hunkEnd = header.getEndOffset();
      int lineStart = header.getStartOffset();
      while (lineStart < hunkEnd) {
        int nextLineStart = RawParseUtils.nextLF(buf, lineStart);
        if (nextLineStart > hunkEnd) {
          nextLineStart = hunkEnd;
        }
        if (nextLineStart <= lineStart) {
          break;
        }
        if (nextLineStart - lineStart > 1) {
          char first = (char) (buf[lineStart] & 0xFF);
          if (first == ' ' || first == '-') {
            // It's an old line. Does it end in CR-LF?
            if (buf[nextLineStart - 2] == '\r') {
              return true;
            }
          }
        }
        lineStart = nextLineStart;
      }
    }
    return false;
  }

  private void initHash(SHA1 hash, long size) {
    hash.update(Constants.encodedTypeString(Constants.OBJ_BLOB));
    hash.update((byte) ' ');
    hash.update(Constants.encodeASCII(size));
    hash.update((byte) 0);
  }

  private ObjectId hash(File f) throws IOException {
    SHA1 hash = SHA1.newInstance();
    initHash(hash, f.length());
    try (InputStream input = new FileInputStream(f)) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = input.read(buf)) >= 0) {
        hash.update(buf, 0, n);
      }
    }
    return hash.toObjectId();
  }

  private void checkOid(ObjectId baseId, ObjectId id, ChangeType type, File f, String path)
      throws PatchApplyException, IOException {
    boolean hashOk = false;
    if (id != null) {
      hashOk = baseId.equals(id);
      if (!hashOk && ChangeType.ADD.equals(type) && ObjectId.zeroId().equals(baseId)) {
        // We create a new file. The OID of an empty file is not the zero id!
        hashOk = Constants.EMPTY_BLOB_ID.equals(id);
      }
    } else {
      // DO NOT SUBMIT - is this code branch handling file (content) deletion?
      if (!inCore && ObjectId.zeroId().equals(baseId)) {
        // Empty file is OK.
        hashOk = !f.exists() || f.length() == 0;
      } else {
        hashOk = baseId.equals(hash(f));
      }
    }
    if (!hashOk) {
      throw new PatchApplyException(
          MessageFormat.format(JGitText.get().applyBinaryBaseOidWrong, path));
    }
  }

  private StreamLoader applyBinary(String path, File f, org.eclipse.jgit.patch.FileHeader fh,
      RepoIOHandler.StreamSupplier inputSupplier, ObjectId id, SHA1 hash)
      throws PatchApplyException, IOException, UnsupportedOperationException {
    if (inCore) {
      throw new UnsupportedOperationException(
          "Applying binary patch for remote repositories is not yet supported.");
    }
    if (!fh.getOldId().isComplete() || !fh.getNewId().isComplete()) {
      throw new PatchApplyException(
          MessageFormat.format(JGitText.get().applyBinaryOidTooShort, path));
    }
    org.eclipse.jgit.patch.BinaryHunk hunk = fh.getForwardBinaryHunk();
    // A BinaryHunk has the start at the "literal" or "delta" token. Data
    // starts on the next line.
    int start = RawParseUtils.nextLF(hunk.getBuffer(), hunk.getStartOffset());
    int length = hunk.getEndOffset() - start;
    // Write to a buffer and copy to the file only if everything was fine
    TemporaryBuffer buffer = new TemporaryBuffer.LocalFile(null);
    OutputStream out = buffer;
    switch (hunk.getType()) {
      case LITERAL_DEFLATED:
        // This just overwrites the file. We need to check the hash of
        // the base.
        checkOid(fh.getOldId().toObjectId(), id, fh.getChangeType(), f, path);
        initHash(hash, hunk.getSize());
        InputStream inflated = new SHA1InputStream(hash, new InflaterInputStream(
            new BinaryHunkInputStream(
                new ByteArrayInputStream(hunk.getBuffer(), start, length))));
        return ioHandler.createStreamLoader(() -> inflated, hunk.getSize());
      case DELTA_DEFLATED:
        // Unfortunately delta application needs random access to the
        // base to construct the result.
        byte[] base;
        try (InputStream in = inputSupplier.load()) {
          base = IO.readWholeStream(in, 0).array();
        }
        // At least stream the result!
        BinaryDeltaInputStream input = new BinaryDeltaInputStream(base,
            new InflaterInputStream(new BinaryHunkInputStream(
                new ByteArrayInputStream(hunk.getBuffer(), start, length))));
        long finalSize = input.getExpectedResultSize();
        initHash(hash, finalSize);
        SHA1InputStream hashed = new SHA1InputStream(hash, input);
        return ioHandler.createStreamLoader(() -> hashed, finalSize);
      default:
        throw new UnsupportedOperationException(
            "Couldn't apply binary patch of type " + hunk.getType().name());
    }
  }

  private StreamLoader applyText(RawText rt, org.eclipse.jgit.patch.FileHeader fh)
      throws IOException, PatchApplyException {
    List<ByteBuffer> oldLines = new ArrayList<>(rt.size());
    for (int i = 0; i < rt.size(); i++) {
      oldLines.add(rt.getRawString(i));
    }
    List<ByteBuffer> newLines = new ArrayList<>(oldLines);
    int afterLastHunk = 0;
    int lineNumberShift = 0;
    int lastHunkNewLine = -1;
    for (org.eclipse.jgit.patch.HunkHeader hh : fh.getHunks()) {
      // We assume hunks to be ordered
      if (hh.getNewStartLine() <= lastHunkNewLine) {
        throw new PatchApplyException(MessageFormat.format(JGitText.get().patchApplyException, hh));
      }
      lastHunkNewLine = hh.getNewStartLine();

      byte[] b = new byte[hh.getEndOffset() - hh.getStartOffset()];
      System.arraycopy(hh.getBuffer(), hh.getStartOffset(), b, 0, b.length);
      RawText hrt = new RawText(b);

      List<ByteBuffer> hunkLines = new ArrayList<>(hrt.size());
      for (int i = 0; i < hrt.size(); i++) {
        hunkLines.add(hrt.getRawString(i));
      }

      if (hh.getNewStartLine() == 0) {
        // Must be the single hunk for clearing all content
        if (fh.getHunks().size() == 1 && canApplyAt(hunkLines, newLines, 0)) {
          newLines.clear();
          break;
        }
        throw new PatchApplyException(MessageFormat.format(JGitText.get().patchApplyException, hh));
      }
      // Hunk lines as reported by the hunk may be off, so don't rely on
      // them.
      int applyAt = hh.getNewStartLine() - 1 + lineNumberShift;
      // But they definitely should not go backwards.
      if (applyAt < afterLastHunk && lineNumberShift < 0) {
        applyAt = hh.getNewStartLine() - 1;
        lineNumberShift = 0;
      }
      if (applyAt < afterLastHunk) {
        throw new PatchApplyException(MessageFormat.format(JGitText.get().patchApplyException, hh));
      }
      boolean applies = false;
      int oldLinesInHunk = hh.getLinesContext() + hh.getOldImage().getLinesDeleted();
      if (oldLinesInHunk <= 1) {
        // Don't shift hunks without context lines. Just try the
        // position corrected by the current lineNumberShift, and if
        // that fails, the position recorded in the hunk header.
        applies = canApplyAt(hunkLines, newLines, applyAt);
        if (!applies && lineNumberShift != 0) {
          applyAt = hh.getNewStartLine() - 1;
          applies = applyAt >= afterLastHunk && canApplyAt(hunkLines, newLines, applyAt);
        }
      } else {
        int maxShift = applyAt - afterLastHunk;
        for (int shift = 0; shift <= maxShift; shift++) {
          if (canApplyAt(hunkLines, newLines, applyAt - shift)) {
            applies = true;
            applyAt -= shift;
            break;
          }
        }
        if (!applies) {
          // Try shifting the hunk downwards
          applyAt = hh.getNewStartLine() - 1 + lineNumberShift;
          maxShift = newLines.size() - applyAt - oldLinesInHunk;
          for (int shift = 1; shift <= maxShift; shift++) {
            if (canApplyAt(hunkLines, newLines, applyAt + shift)) {
              applies = true;
              applyAt += shift;
              break;
            }
          }
        }
      }
      if (!applies) {
        throw new PatchApplyException(MessageFormat.format(JGitText.get().patchApplyException, hh));
      }
      // Hunk applies at applyAt. Apply it, and update afterLastHunk and
      // lineNumberShift
      lineNumberShift = applyAt - hh.getNewStartLine() + 1;
      int sz = hunkLines.size();
      for (int j = 1; j < sz; j++) {
        ByteBuffer hunkLine = hunkLines.get(j);
        if (!hunkLine.hasRemaining()) {
          // Completely empty line; accept as empty context line
          applyAt++;
          continue;
        }
        switch (hunkLine.array()[hunkLine.position()]) {
          case ' ':
            applyAt++;
            break;
          case '-':
            newLines.remove(applyAt);
            break;
          case '+':
            newLines.add(applyAt++, slice(hunkLine, 1));
            break;
          default:
            break;
        }
      }
      afterLastHunk = applyAt;
    }
    if (!isNoNewlineAtEndOfFile(fh)) {
      newLines.add(null);
    }
    if (!rt.isMissingNewlineAtEnd()) {
      oldLines.add(null);
    }
    if (oldLines.equals(newLines)) {
      // Unchanged; don't supply content override.
      return null;
    }

    TemporaryBuffer buffer = new TemporaryBuffer.LocalFile(null);
    try (OutputStream out = buffer) {
      for (Iterator<ByteBuffer> l = newLines.iterator(); l.hasNext(); ) {
        ByteBuffer line = l.next();
        if (line == null) {
          // Must be the marker for the final newline
          break;
        }
        out.write(line.array(), line.position(), line.remaining());
        if (l.hasNext()) {
          out.write('\n');
        }
      }
      return ioHandler.createStreamLoader(buffer::openInputStream, buffer.length());
    }
  }

  private boolean canApplyAt(List<ByteBuffer> hunkLines, List<ByteBuffer> newLines, int line) {
    int sz = hunkLines.size();
    int limit = newLines.size();
    int pos = line;
    for (int j = 1; j < sz; j++) {
      ByteBuffer hunkLine = hunkLines.get(j);
      if (!hunkLine.hasRemaining()) {
        // Empty line. Accept as empty context line.
        if (pos >= limit || newLines.get(pos).hasRemaining()) {
          return false;
        }
        pos++;
        continue;
      }
      switch (hunkLine.array()[hunkLine.position()]) {
        case ' ':
        case '-':
          if (pos >= limit || !newLines.get(pos).equals(slice(hunkLine, 1))) {
            return false;
          }
          pos++;
          break;
        default:
          break;
      }
    }
    return true;
  }

  private ByteBuffer slice(ByteBuffer b, int off) {
    int newOffset = b.position() + off;
    return ByteBuffer.wrap(b.array(), newOffset, b.limit() - newOffset);
  }

  private boolean isNoNewlineAtEndOfFile(org.eclipse.jgit.patch.FileHeader fh) {
    List<? extends org.eclipse.jgit.patch.HunkHeader> hunks = fh.getHunks();
    if (hunks == null || hunks.isEmpty()) {
      return false;
    }
    org.eclipse.jgit.patch.HunkHeader lastHunk = hunks.get(hunks.size() - 1);
    byte[] buf = new byte[lastHunk.getEndOffset() - lastHunk.getStartOffset()];
    System.arraycopy(lastHunk.getBuffer(), lastHunk.getStartOffset(), buf, 0, buf.length);
    RawText lhrt = new RawText(buf);
    return lhrt.getString(lhrt.size() - 1).equals("\\ No newline at end of file"); // $NON-NLS-1$
  }

  /**
   * An {@link InputStream} that updates a {@link SHA1} on every byte read. The hash is supposed to
   * have been initialized before reading starts.
   */
  private static class SHA1InputStream extends InputStream {

    private final SHA1 hash;

    private final InputStream in;

    SHA1InputStream(SHA1 hash, InputStream in) {
      this.hash = hash;
      this.in = in;
    }

    @Override
    public int read() throws IOException {
      int b = in.read();
      if (b >= 0) {
        hash.update((byte) b);
      }
      return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int n = in.read(b, off, len);
      if (n > 0) {
        hash.update(b, off, n);
      }
      return n;
    }

    @Override
    public void close() throws IOException {
      in.close();
    }
  }
}
