# JGit configuration options

## Legend

| git option | description |
|------------|-------------|
| &#x2705; | option defined by native git |
| &#x20DE; | jgit custom option not supported by native git |

## __core__ options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `core.bigFileThreshold` | `52428800` (50 MiB) | &#x2705; | Maximum file size that will be delta compressed. Files larger than this size are stored deflated, without attempting delta compression. |
| `core.compression` | `-1` (default compression) | &#x2705; | An integer -1..9, indicating a default compression level. -1 is the zlib default. 0 means no compression, and 1..9 are various speed/size tradeoffs, 9 being slowest.|

## __gc__ options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `gc.aggressiveDepth` | 50 | &#x2705; | The depth parameter used in the delta compression algorithm used by aggressive garbage collection. |
| `gc.aggressiveWindow` | 250 | &#x2705; | The window size parameter used in the delta compression algorithm used by aggressive garbage collection. |
| `gc.auto` | `6700` | &#x2705; | Number of loose objects until auto gc combines all loose objects into a pack and consolidates all existing packs into one. Setting to 0 disables automatic packing of loose objects. |
| `gc.autoDetach` | `true` |  &#x2705; | Make auto gc return immediately and run in background. |
| `gc.autoPackLimit` | `50` |  &#x2705; | Number of packs until auto gc consolidates existing packs (except those marked with a .keep file) into a single pack. Setting `gc.autoPackLimit` to 0 disables automatic consolidation of packs. |
| `gc.logExpiry` | `1.day.ago` | &#x2705; | If the file `gc.log` exists, then auto gc will print its content and exit successfully instead of running unless that file is more than `gc.logExpiry` old. |
| `gc.pruneExpire` | `2.weeks.ago` | &#x2705; | Grace period after which unreachable objects will be pruned. |
| `gc.prunePackExpire` | `1.hour.ago` |  &#x20DE; | Grace period after which packfiles only containing unreachable objects will be pruned. |

## __pack__ options

|  option | default | git option | description |
|---------|---------|------------|-------------|
| `pack.bitmapContiguousCommitCount` | `100` | &#x20DE; | Count of most recent commits for which to build bitmaps. |
| `pack.bitmapDistantCommitSpan` | `5000` | &#x20DE; | Span of commits when building bitmaps for distant history. |
| `pack.bitmapExcessiveBranchCount` | `100` | &#x20DE; | The count of branches deemed "excessive". If the count of branches in a repository exceeds this number and bitmaps are enabled, "inactive" branches will have fewer bitmaps than "active" branches. |
| `pack.bitmapInactiveBranchAgeInDays` | `90` | &#x20DE; | Age in days that marks a branch as "inactive" for bitmap creation. |
| `pack.bitmapRecentCommitCount` | `20000`  | &#x20DE; | Count at which to switch from `bitmapRecentCommitSpan` to `bitmapDistantCommitSpan`. |
| `pack.bitmapRecentCommitSpan` | `100` | &#x20DE; | Span of commits when building bitmaps for recent history. |
| `pack.buildBitmaps` | `true` | &#x20DE; synonym for `repack.writeBitmaps` | Whether index writer is allowed to build bitmaps for indexes. |
| `pack.compression` | `core.compression` | &#x2705; | Compression level applied to objects in the pack. |
| `pack.cutDeltaChains` | `false` | &#x20DE; | Whether existing delta chains should be cut at {@link #getMaxDeltaDepth() |
| `pack.deltaCacheLimit` | `100` | &#x2705; | Maximum size in bytes of a delta to cache. |
| `pack.deltaCacheSize` | `52428800` (50 MiB) | &#x2705; | Size of the in-memory delta cache. |
| `pack.deltaCompression` | `true` | &#x20DE; | Whether the writer will create new deltas on the fly. `true` if the pack writer will create a new delta when either `pack.reuseDeltas` is false, or no suitable delta is available for reuse. |
| `pack.depth` | `50` | &#x2705; | Maximum depth of delta chain set up for the pack writer. |
| `pack.indexVersion` | `2` | &#x2705; | Pack index file format version. |
| `pack.minSizePreventRacyPack` | `104857600` (100 MiB) | &#x20DE; | Minimum packfile size for which we wait before opening a newly written pack to prevent its lastModified timestamp could be racy if `pack.waitPreventRacyPack` is `true`. |
| `pack.preserveOldPacks` | `false` | &#x20DE; | Whether to preserve old packs in a preserved directory. |
| `prunePreserved`, only via API of PackConfig | `false` | &#x20DE; | Whether to remove preserved pack files in a preserved directory. |
| `pack.reuseDeltas` | `true` |&#x20DE; | Whether to reuse deltas existing in repository. |
| `pack.reuseObjects` | `true` | &#x20DE; | Whether to reuse existing objects representation in repository. |
| `pack.singlePack` | `false` | &#x20DE; | Whether all of `refs/*` should be packed in a single pack. |
| `pack.threads` | `0` (auto-detect number of processors) | &#x2705; | Number of threads to use for delta compression. |
| `pack.waitPreventRacyPack` | `false` | &#x20DE; | Whether we wait before opening a newly written pack to prevent its lastModified timestamp could be racy. |
| `pack.window` | `10` | &#x2705; | Number of objects to try when looking for a delta base per thread searching for deltas. |
| `pack.windowMemory` | `0` (unlimited) | &#x2705; | Maximum number of bytes to put into the delta search window. |
