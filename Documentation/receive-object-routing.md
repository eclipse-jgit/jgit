# Receiving objects with exclusive routing or loose mirroring

File-backed servers can opt into either of two explicit receive modes:

* **Exclusive routing**, through `newRoutingInserter(destination, filter)`,
  writes selected received objects loose in one supplied alternate and excludes
  their IDs from the newly produced main pack, including supporting bases.
  Retained objects are emitted whole in at most one self-contained main pack.
  Empty or all-selected receives produce no main pack.
* **Mirroring**, through `newMirroringInserter(destination, filter)`, first
  imports the complete incoming pack through the ordinary
  `ObjectDirectoryPackParser`, including validation, delta resolution, thin-pack
  repair, indexing and main installation. It then adds loose alternate copies.
  **Selected objects remain in the main pack.** This intentional duplication
  consumes extra storage and is not exclusive offloading.

Neither mode migrates or deletes historical main copies, establishes a
shared-store retention policy, or controls placement by future repacks.

## Enablement and destination

Default receives are unchanged and use neither mode:

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
* A protected `ReceivePack.newObjectInserter()` override returning the chosen
  factory's inserter.

Both factories compare canonical directory identities. A distinct handle or a
path containing `.` can identify the configured destination; the main directory,
an unconfigured destination, and an indirect-only alternate are rejected.
Multiple alternates are supported, but their order never chooses a write target.
Only the supplied destination is written. There is no broadcast and no fallback
on failure. The routing factory also checks that the destination can be opened
for writes before accepting input. Neither factory configures alternates or
creates repository refs.

