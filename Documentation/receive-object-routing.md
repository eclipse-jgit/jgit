# Receiving objects with loose mirroring

File-backed servers can opt into **mirroring** selected received objects into
one explicitly supplied alternate object directory. Mirroring first imports
the complete incoming pack through the ordinary `ObjectDirectoryPackParser`,
including validation, delta resolution, thin-pack repair, indexing and main
pack installation. It then adds loose alternate copies of the selected
received objects. **Selected objects remain in the main pack.** This is
intentional duplication, consumes extra storage, and is not exclusive
offloading or historical-object migration.

## Enablement and destination

Default receives are unchanged and do not mirror:

```java
ReceivePack receiver = new ReceivePack(repository);
```

V1 provides programmatic configuration only. It adds no repository-config
boolean, destination-path or filter keys. An application must supply all of:

* A file-backed main `ObjectDirectory`.
* One explicit destination `ObjectDirectory`, already configured as a **direct**
  alternate of the main database. Configure visibility before opening/using the
  receive database, through the ordinary `objects/info/alternates` file or
  repository builder alternate paths. The destination denotes an `objects`
  directory, not a repository's Git directory.
* A deterministic `ReceivedObjectFilter`.
* A protected `ReceivePack.newObjectInserter()` override returning
  `main.newMirroringInserter(destinationAlternate, filter)`.

The factory compares canonical directory identities. A distinct handle or a
path containing `.` can identify the configured destination; the main directory,
an unconfigured destination, and an indirect-only alternate are rejected.
Multiple alternates are supported, but their order never chooses a write target.
Only the supplied destination is written. There is no broadcast and no fallback
on failure. The factory does not configure alternates or create repository refs.

For example, an application can select blobs whose full size is **greater than
or equal to** an application-supplied threshold:

```java
// Both database handles belong to the server/application. The destination's
// objects directory is already a configured direct alternate of main.
ObjectDirectory main = (ObjectDirectory) repository.getObjectDatabase();
ObjectDirectory destinationAlternate = sharedRepository.getObjectDatabase();
long minimumBlobBytes = 1024 * 1024;
ReceivedObjectFilter filter = (id, type, inflatedSize) ->
        type == Constants.OBJ_BLOB && inflatedSize >= minimumBlobBytes;

ReceivePack receiver = new ReceivePack(repository) {
    @Override
    protected ObjectInserter newObjectInserter() throws IOException {
        return main.newMirroringInserter(destinationAlternate, filter);
    }
};
```

Here `sharedRepository` is a caller-owned `FileRepository`. Validate application
thresholds and destination authorization before constructing the receiver. Do
not select the first alternate as an implicit destination. The receive-only
inserter supports pack parsing and readers, not individual `insert(...)` calls.

## Selection and copying

The callback receives a resolved object ID, ordinary object type (never
`OFS_DELTA` or `REF_DELTA`), and full inflated object size in bytes, excluding
its Git object header. Contents and paths are not available for filtering.
Selection uses the **first reconstructed received representation** of each ID.
The callback is evaluated once per distinct received ID; later representations
must have the same resolved type and full size and cannot replace the first
selection. Bases borrowed from main storage or other alternates to repair a thin
pack are not received objects and are not selected merely because they were
appended to the main pack.

After successful normal import, an ordinary main `ObjectReader` supplies each
selected object through `ObjectLoader.openStream()`. The destination's loose
inserter uses bounded copy buffers and forced loose insertion, even when the
destination already has that ID in a pack. Returned IDs are checked before
success. Existing loose objects are left in place. Mirroring retains only
per-ID metadata, not a cache of all reconstructed contents; ordinary parser and
reader delta reconstruction and memory constraints still apply.

Copies use the destination handle's existing `core.compression` and loose-write
settings. Main pack installation remains the normal import path: incoming
compressed entries, including deltas, are not rewritten or recompressed by
mirroring. Existing normal thin-pack repair behavior is unchanged. There is no
staging object database, replay spool, new delta computation or alternate pack
output in this mode.

## Validation, reporting and resources

ReceivePack still configures normal parser flags, object checkers, incoming
limits, new/base-ID tracking, and thin-pack handling. Selection does not depend
on ReceivePack retaining parsed object IDs. Normal main `.pack` plus `.idx` size
reporting and incoming `ReceivedPackStatistics` are retained; alternate loose
copy bytes are not included in the reported main pack size or incoming object
counts. Cancellation is checked during copying, including between bounded
stream reads, using both supplied parser progress monitors. Receive input size
limits and timeouts remain in force for the input phase.

The server must budget main pack space **and** additional loose-copy space.
An incoming-pack size limit does not bound inflated/mirrored output disk use.
No all-object reconstructed-content cache is introduced, but normal delta
reconstruction can still require full objects in memory.

The caller owns the main and alternate database/repository handles and keeps
them open for the receive. Closing the mirroring inserter does not close those
handles. Parsing closes operation-owned readers, object streams and the loose
inserter. On success, the original normal parser's nullable `PackLock` is returned
to its caller. ReceivePack keeps it through ref processing and releases it in
its normal cleanup. An empty import or an already-existing main pack may return
no lock; an existing foreign keep is not acquired or released by mirroring.

## Failure and deployment responsibilities

A mirror copy, flush, or cancellation failure rejects receive ref updates.
The valid normally installed main pack is **not deleted**. Loose objects already
published by this receive are also **not deleted as rollback**; some selected
copies may therefore remain even though refs did not change. Any keep owned by
this parse is released if copying fails before its lock can be returned to
ReceivePack, without touching foreign keeps. Parser failures before normal
installation use the normal parser's cleanup and do not start copying.

The server owns alternate permissions, visibility, disk quotas, GC coordination
and long-term retention. An alternate's own refs do not automatically retain
objects borrowed by other repositories. Coordinate shared-store GC and retain
borrowed objects while any borrowing repository needs them. Mirroring retains
main copies, but does not establish a shared-store retention policy, move old
main copies, or control where future repacks place objects. Those are separate
features; do not treat mirroring as completed offloading.
