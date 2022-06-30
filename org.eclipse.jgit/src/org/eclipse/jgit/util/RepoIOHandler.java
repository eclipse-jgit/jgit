package org.eclipse.jgit.util;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
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
import org.eclipse.jgit.errors.IndexWriteException;
import org.eclipse.jgit.errors.LargeObjectException;
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
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.TreeWalk.OperationType;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.util.FS.ExecutionResult;
import org.eclipse.jgit.util.LfsFactory.LfsInputStream;
import org.eclipse.jgit.util.TemporaryBuffer.LocalFile;
import org.eclipse.jgit.util.io.EolStreamTypeUtil;

/**
 * Handles files IO for both local and remote repositories.
 * <p>
 * You should use a single instance for all of your file changes.
 */
public class RepoIOHandler {

  /**
   * The repository this handler operates on.
   *
   * <p>Callers that want to assume the repo is not null may use {@link #nonNullRepo()}.
   */
  @Nullable
  private final Repository repo;

  private final ObjectInserter inserter;
  private final ObjectReader reader;
  private final boolean inCore;
  private DirCache dirCache = null;
  private DirCacheBuilder builder = null;
  String smudgeCommand;
  /**
   * The {@link WorkingTreeOptions} are needed to determine line endings for merged files.
   */
  private WorkingTreeOptions workingTreeOptions;
  /**
   * Files modified during this merge operation.
   */
  private final List<String> modifiedFiles = new LinkedList<>();
  /**
   * If the merger has nothing to do for a file but check it out at the end of the operation, it can
   * be added here.
   */
  private final Map<String, DirCacheEntry> toBeCheckedOut = new HashMap<>();
  /**
   * Keeps {@link CheckoutMetadata} for {@link #checkout()}.
   */
  private Map<String, CheckoutMetadata> checkoutMetadata;

  /**
   * Keeps {@link CheckoutMetadata} for {@link #cleanUp()}.
   */
  private Map<String, CheckoutMetadata> cleanupMetadata;