For example, an application can exclusively route blobs whose full size is
**greater than or equal to** an application-supplied threshold:

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
        return main.newRoutingInserter(destinationAlternate, filter);
    }
};
```

Here `sharedRepository` is a caller-owned `FileRepository`. To retain normal main
copies while adding loose alternate copies, return
`main.newMirroringInserter(destinationAlternate, filter)` instead. Validate
application thresholds and destination authorization before constructing the
receiver. Do not select the first alternate as an implicit destination. Both
receive-only inserters support one pack parser and ordinary main readers, not
individual `insert(...)` calls. Flush and close never publish another pack.

## Selection

The callback receives a resolved object ID, ordinary object type (never
`OFS_DELTA` or `REF_DELTA`), and full inflated object size in bytes, excluding
its Git object header. Contents and paths are not available for filtering.
Selection uses the **first reconstructed received representation** of each ID.
The callback is evaluated once per distinct received ID; later representations
must have the same resolved type and full size and cannot replace the first
selection or source metadata. Bases borrowed from main storage or other
alternates to resolve a thin pack are not received objects and are not selected
merely because parsing needs them.

Selected objects use forced loose insertion, even when the destination already
has that ID in a pack. Returned IDs are checked before success. Existing loose
objects are left in place. Writes use the destination handle's existing
`core.compression` and loose-write settings.

### Exclusive routing

The incoming bytes are archived in an **unindexed, unregistered temporary
spool**, not a staging object database. Ordinary parser validation and delta
resolution operate against that archive and the configured read alternates.
Selected whole objects are replayed through bounded streams after input
validation, with original CRC, exact inflated length and returned-ID checks.
Resolved selected deltas are written loose directly from the parser's existing
reconstructed array. No all-object reconstructed-content cache is retained.

Every retained received object is currently emitted **whole**. Retained deltas
are expanded immediately using the reconstructed array; retained original whole
entries are streamed from the archive into the output writer. External bases
are never copied or appended to output, and selected local bases are never
introduced to support a retained delta. The resulting main pack is
self-contained without depending on selected or external delta bases. This
whole-only implementation does not reuse incoming compressed payloads,
preserve input deltas or compute new deltas, so its output may be substantially
larger than the incoming pack.

Whole main output uses the main handle's `pack.compression`, falling back to
`core.compression`. The final pack count, object CRCs, checksum and index are
computed for the new output; incoming entry metadata and statistics remain
separate. After all selected writes succeed, the alternate is flushed before
main publication. A final-name keep is created without overwriting any foreign
keep before the main pack becomes visible. Only operation-owned output files
are installed and registered; existing final packs and sidecars are not
replaced. Closing the spool or writer without successful parsing only aborts.

### Mirroring

After successful normal import, an ordinary main `ObjectReader` supplies each
selected object through `ObjectLoader.openStream()`. The destination's loose
inserter uses bounded copy buffers. Mirroring retains only per-ID metadata,
not a cache of all reconstructed contents; ordinary parser and reader delta
reconstruction and memory constraints still apply.

Main pack installation remains the normal import path: incoming compressed
entries, including deltas, are not rewritten or recompressed by mirroring.
Normal thin-pack repair is unchanged, including appending borrowed bases when
required. There is no staging object database, replay spool, new delta
computation or alternate pack output in this mode.

## Validation, reporting and resources

ReceivePack still configures parser flags, object checkers, incoming limits,
new/base-ID tracking and thin-pack handling. Selection does not depend on
ReceivePack retaining parsed object IDs. Ordinary primary reads see selected
objects through the preconfigured alternate after receive.

Exclusive routing's `getPackSize()` reports only final main `.pack` plus `.idx`
bytes, or zero without main output. Mirroring retains normal main pack/index
reporting. Neither includes alternate loose bytes or optional object-size-index
bytes in the reported main size. Incoming `ReceivedPackStatistics` describe the
received encoding and input bytes, not expanded main output or alternate
copies. Cancellation is checked using both supplied parser progress monitors
through copying/replay and bounded output reads. Receive input limits and
timeouts remain in force for the input phase.

The server must budget incoming spool space, whole-expanded main output and
alternate loose output for routing, or normal main pack space **and** additional
loose-copy space for mirroring. An incoming-pack size limit does not bound
inflated/output disk use. No all-object reconstructed-content cache is
introduced, but normal delta reconstruction can still require full objects in
memory.

The caller owns the main and alternate database/repository handles and keeps
them open for receive. Closing either receive inserter does not close those
handles. Parsing closes operation-owned readers, streams, loose inserters and
temporaries; closing an unused routing parser also releases its resources.
On success the nullable `PackLock` belongs to the caller. ReceivePack keeps it
through ref processing and releases it in normal cleanup. Empty/all-selected
routing or an already-existing main pack can return no lock. An existing
foreign keep is never acquired or released by routing; mirroring retains its
normal parser's lock behavior. A main keep does **not** protect alternate loose
objects from alternate GC.

## Failure and deployment responsibilities

Routing validation, alternate insertion/flush, replay, cancellation or main
publication failures reject receive ref updates. Temporary input/output files
and any final files installed by a failed publication are cleaned up only when
owned by this operation. The writer never removes foreign packs, indexes or
keeps. Loose objects already published by the receive are **not deleted as
rollback**; some selected objects may remain even when there is no main output
and refs did not change. If publication succeeds but later ref processing
rejects a command, the valid main pack and loose objects remain, while
ReceivePack releases its owned keep.

A mirroring copy, flush or cancellation failure also rejects receive ref
updates. Its valid normally installed main pack is **not deleted**, and already
published loose copies are not rolled back. Any keep owned by the parse is
released if copying fails before the lock can be returned to ReceivePack,
without touching foreign keeps. Parser failures before normal installation use
the normal parser's cleanup and do not start copying.

The server owns alternate permissions, visibility, disk quotas, GC coordination
and long-term retention. An alternate's own refs do not automatically retain
objects borrowed by other repositories. Coordinate shared-store GC and retain
borrowed objects while any borrowing repository needs them. Historical
offloading and future repack placement are separate features. Mirroring retains
main copies, whereas exclusive routing relies on correct alternate retention
for its selected objects.
