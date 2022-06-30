package org.eclipse.jgit.util;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.FilterFailedException;
import org.eclipse.jgit.attributes.Attributes;
import org.eclipse.jgit.attributes.FilterCommand;
import org.eclipse.jgit.attributes.FilterCommandRegistry;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.dircache.DirCacheCheckout.CheckoutMetadata;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.BinaryBlobException;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IndexWriteException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.EolStreamType;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ResolveMerger.MergeFailureReason;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.LfsFactory.LfsInputStream;
import org.eclipse.jgit.util.TemporaryBuffer.LocalFile;
import org.eclipse.jgit.util.io.EolStreamTypeUtil;

// DO NOT SUBMIT - rename. This class can handle both local and remote repos.
public class InCoreHandler {

  /**
   * The repository this merger operates on.
   * <p>
   * Callers that want to assume the repo is not null (e.g. because of a previous check that the
   * applier is not in-core) may use {@link #nonNullRepo()}.
   */
  @Nullable
  protected final Repository repo;
  protected final ObjectInserter inserter;
  protected ObjectReader reader;
  private final boolean inCore;
  protected final boolean shouldUpdateIndex;
  protected DirCache dirCache = null;
  String smudgeCommand;
  /**
   * The {@link WorkingTreeOptions} are needed to determine line endings for merged files.
   */
  protected WorkingTreeOptions workingTreeOptions;
  /**
   * Files modified during this merge operation.
   */
  protected List<String> modifiedFiles = new LinkedList<>();
  /**
   * If the merger has nothing to do for a file but check it out at the end of the operation, it can
   * be added here.
   */
  protected Map<String, DirCacheEntry> toBeCheckedOut = new HashMap<>();
  /**
   * Keeps {@link CheckoutMetadata} for {@link #checkout()}.
   */
  private Map<String, CheckoutMetadata> checkoutMetadata;

  /**
   * Keeps {@link CheckoutMetadata} for {@link #cleanUp()}.
   */
  private Map<String, CheckoutMetadata> cleanupMetadata;

  InCoreHandler(Repository repo, ObjectInserter oi, ObjectReader reader, boolean inCore,
      boolean shouldUpdateIndex, String smudgeCommand) {
    this.repo = repo;
    this.inserter = oi;
    this.reader = reader;
    this.inCore = inCore;
    this.shouldUpdateIndex = shouldUpdateIndex;
    if (!inCore) {
      workingTreeOptions = repo.getConfig().get(WorkingTreeOptions.KEY);
      this.smudgeCommand = smudgeCommand;
      checkoutMetadata = new HashMap<>();
      cleanupMetadata = new HashMap<>();
    }
  }

  /**
   * Something that can supply an {@link InputStream}.
   */
  public interface StreamSupplier {

    InputStream load() throws IOException;
  }

  /**
   * We write the patch result to a {@link org.eclipse.jgit.util.TemporaryBuffer} and then use {@link
   * DirCacheCheckout}.getContent() to run the result through the CR-LF and smudge filters.
   * DirCacheCheckout needs an ObjectLoader, not a TemporaryBuffer, so this class bridges between
   * the two, making any Stream provided by a {@link StreamSupplier} look like an ordinary git blob
   * to DirCacheCheckout.
   */
  public class StreamLoader extends ObjectLoader {

    private StreamSupplier data;

    private long size;


    public StreamLoader(StreamSupplier data, long length) {
      this.data = data;
      this.size = length;
    }

    @Override
    public int getType() {
      return Constants.OBJ_BLOB;
    }

    @Override
    public long getSize() {
      return size;
    }

    @Override
    public boolean isLarge() {
      return true;
    }

    @Override
    public byte[] getCachedBytes() throws LargeObjectException {
      throw new LargeObjectException();
    }

    @Override
    public ObjectStream openStream()
        throws IOException {
      return new ObjectStream.Filter(getType(), getSize(),
          new BufferedInputStream(data.load()));
    }
  }

  public StreamLoader getStreamLoader(StreamSupplier supplier, long length) {
    return new StreamLoader(supplier, length);
  }

