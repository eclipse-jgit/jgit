# JGit configuration options

## Legend

| git option | description |
|------------|-------------|
| &#x2705; | option defined by native git |
| &#x20DE; | jgit custom option not supported by native git |

For details on native git options see also the official [git config documentation](https://git-scm.com/docs/git-config).

## __commitGraph__ options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `commitGraph.readChangedPaths` | `false` | &#x2705; | Whether to use the changed-path Bloom filters in the commit-graph file (if it exists, and they are present). |

## __core__ options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `core.attributesFile` | | &#x2705; | In addition to `.gitattributes` (per-directory) and `.git/info/attributes`, Git looks into this file for attributes . Path expansions are made the same way as for `core.excludesFile`. |
| `core.autocrlf` | `false` | &#x2705; | Setting this variable to `true` is the same as setting the text attribute to `auto` on all files and `core.eol` to `crlf`. Set to `true` if you want to have CRLF line endings in your working directory and the repository has LF line endings. This variable can be set to `input`, in which case no output conversion is performed. |
| `core.bare` | set automatically on init or clone | &#x2705; | If true this repository is assumed to be bare and has no working directory associated with it. If this is the case a number of commands that require a working directory will be disabled |
| `core.bigFileThreshold` | `50 MiB` | &#x2705; | Files larger than this size are stored deflated, without attempting delta compression. Storing large files without delta compression avoids excessive memory usage, at the slight expense of increased disk usage. Additionally files larger than this size are always treated as binary. |
| `core.checkstat` |  | &#x2705; | When missing or is set to `default`, many fields in the stat structure are checked to detect if a file has been modified since Git looked at it. Checks as much of the dircache stat info as possible (in JGit limited by Java filesystem API). When set to `minimum` only checks the size and whole second part of time stamp when comparing the stat info in the dircache with actual file stat info. |
| `core.commitGraph`| `false` | &#x2705; | Whether to read the commit-graph file (if it exists) to parse the graph structure of commits. |
| `core.compression` | `-1` (zlib default) | &#x2705; | An integer `-1..9`, indicating a default compression level. `-1` is the zlib default. `0` means no compression, and `1..9` are various speed/size tradeoffs, `9` being slowest.|
| `core.deltaBaseCacheLimit` | `10 MiB` | &#x2705; | Maximum number of bytes to reserve for caching base objects that multiple deltafied objects reference. By storing the entire decompressed base object in a cache Git is able to avoid unpacking and decompressing frequently used base objects multiple times. |
| `core.dfs.blockLimit` | `30 MiB` | &#x20DE; | Maximum number bytes of heap memory to dedicate to caching pack file data in DFS block cache. |
| `core.dfs.blockSize` | `64 kiB` | &#x20DE; | Size in bytes of a single window read in from the pack file into the DFS block cache. |
| `core.dfs.concurrencyLevel` | `32` | &#x20DE; | The estimated number of threads concurrently accessing the DFS block cache. |
| `core.dfs.deltaBaseCacheLimit` | `10 MiB` | &#x20DE; | Maximum number of bytes to hold in per-reader DFS delta base cache. |
| `core.dfs.loadRevIndexInParallel` | false; | &#x20DE; | Try to load the reverse index in parallel with the bitmap index. |
| `core.dfs.streamFileThreshold` | `50 MiB` | &#x20DE; | The size threshold beyond which objects must be streamed. |
| `core.dfs.streamBuffer` | Block size of the pack | &#x20DE; | Number of bytes to use for buffering when streaming a pack file during copying. If 0 the block size of the pack is used|
| `core.dfs.streamRatio` | `0.30` | &#x20DE; | Ratio of DFS block cache to occupy with a copied pack. Values between `0` and `1.0`. |
| `core.dirNoGitLinks` | `false` | &#x20DE; | If set to `true` avoid checking for submodules. See [bug 436200](https://bugs.eclipse.org/bugs/show_bug.cgi?id=436200). |
| `core.eol` | `native` | &#x2705; | Sets the line ending type to use in the working directory for files that are marked as text (either by having the text attribute set, or by having `text=auto` and Git auto-detecting the contents as text). Alternatives are `lf`, `crlf` and `native`, which uses the platform’s native line ending. |
| `core.excludesFile` | | &#x2705; | Specifies the pathname to the file that contains patterns to describe paths that are not meant to be tracked, in addition to `.gitignore` (per-directory) and `.git/info/exclude`. |
| `core.fileMode` | Auto detects if file modes are supported | &#x2705; | Tells Git if the executable bit of files in the working tree is to be honored. |
| `core.hideDotFiles` | `dotGitOnly` | &#x2705; | Windows only. If `true`, mark newly-created directories and files whose name starts with a dot as hidden. If `dotGitOnly`, only the `.git/` directory is hidden, but no other files starting with a dot. |
| `core.hooksPath` | `$GIT_DIR/hooks` | &#x2705; | Path to look for hooks. |
| `core.logAllRefUpdates` | `true` in a repository with working tree, `false` in bare repository | &#x2705; | Enable the reflog. |
| `core.packedGitLimit` | `10 MiB` | &#x2705; | Maximum number of bytes to cache in memory from pack files. |
| `core.packedGitMmap` | `false` | &#x2705; | Whether to use Java NIO virtual memory mapping for JGit buffer cache. When set to `true` enables use of Java NIO virtual memory mapping for cache windows, `false` reads entire window into a `byte[]` with standard read calls. `true` is experimental and may cause instabilities and crashes since Java doesn't support explicit unmapping of file regions mapped to virtual memory. |
| `core.packedGitOpenFiles` | `128` | &#x20DE; | Maximum number of streams to open at a time. Open packs count against the process limits. |
| `core.packedGitUseStrongRefs` | `false` | &#x20DE; | Whether the window cache should use strong references (`true`) or SoftReferences (`false`). When `false` the JVM will drop data cached in the JGit block cache when heap usage comes close to the maximum heap size. |
| `core.packedIndexGitUseStrongRefs` | `true` | &#x20DE; | Whether pack indices should use strong references (`true`) or SoftReferences (`false`). When `false` the JVM will drop data cached in the JGit pack indices when heap usage comes close to the maximum heap size. |
| `core.packedGitWindowSize` | `8 kiB` | &#x2705; | Number of bytes of a pack file to load into memory in a single read operation. This is the "page size" of the JGit buffer cache, used for all pack access operations. All disk IO occurs as single window reads. Setting this too large may cause the process to load more data than is required; setting this too small may increase the frequency of read() system calls. |
| `core.precomposeUnicode` | `true` on Mac OS | &#x2705; | MacOS only. When `true`, JGit reverts the unicode decomposition of filenames done by Mac OS. |
| `core.quotePath` | `true` | &#x2705; | Commands that output paths (e.g. ls-files, diff), will quote "unusual" characters in the pathname by enclosing the pathname in double-quotes and escaping those characters with backslashes in the same way C escapes control characters (e.g. `\t` for TAB, `\n` for LF, `\\` for backslash) or bytes with values larger than `0x80` (e.g. octal `\302\265` for "micro" in UTF-8). |
| `core.repositoryFormatVersion` | `1` | &#x20DE; | Internal version identifying the repository format and layout version. Don't set manually. |
| `core.sha1Implementation` | `java` | &#x20DE; | Choose the SHA1 implementation used by JGit. Set it to `java` to use JGit's Java implementation which detects SHA1 collisions if system property `org.eclipse.jgit.util.sha1.detectCollision` is unset or `true`. Set it to `jdkNative` to use the native implementation available in the JDK, can also be set using system property `org.eclipse.jgit.util.sha1.implementation`. If both are set the system property takes precedence. Performance of `jdkNative` is around 10% higher than `java` when `detectCollision=false` and 30% higher when `detectCollision=true`.|
| `core.streamFileThreshold` | `50 MiB` | &#x20DE; | The size threshold beyond which objects must be streamed. |
| `core.supportsAtomicFileCreation` | `true` | &#x20DE; | Whether the filesystem supports atomic file creation. |
| `core.symlinks` | Auto detect if filesystem supports symlinks| &#x2705; | If false, symbolic links are checked out as small plain files that contain the link text. |
| ~~`core.trustFolderStat`~~ | `true` | &#x20DE; | __Deprecated__, use `core.trustStat` instead. If set to `true` translated to `core.trustStat=always`, if `false` translated to `core.trustStat=never`, see below. If both `core.trustFolderStat` and `core.trustStat` are configured then `trustStat` takes precedence and `trustFolderStat` is ignored. |
| `core.trustLooseRefStat` | `inherit` | &#x20DE; | Whether to trust the file attributes of loose refs and its fan-out parent directory. See `core.trustStat` for possible values. If `inherit`, JGit will use the behavior configured in `trustStat`. |
| `core.trustPackedRefsStat` | `inherit` | &#x20DE; | Whether to trust the file attributes of the packed-refs file. See `core.trustStat` for possible values. If `inherit`, JGit will use the behavior configured in `core.trustStat`. __Note:__ since 6.10.2, this setting applies during both ref reads and ref updates, but previously only applied during reads.|
| `core.trustTablesListStat` | `inherit` | &#x20DE; | Whether to trust the file attributes of the `tables.list` file used by the reftable ref storage backend to store the list of reftable filenames. See `core.trustStat` for possible values. If `inherit`, JGit will use the behavior configured in `core.trustStat`.  The reftable backend is used if `extensions.refStorage = reftable`. |
| `core.trustLooseObjectStat` | `inherit` | &#x20DE; | Whether to trust the file attributes of the loose object file and its fan-out parent directory. See `core.trustStat` for possible values. If `inherit`, JGit will use the behavior configured in `core.trustStat`. |
| `core.trustPackStat` | `inherit` | &#x20DE; | Whether to trust the file attributes of the `objects/pack` directory. See `core.trustStat` for possible values. If `inherit`, JGit will use the behavior configured in `core.trustStat`. |
| `core.trustStat` | `always` | &#x20DE; | Global option to configure whether to trust file attributes (Java equivalent of stat command on Unix) of files storing git objects. Can be overridden for specific files by configuring `core.trustLooseRefStat, core.trustPackedRefsStat, core.trustLooseObjectStat, core.trustPackStat,core.trustTablesListStat`. If `never` JGit will ignore the file attributes of the file and always read it. If `always` JGit will trust the file attributes and will only read it if a file attribute has changed. `after_open` behaves the same as `always`, but file attributes are only considered *after* the file itself and any transient parent directories have been opened and closed. An open/close of the file/directory is known to refresh its file attributes, at least on some NFS clients. |
| `core.worktree` | Root directory of the working tree if it is not the parent directory of the `.git` directory | &#x2705; | The path to the root of the working tree. |

## __fetch__ options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `fetch.useNegotiationTip` | `false` | &#x2705; | When enabled it restricts the client negotiation on unrelated branches i.e. only send haves for the refs that the client is interested in fetching. |

## __gc__ options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `gc.aggressiveDepth` | `50` | &#x2705; | The depth parameter used in the delta compression algorithm used by aggressive garbage collection. |
| `gc.aggressiveWindow` | `250` | &#x2705; | The window size parameter used in the delta compression algorithm used by aggressive garbage collection. |
| `gc.auto` | `6700` | &#x2705; | Number of loose objects until auto gc combines all loose objects into a pack and consolidates all existing packs into one. Setting to 0 disables automatic packing of loose objects. |
| `gc.autoDetach` | `true` |  &#x2705; | Make auto gc return immediately and run in background. |
| `gc.autoPackLimit` | `50` |  &#x2705; | Number of packs until auto gc consolidates existing packs (except those marked with a .keep file) into a single pack. Setting `gc.autoPackLimit` to 0 disables automatic consolidation of packs. |
| `gc.logExpiry` | `1.day.ago` | &#x2705; | If the file `gc.log` exists, then auto gc will print its content and exit successfully instead of running unless that file is more than `gc.logExpiry` old. |
| `gc.packRefs`| `true` | &#x2705; | This variable determines whether JGit garbage collection will also pack refs. This can be set to `notbare` to enable it within all non-bare repos or it can be set to a boolean value. |
| `gc.pruneExpire` | `2.weeks.ago` | &#x2705; | Grace period after which unreachable objects will be pruned. |
| `gc.prunePackExpire` | `1.hour.ago` |  &#x20DE; | Grace period after which packfiles containing only unreachable objects will be pruned. |
| `gc.writeChangedPaths` | `false`| &#x20DE; | Whether bloom filter should be written to commit-graph during a gc operation. |
| `gc.writeCommitGraph`| `false` | &#x20DE; | If true, then gc will rewrite the commit-graph file when jgit gc is run. |

## __http__ options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `http.cookieFile`| | &#x2705; | Absolute path (with tilde expansion) of a cookie file in Netscape format. |
| `http.cookieFileCacheLimit`| 10 | &#x20DE; | JGit caches at most this number of the most recently used cookie files. |
| `http.extraHeader`|  | &#x2705; | Extra HTTP header(s) to send with HTTP requests, in the format "`Key: Value`". May appear multiple times; an empty option clears the list. |
| `http.followRedirects`| `initial` | &#x2705; | `true`, `false`, or `initial`. Whether to follow a redirect always, never, or only on the first HTTP request in a git remote operation. |
| `http.maxRedirects`| 5 | &#x20DE; | Maximum number of redirects to follow; can be overridden via the Java system property `http.maxRedirects`. |
| `http.postBuffer`| `1 MiB` | &#x2705; | Maximum size in bytes for single HTTP POST requests; for larger requests, HTTP 1.1 chunked transfer is used. |
| `http.saveCookies`| `false` | &#x2705; | Boolean; if `true` and `http.cookieFile` is set, save received cookies. |
| `http.sslVerify`| `true` | &#x2705; | Boolean; whether to check SSL certificates in HTTPS connections. |
| `http.userAgent`| | &#x2705; | User-agent string to send with HTTP requests. Must be 7bit-ASCII. Can be overridden via environment variable `GIT_HTTP_USER_AGENT`. |

All `http.*` options can also be specified in a URL-specific way using the format `http.<url>.*`. See the official [git config documentation](https://git-scm.com/docs/git-config#Documentation/git-config.txt-httplturlgt) for details.

Proxy configuration uses the standard Java mechanisms via class `java.net.ProxySelector`.

## __pack__ options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `pack.bitmapContiguousCommitCount` | `100` | &#x20DE; | Count of most recent commits for which to build bitmaps. |
| `pack.bitmapDistantCommitSpan` | `5000` | &#x20DE; | Span of commits when building bitmaps for distant history. |
| `pack.bitmapExcessiveBranchCount` | `100` | &#x20DE; | The count of branches deemed "excessive". If the count of branches in a repository exceeds this number and bitmaps are enabled, "inactive" branches will have fewer bitmaps than "active" branches. |
| `pack.bitmapExcludedRefsPrefixes` | | &#x20DE; | The refs prefixes to be excluded when building bitmaps. May be specified more than once to exclude multiple prefixes. |
| `pack.bitmapInactiveBranchAgeInDays` | `90` | &#x20DE; | Age in days that marks a branch as "inactive" for bitmap creation. |
| `pack.bitmapRecentCommitCount` | `20000`  | &#x20DE; | Count at which to switch from `bitmapRecentCommitSpan` to `bitmapDistantCommitSpan`. |
| `pack.bitmapRecentCommitSpan` | `100` | &#x20DE; | Span of commits when building bitmaps for recent history. |
| `pack.buildBitmaps` | `true` | &#x20DE; synonym for `repack.writeBitmaps` | Whether index writer is allowed to build bitmaps for indexes. |
| `pack.compression` | `core.compression` | &#x2705; | Compression level applied to objects in the pack. |
| `pack.cutDeltaChains` | `false` | &#x20DE; | Whether existing delta chains should be cut at {@link #getMaxDeltaDepth() |
| `pack.deltaCacheLimit` | `100` | &#x2705; | Maximum size in bytes of a delta to cache. |
| `pack.deltaCacheSize` | `50 MiB` | &#x2705; | Size of the in-memory delta cache. |
| `pack.deltaCompression` | `true` | &#x20DE; | Whether the writer will create new deltas on the fly. `true` if the pack writer will create a new delta when either `pack.reuseDeltas` is false, or no suitable delta is available for reuse. |
| `pack.depth` | `50` | &#x2705; | Maximum depth of delta chain set up for the pack writer. |
| `pack.indexVersion` | `2` | &#x2705; | Pack index file format version. |
| `pack.minBytesForObjSizeIndex` | `-1` | &#x20DE; | Minimum size of an object (inclusive, in bytes) to be included in the size index. -1 to disable the object size index. |
| `pack.minSizePreventRacyPack` | `100 MiB` | &#x20DE; | Minimum packfile size for which we wait before opening a newly written pack to prevent its lastModified timestamp could be racy if `pack.waitPreventRacyPack` is `true`. |
| `pack.preserveOldPacks` | `false` | &#x20DE; | Whether to preserve old packs during gc in the `objects/pack/preserved` directory. This can avoid rare races between gc removing pack files and other concurrent operations. If this option is false data loss can occur in rare cases when an object is believed to be unreferenced when object repacking is running, and then garbage collection deletes it while another concurrent operation references this object shortly before garbage collection deletes it. When this happens, a new reference is created which points to a now missing object. |
| `pack.prunePreserved` | `false` | &#x20DE; | Whether to prune preserved pack files from the previous run of gc from the `objects/pack/preserved` directory. This helps to limit the additional storage space needed to preserve old packs when `pack.preserveOldPacks = true`. |
| `pack.reuseDeltas` | `true` |&#x20DE; | Whether to reuse deltas existing in repository. |
| `pack.reuseObjects` | `true` | &#x20DE; | Whether to reuse existing objects representation in repository. |
| `pack.searchForReuseTimeout` | | &#x20DE; | Search for reuse phase timeout. Expressed as a `Duration`, i.e.: `50sec`. |
| `pack.singlePack` | `false` | &#x20DE; | Whether all of `refs/*` should be packed in a single pack. |
| `pack.threads` | `0` (auto-detect number of processors) | &#x2705; | Number of threads to use for delta compression. |
| `pack.useObjectSizeIndex` | `false` | &#x20DE; | Whether to use the object size index when available. |
| `pack.waitPreventRacyPack` | `false` | &#x20DE; | Whether we wait before opening a newly written pack to prevent its lastModified timestamp could be racy. |
| `pack.window` | `10` | &#x2705; | Number of objects to try when looking for a delta base per thread searching for deltas. |
| `pack.windowMemory` | `0` (unlimited) | &#x2705; | Maximum number of bytes to put into the delta search window. |

## reftable options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `reftable.autoRefresh` | `false` | &#x20DE; | Whether to auto-refresh the reftable stack if it is out of date. |


## __repack__ options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `repack.packKeptObjects` | `true` when `pack.buildBitmaps` is set, `false` otherwise | &#x2705; | Include objects in packs locked by a `.keep` file when repacking. |


## Java System Properties

| system property | default | description |
|-----------------|---------|-------------|
| `REVWALK_USE_PRIORITY_QUEUE` | `false` | If set to `true` `RevWalk` uses `DateRevPriorityQueue` which is faster, otherwise it uses the old `DateRevQueue`. |

## Tracing

**GIT_TRACE_PERFORMANCE**: set this to `true` as a Java system property or environment variable to trace timings from the progress monitor. The system property takes
precedence. Defaults to `false`. Can also be set programmatically via `ProgressMonitor#showDuration`.

*Example using JGit CLI:*

```bash
$ GIT_TRACE_PERFORMANCE=true jgit clone https://foo.bar/foobar
Cloning into 'foobar'...
remote: Counting objects: 1 [0.002s]
remote: Finding sources: 100% (15531/15531) [0.006s]
Receiving objects:      100% (169737/169737) [13.045s]
Resolving deltas:       100% (67579/67579) [1.842s]
```