  /**
   * Please do not use this directly. Instead, use {@link InCoreSupport} interface.
   *
   * @param repo          either local or remote repository.
   * @param inCore        whether the repository is remote (inCore==true), or local
   *                      (inCore==false).
   * @param oi            to use for writing the modified objects with.
   * @param reader        to read existing content with.
   * @param smudgeCommand to use when reading files.
   */
  RepoIOHandler(
      Repository repo,
      boolean inCore,
      ObjectInserter oi,
      ObjectReader reader,
      String smudgeCommand) {
    this.repo = repo;
    this.inserter = oi;
    this.reader = reader;
    this.inCore = inCore;
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
   * We write the patch result to a {@link org.eclipse.jgit.util.TemporaryBuffer} and then use
   * {@link DirCacheCheckout}.getContent() to run the result through the CR-LF and smudge filters.
   * DirCacheCheckout needs an ObjectLoader, not a TemporaryBuffer, so this class bridges between
   * the two, making any Stream provided by a {@link StreamSupplier} look like an ordinary git blob
   * to DirCacheCheckout.
   */
  public static class StreamLoader extends ObjectLoader {

    private final StreamSupplier data;

    private final long size;

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
    public ObjectStream openStream() throws IOException {
      return new ObjectStream.Filter(getType(), getSize(), new BufferedInputStream(data.load()));
    }
  }

  /**
   * Creates stream loader for the given supplier.
   *
   * @param supplier to wrap
   * @param length   of the supplied content
   * @return the result stream loader
   */
  public StreamLoader createStreamLoader(StreamSupplier supplier, long length) {
    return new StreamLoader(supplier, length);
  }

  /**
   * Gets dir cache for the repo. Locked for local repositories.
   *
   * @return the result dir cache
   * @throws IOException is case the local dir cache cannot be read
   */
  public DirCache getLockedDirCache() throws IOException {
    if (dirCache == null) {
      if (inCore) {
        dirCache = DirCache.newInCore();
      } else {
        dirCache = repo.readDirCache();
        dirCache.lock();
      }
      builder = dirCache.builder();
    }
    return dirCache;
  }

  /**
   * Gets the raw text of the given file.
   *
   * @param file               to read from
   * @param fileStreamSupplier if loaded, the stream of the file content
   * @param fileId             of the file
   * @param path               of the file
   * @param loaded             whether the file was loaded by a {@link TreeWalk}
   * @param filterCommand      for reading the file content
   * @param convertCrLf        whether a CR-LF conversion is needed
   * @return the result raw text
   * @throws IOException in case of filtering issues
   */
  public RawText getRawText(
      File file,
      StreamSupplier fileStreamSupplier,
      ObjectId fileId,
      String path,
      boolean loaded,
      String filterCommand,
      boolean convertCrLf)
      throws IOException {
    if (loaded) {
      // Can't use file.openEntryStream() as we cannot control its CR-LF conversion.
      try (InputStream input =
          filterClean(repo, path, fileStreamSupplier.load(), convertCrLf, filterCommand)) {
        return new RawText(org.eclipse.jgit.util.IO.readWholeStream(input, 0).array());
      }
    }
    if (convertCrLf) {
      try (InputStream input =
          EolStreamTypeUtil.wrapInputStream(fileStreamSupplier.load(), EolStreamType.TEXT_LF)) {
        return new RawText(org.eclipse.jgit.util.IO.readWholeStream(input, 0).array());
      }
    }
    if (inCore && fileId.equals(ObjectId.zeroId())) {
      return new RawText(new byte[]{});
    }
    return new RawText(file);
  }

  private InputStream filterClean(
      Repository repository,
      String path,
      InputStream fromFile,
      boolean convertCrLf,
      String filterCommand)
      throws IOException {
    InputStream input = fromFile;
    if (convertCrLf) {
      input = EolStreamTypeUtil.wrapInputStream(input, EolStreamType.TEXT_LF);
    }
    if (org.eclipse.jgit.util.StringUtils.isEmptyOrNull(filterCommand)) {
      return input;
    }
    if (FilterCommandRegistry.isRegistered(filterCommand)) {
      LocalFile buffer = new org.eclipse.jgit.util.TemporaryBuffer.LocalFile(null);
      FilterCommand command =
          FilterCommandRegistry.createFilterCommand(filterCommand, repository, input, buffer);
      while (command.run() != -1) {
        // loop as long as command.run() tells there is work to do
      }
      return buffer.openInputStreamWithAutoDestroy();
    }
    org.eclipse.jgit.util.FS fs = repository.getFS();
    ProcessBuilder filterProcessBuilder = fs.runInShell(filterCommand, new String[0]);
    filterProcessBuilder.directory(repository.getWorkTree());
    filterProcessBuilder
        .environment()
        .put(Constants.GIT_DIR_KEY, repository.getDirectory().getAbsolutePath());
    ExecutionResult result;
    try {
      result = fs.execute(filterProcessBuilder, input);
    } catch (IOException | InterruptedException e) {
      throw new IOException(new FilterFailedException(e, filterCommand, path));
    }
    int rc = result.getRc();
    if (rc != 0) {
      throw new IOException(
          new FilterFailedException(
              rc,
              filterCommand,
              path,
              result.getStdout().toByteArray(4096),
              org.eclipse.jgit.util.RawParseUtils.decode(result.getStderr().toByteArray(4096))));
    }
    return result.getStdout().openInputStreamWithAutoDestroy();
  }

  /**
   * Writes the cache changes and cleans up everything.
   *
   * @return the modified tree ID if any, or null otherwise
   * @throws IOException if any of the writes fail
   */
  public ObjectId writeChangesAndCleanUp() throws IOException {
    if (inCore) {
      builder.finish();
      cleanUp();
      return getLockedDirCache().writeTree(inserter);
    }

    // DO NOT SUBMIT - not needed for apply
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
    dirCache.unlock();
    return null;
  }

  /**
   * Adds a {@link DirCacheEntry} for direct checkout and remembers its {@link CheckoutMetadata}.
   *
   * @param path               of the entry
   * @param entry              to add
   * @param cleanupAttributes  the {@link Attributes} of the tree
   * @param checkoutAttributes the {@link Attributes} of the tree
   * @since 6.1
   */
  public void addToCheckout(
      String path, DirCacheEntry entry, Attributes cleanupAttributes,
      Attributes checkoutAttributes) {
    // DO NOT SUBMIT - should call this from the merger directly.
    if (!inCore) {
      return;
    }
    toBeCheckedOut.put(path, entry);
    addCheckoutMetadata(cleanupMetadata, path, cleanupAttributes);
    addCheckoutMetadata(checkoutMetadata, path, checkoutAttributes);
  }

  /**
   * Deletes the given file
   *
   * @param path       of the file to be deleted
   * @param file       to be deleted
   * @param attributes to use for determining the metadata
   * @throws IOException if the file cannot be deleted
   */
  public void deleteFile(String path, File file, Attributes attributes) throws IOException {
    if (!file.delete()) {
      throw new IOException(
          MessageFormat.format(JGitText.get().cannotDeleteFile, file));
    }
    if (inCore && file.isFile()) {
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
   * @since 6.1
   */
  private void addCheckoutMetadata(
      Map<String, CheckoutMetadata> map, String path, Attributes attributes) {
    if (inCore || map == null) {
      return;
    }
    EolStreamType eol =
        EolStreamTypeUtil.detectStreamType(
            OperationType.CHECKOUT_OP, workingTreeOptions, attributes);
    CheckoutMetadata data = new CheckoutMetadata(eol, smudgeCommand);
    map.put(path, data);
  }

  // DO NOT SUBMIT - ResolveMerger::checkout also handles deletions, is that covered by adddeletion()?
  private void checkout() throws NoWorkTreeException, IOException {
    // Iterate in reverse so that "folder/file" is deleted before
    // "folder". Otherwise, this could result in a failing path because
    // of a non-empty directory, for which delete() would fail.
    for (Map.Entry<String, DirCacheEntry> entry : toBeCheckedOut.entrySet()) {
      DirCacheEntry cacheEntry = entry.getValue();
      if (cacheEntry.getFileMode() == FileMode.GITLINK) {
        new File(nonNullRepo().getWorkTree(), entry.getKey()).mkdirs();
      } else {
        DirCacheCheckout.checkoutEntry(
            repo, cacheEntry, reader, false, checkoutMetadata.get(entry.getKey()));
        modifiedFiles.add(entry.getKey());
      }
    }
  }

  private void cleanUp() throws NoWorkTreeException, IOException {
    if (inCore) {
      modifiedFiles.clear();
      return;
    }

    Iterator<String> mpathsIt = modifiedFiles.iterator();
    while (mpathsIt.hasNext()) {
      String mpath = mpathsIt.next();
      DirCacheEntry entry = getLockedDirCache().getEntry(mpath);
      if (entry != null) {
        DirCacheCheckout.checkoutEntry(repo, entry, reader, false, cleanupMetadata.get(mpath));
      }
      mpathsIt.remove();
    }
  }

  /**
   * Applies the file in the filesystem or cache builder with the given content.
   *
   * @param resultStreamLoader with the content to be updated
   * @param checkoutMetadata   for parsing the content
   * @param path               of the file to be updated
   * @param file               to be updated
   * @param safeWrite          whether the content should be written to a buffer first
   * @param fileMode           of the updated file
   * @throws IOException if the {@link CheckoutMetadata} cannot be determined
   */
  public void updateFileWithContent(
      StreamLoader resultStreamLoader,
      CheckoutMetadata checkoutMetadata,
      String path,
      File file,
      boolean safeWrite,
      FileMode fileMode)
      throws IOException {
    updateFile(resultStreamLoader, checkoutMetadata, path, file, safeWrite);
    updateIndex(resultStreamLoader, path, file, fileMode);
  }

  // DO NOT SUBMIT ResolveMerger::writeMergedFile should be replaced by writing using
  // DirCacheCheckout::getContent
  private void updateFile(
      StreamLoader resultStreamLoader,
      CheckoutMetadata checkoutMetadata,
      String path,
      File f,
      boolean safeWrite)
      throws IOException {
    if (inCore) {
      return;
    }
    if (safeWrite) {
      try (org.eclipse.jgit.util.TemporaryBuffer buffer =
          new org.eclipse.jgit.util.TemporaryBuffer.LocalFile(null)) {
        // Write to a buffer and copy to the file only if everything was fine.
        DirCacheCheckout.getContent(
            repo, path, checkoutMetadata, resultStreamLoader, null, buffer);
        InputStream bufIn = buffer.openInputStream();
        Files.copy(bufIn, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    } else {
      OutputStream outputStream = new FileOutputStream(f);
      DirCacheCheckout.getContent(
          repo, path, checkoutMetadata, resultStreamLoader, null, outputStream);
    }
  }

  private void updateIndex(
      StreamLoader resultStreamLoader,
      String path,
      File f,
      FileMode fileMode)
      throws IOException {
    if (!inCore) {
      return;
    }
    DirCacheEntry dce = new DirCacheEntry(path, DirCacheEntry.STAGE_0);
    // Set the mode for the new content. Fall back to REGULAR_FILE if
    // we can't merge modes of OURS and THEIRS.
    dce.setFileMode(fileMode == FileMode.MISSING ? FileMode.REGULAR_FILE : fileMode);
    if (f != null) {
      dce.setLastModified(nonNullRepo().getFS().lastModifiedInstant(f));
      dce.setLength((int) f.length());
    }

    // DO NOT SUBMIT - merger might do have attributes here.
    dce.setObjectId(insertResult(resultStreamLoader, null));
    builder.add(dce);
  }

  private ObjectId insertResult(StreamLoader resultStreamLoader, Attributes attributes)
      throws IOException {
    try (LfsInputStream is =
        org.eclipse.jgit.util.LfsFactory.getInstance()
            .applyCleanFilter(
                repo,
                resultStreamLoader.data.load(),
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