  // DO NOT SUBMIT - need to handle dircache locking and unlocking for !inCore. Probably not here.
  public DirCache getCache() throws IOException {
    if (dirCache == null) {
      if (inCore) {
        dirCache = DirCache.newInCore();
      } else {
        dirCache = repo.readDirCache();
      }
    }
    return dirCache;
  }

  public RawText getRawText(TreeWalk walk, boolean loaded, ObjectId fileId, String path, File f,
      StreamSupplier fileStreamSupplier, boolean convertCrLf)
      throws IOException, BinaryBlobException {
    if (loaded) {
      String command = walk.getFilterCommand(
          Constants.ATTR_FILTER_TYPE_CLEAN);
      // Can't use file.openEntryStream() as it would do CR-LF
      // conversion as usual, not as wanted by us.
      try (InputStream input = filterClean(repo, path,
          fileStreamSupplier.load(), convertCrLf, command)) {
        return new RawText(
            org.eclipse.jgit.util.IO.readWholeStream(input, 0).array());
      }
    }
    if (convertCrLf) {
      try (InputStream input = EolStreamTypeUtil.wrapInputStream(
          new FileInputStream(f), EolStreamType.TEXT_LF)) {
        return new RawText(org.eclipse.jgit.util.IO.readWholeStream(input, 0).array());
      }
    }
    if (inCore && fileId.equals(ObjectId.zeroId())) {
      return new RawText(new byte[]{});
    }
    return new RawText(f);
  }

  private InputStream filterClean(Repository repository, String path,
      InputStream fromFile, boolean convertCrLf, String filterCommand)
      throws IOException {
    InputStream input = fromFile;
    if (convertCrLf) {
      input = EolStreamTypeUtil.wrapInputStream(input,
          EolStreamType.TEXT_LF);
    }
    if (org.eclipse.jgit.util.StringUtils.isEmptyOrNull(filterCommand)) {
      return input;
    }
    if (FilterCommandRegistry.isRegistered(filterCommand)) {
      LocalFile buffer = new org.eclipse.jgit.util.TemporaryBuffer.LocalFile(null);
      FilterCommand command = FilterCommandRegistry.createFilterCommand(
          filterCommand, repository, input, buffer);
      while (command.run() != -1) {
        // loop as long as command.run() tells there is work to do
      }
      return buffer.openInputStreamWithAutoDestroy();
    }
    org.eclipse.jgit.util.FS fs = repository.getFS();
    ProcessBuilder filterProcessBuilder = fs.runInShell(filterCommand,
        new String[0]);
    filterProcessBuilder.directory(repository.getWorkTree());
    filterProcessBuilder.environment().put(Constants.GIT_DIR_KEY,
        repository.getDirectory().getAbsolutePath());
    ExecutionResult result;
    try {
      result = fs.execute(filterProcessBuilder, input);
    } catch (IOException | InterruptedException e) {
      throw new IOException(
          new FilterFailedException(e, filterCommand, path));
    }
    int rc = result.getRc();
    if (rc != 0) {
      throw new IOException(new FilterFailedException(rc, filterCommand,
          path, result.getStdout().toByteArray(4096), org.eclipse.jgit.util.RawParseUtils
          .decode(result.getStderr().toByteArray(4096))));
    }
    return result.getStdout().openInputStreamWithAutoDestroy();
  }


  public DirCacheBuilder finishBuilding(DirCacheBuilder builder)
      throws IOException {
    if (inCore) {
      builder.finish();
      return null;
    }
    // No problem found. The only thing left to be done is to
    // checkout all files from "theirs" which have been selected to
    // go into the new index.
    checkout();

    // All content-merges are successfully done. If we can now write the
    // new index we are on quite safe ground. Even if the checkout of
    // files coming from "theirs" fails the user can work around such
    // failures by checking out the index again.
    if (!builder.commit()) {
      cleanUp();
      throw new IndexWriteException();
    }
    return null;
  }

  public ObjectId writeCache() throws IOException {
    if (!shouldUpdateIndex) {
      return null;
    }
    return getCache().writeTree(inserter);
  }

  /**
   * Adds a {@link DirCacheEntry} for direct checkout and remembers its {@link CheckoutMetadata}.
   *
   * @param path               of the entry
   * @param entry              to add
   * @param cleanupAttributes  the {@link Attributes} of the tree
   * @param checkoutAttributes the {@link Attributes} of the tree
   * @throws IOException if the {@link CheckoutMetadata} cannot be determined
   * @since 6.1
   */
  // DO NOT SUBMIT - should call this from applyPath() directly.
  public void addToCheckout(String path, DirCacheEntry entry,
      Attributes cleanupAttributes, Attributes checkoutAttributes)
      throws IOException {
    if (!shouldUpdateIndex) {
      return;
    }
    toBeCheckedOut.put(path, entry);
    addCheckoutMetadata(cleanupMetadata, path, cleanupAttributes);
    addCheckoutMetadata(checkoutMetadata, path, checkoutAttributes);
  }

  public void addDeletion(String path, boolean isFile,
      Attributes attributes) throws IOException {
    if (!shouldUpdateIndex) {
      return;
    }
    if (isFile) {
      addCheckoutMetadata(cleanupMetadata, path, attributes);
    }
  }

  /**
   * Remembers the {@link CheckoutMetadata} for the given path; it may be needed in {@link
   * #checkout()} or in {@link #cleanUp()}.
   *
   * @param map        to add the metadata to
   * @param path       of the current node
   * @param attributes to use for determining the metadata
   * @throws IOException if the smudge filter cannot be determined
   * @since 6.1
   */
  protected void addCheckoutMetadata(Map<String, CheckoutMetadata> map,
      String path, Attributes attributes)
      throws IOException {
    if (inCore || map == null) {
      return;
    }
    EolStreamType eol = EolStreamTypeUtil.detectStreamType(
        OperationType.CHECKOUT_OP, workingTreeOptions,
        attributes);
    CheckoutMetadata data = new CheckoutMetadata(eol,
        smudgeCommand);
    map.put(path, data);
  }

  // DO NOT SUBMIT - ResolveMerger::checkout also handles deletions, should extract a different method. Applier handles deletions inline.
  private void checkout() throws NoWorkTreeException, IOException {
    // Iterate in reverse so that "folder/file" is deleted before
    // "folder". Otherwise this could result in a failing path because
    // of a non-empty directory, for which delete() would fail.
    for (Map.Entry<String, DirCacheEntry> entry : toBeCheckedOut
        .entrySet()) {
      DirCacheEntry cacheEntry = entry.getValue();
      if (cacheEntry.getFileMode() == FileMode.GITLINK) {
        new File(nonNullRepo().getWorkTree(), entry.getKey()).mkdirs();
      } else {
        DirCacheCheckout.checkoutEntry(repo, cacheEntry, reader, false,
            checkoutMetadata.get(entry.getKey()));
        modifiedFiles.add(entry.getKey());
      }
    }
  }

  protected void cleanUp() throws NoWorkTreeException, CorruptObjectException,
      IOException {
    if (inCore) {
      modifiedFiles.clear();
      return;
    }

    Iterator<String> mpathsIt = modifiedFiles.iterator();
    while (mpathsIt.hasNext()) {
      String mpath = mpathsIt.next();
      DirCacheEntry entry = getCache().getEntry(mpath);
      if (entry != null) {
        DirCacheCheckout.checkoutEntry(repo, entry, reader, false,
            cleanupMetadata.get(mpath));
      }
      mpathsIt.remove();
    }
  }

  public void update(ObjectId baseObjectId, StreamLoader resultStreamLoader, DirCacheBuilder builder,
      CheckoutMetadata checkoutMetadata, String path, File f, boolean safeWrite,
      FileMode fileMode)
      throws IOException {
    updateFile(resultStreamLoader, checkoutMetadata, path, f, safeWrite);
    updateIndex(baseObjectId, builder, resultStreamLoader, path, f, fileMode);
  }

  // DO NOT SUBMIT ResolveMerger::writeMergedFile should be replaced by writing using DirCacheCheckout::getContent
  private void updateFile(StreamLoader resultStreamLoader,
      CheckoutMetadata checkoutMetadata, String path, File f, boolean safeWrite)
      throws FileNotFoundException,
      IOException {
    if (inCore) {
      return;
    }
    try {
      if (safeWrite) {
        org.eclipse.jgit.util.TemporaryBuffer buffer = new org.eclipse.jgit.util.TemporaryBuffer.LocalFile(null);
        try {
          // Write to a buffer and copy to the file only if everything was fine.
          OutputStream outputStream = buffer;
          DirCacheCheckout.getContent(repo, path, checkoutMetadata, resultStreamLoader, null,
              outputStream);
          InputStream bufIn = buffer.openInputStream();
          Files.copy(bufIn, f.toPath(),
              StandardCopyOption.REPLACE_EXISTING);
        } finally {
          if (buffer != null) {
            buffer.destroy();
          }
        }
      } else {
        OutputStream outputStream = new FileOutputStream(f);
        DirCacheCheckout.getContent(repo, path, checkoutMetadata, resultStreamLoader, null,
            outputStream);
      }
    } finally {
      // DO NOT SUBMIT - need to close the streamloader's buffer. Probably outside.
    }
  }

  private void updateIndex(ObjectId baseObjectId, DirCacheBuilder builder, StreamLoader resultStreamLoader, String path,
      File f, FileMode fileMode) throws IOException {
    DirCacheEntry dce = new DirCacheEntry(path, DirCacheEntry.STAGE_1);

    // Set the mode for the new content. Fall back to REGULAR_FILE if
    // we can't merge modes of OURS and THEIRS.
    dce.setFileMode(fileMode == FileMode.MISSING
        ? FileMode.REGULAR_FILE : fileMode);
    if (!inCore) {
      dce.setLastModified(repo.getFS().lastModifiedInstant(f));
      dce.setLength((int) f.length());
    }
    // DO NOT SUBMIT - merger might do have attributes here.
    dce.setObjectId(baseObjectId);
    builder.add(dce);

    dce = new DirCacheEntry(path, DirCacheEntry.STAGE_2);

    // Set the mode for the new content. Fall back to REGULAR_FILE if
    // we can't merge modes of OURS and THEIRS.
    dce.setFileMode(fileMode == FileMode.MISSING
        ? FileMode.REGULAR_FILE : fileMode);
    if (!inCore) {
      dce.setLastModified(repo.getFS().lastModifiedInstant(f));
      dce.setLength((int) f.length());
    }
    // DO NOT SUBMIT - merger might do have attributes here.
    dce.setObjectId(
        insertResult(resultStreamLoader, null));

//    add(tw.getRawPath(), base, DirCacheEntry.STAGE_1, EPOCH, 0);
//				add(tw.getRawPath(), ours, DirCacheEntry.STAGE_2, EPOCH, 0);
//				add(tw.getRawPath(), theirs, DirCacheEntry.STAGE_3, EPOCH, 0);
    builder.add(dce);
  }

  private ObjectId insertResult(StreamLoader resultStreamLoader,
      Attributes attributes) throws IOException {
    // DO NOT SUBMIT - ATTR_MERGE should be determined by an abstract interface method.
    try (LfsInputStream is = org.eclipse.jgit.util.LfsFactory.getInstance().applyCleanFilter(
        repo, resultStreamLoader.data.load(),
        resultStreamLoader.size,
        attributes != null ? attributes.get(Constants.ATTR_MERGE) : null)) {
      return inserter.insert(OBJ_BLOB, is.getLength(), is);
    }
  }

  /**
   * Get non-null repository instance
   *
   * @return non-null repository instance
   * @throws java.lang.NullPointerException if the handler was constructed without a repository.
   * @since 4.8
   */
  public Repository nonNullRepo() {
    if (repo == null) {
      throw new NullPointerException(JGitText.get().repositoryIsRequired);
    }
    return repo;
  }
}
