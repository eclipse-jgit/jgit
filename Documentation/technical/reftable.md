# reftable

[TOC]

## Overview

### Problem statement

Some repositories contain a lot of references (e.g.  android at 866k,
rails at 31k).  The existing packed-refs format takes up a lot of
space (e.g.  62M), and does not scale with additional references.
Lookup of a single reference requires linearly scanning the file.

Atomic pushes modifying multiple references require copying the
entire packed-refs file, which can be a considerable amount of data
moved (e.g. 62M in, 62M out) for even small transactions (2 refs
modified).

Repositories with many loose references occupy a large number of disk
blocks from the local file system, as each reference is its own file
storing 41 bytes (and another file for the corresponding reflog).
This negatively affects the number of inodes available when a large
number of repositories are stored on the same filesystem.  Readers can
be penalized due to the larger number of syscalls required to traverse
and read the `$GIT_DIR/refs` directory.

### Objectives

- Near constant time lookup for any single reference, even when the
  repository is cold and not in process or kernel cache.
- Near constant time verification if a SHA-1 is referred to by at
  least one reference (for allow-tip-sha1-in-want).
- Efficient lookup of an entire namespace, such as `refs/tags/`.
- Support atomic push with `O(size_of_update)` operations.
- Combine reflog storage with ref storage for small transactions.
- Separate reflog storage for base refs and historical logs.

### Description

A reftable file is a portable binary file format customized for
reference storage. References are sorted, enabling linear scans,
binary search lookup, and range scans.

Storage in the file is organized into blocks.  Prefix compression
is used within a single block to reduce disk space.  Block size is
tunable by the writer.

### Performance

Space used, packed-refs vs. reftable:

repository | packed-refs | reftable | % original | avg ref  | avg obj
-----------|------------:|---------:|-----------:|---------:|--------:
android    |      62.2 M |   34.4 M |     55.2%  | 33 bytes | 8 bytes
rails      |       1.8 M |    1.1 M |     57.7%  | 29 bytes | 6 bytes
git        |      78.7 K |   44.0 K |     60.0%  | 50 bytes | 6 bytes
git (heads)|       332 b |    274 b |     83.1%  | 34 bytes | 0 bytes

Scan (read 866k refs), by reference name lookup (single ref from 866k
refs), and by SHA-1 lookup (refs with that SHA-1, from 866k refs):

format      | cache | scan    | by name        | by SHA-1
------------|------:|--------:|---------------:|---------------:
packed-refs | cold  |  402 ms | 409,660.1 usec | 412,535.8 usec
packed-refs | hot   |         |   6,844.6 usec |  20,110.1 usec
reftable    | cold  |  112 ms |      33.9 usec |     323.2 usec
reftable    | hot   |         |      20.2 usec |     320.8 usec

Space used for 149,932 log entries for 43,061 refs,
reflog vs. reftable:

format        | size  | avg entry
--------------|------:|-----------:
$GIT_DIR/logs | 173 M | 1209 bytes
reftable      |   5 M |   37 bytes

## Details

### Peeling

References stored in a reftable are peeled, a record for an annotated
(or signed) tag records both the tag object, and the object it refers
to.

### Reference name encoding

Reference names are an uninterpreted sequence of bytes that must pass
[git-check-ref-format][ref-fmt] as a valid reference name.

[ref-fmt]: https://git-scm.com/docs/git-check-ref-format

### Network byte order

All multi-byte, fixed width fields are in network byte order.

### Ordering

Blocks are lexicographically ordered by their first reference.

### Directory/file conflicts

The reftable format accepts both `refs/heads/foo` and
`refs/heads/foo/bar` as distinct references.

This property is useful for retaining log records in reftable, but may
confuse versions of Git using `$GIT_DIR/refs` directory tree to
maintain references.  Users of reftable may choose to continue to
reject `foo` and `foo/bar` type conflicts to prevent problems for
peers.

## File format

### Structure

A reftable file has the following high-level structure:

    first_block {
      header
      first_ref_block
    }
    ref_blocks*
    ref_index*
    obj_blocks*
    obj_index*
    log_blocks*
    log_index*
    footer

A log-only file omits the `ref_blocks`, `ref_index`, `obj_blocks` and
`obj_index` sections, containing only the file header and log blocks:

    first_block {
      header
    }
    log_blocks*
    log_index*
    footer

in a log-only file the first log block immediately follows the file
header, without padding to block alignment.

### Block size

The `block_size` is arbitrarily determined by the writer, and does not
have to be a power of 2.  The block size must be larger than the
longest reference name or log entry used in the repository, as
references cannot span blocks.

Powers of two that are friendly to the virtual memory system or
filesystem (such as 4k or 8k) are recommended.  Larger sizes (64k) can
yield better compression, with a possible increased cost incurred by
readers during access.

The largest block size is `16777215` bytes (15.99 MiB).

### Header

A 24-byte header appears at the beginning of the file:

    'REFT'
    uint8( version_number = 1 )
    uint24( block_size )
    uint64( min_update_index )
    uint64( max_update_index )

The `min_update_index` and `max_update_index` describe bounds for the
`update_index` field of all log records in this file.  When reftables
are used in a stack for transactions (see below), these fields can
order the files such that the prior file's `max_update_index + 1` is
the next file's `min_update_index`.

### First ref block

The first ref block shares the same block as the file header, and is
24 bytes smaller than all other blocks in the file.  The first block
immediately begins after the file header, at position 24.

If the first block is a log block (a log-only file), its block header
begins immediately at position 24.

### Ref block format

A ref block is written as:

    'r'
    uint24( block_len )
    ref_record+
    uint24( restart_offset )+
    uint16( restart_count )
    padding?

Blocks begin with `block_type = 'r'` and a 3-byte `block_len` which
encodes the number of bytes in the block up to, but not including the
optional `padding`.  This is almost always shorter than the file's
`block_size`.  In the first ref block, `block_len` includes 24 bytes
for the file header.

The 2-byte `restart_count` stores the number of entries in the
`restart_offset` list, which must not be empty.  Readers can use
`restart_count` to binary search between restarts before starting a
linear scan.

A variable number of 3-byte `restart_offset` follows.  Offsets are
relative to the start of the block and refer to the first byte of any
`ref_record` whose name has not been prefix compressed.  Entries in
the `restart_offset` list must be sorted, ascending.  Readers can
start linear scans from any of these records.

A variable number of `ref_record` fill the middle of the block,
describing reference names and values.  The format is described below.

As the first ref block shares the first file block with the file
header, all `restart_offset` in the first block are relative to the
start of the file (position 0), and include the file header.

The end of the block may be filled with `padding` NUL bytes to fill
out the block to the common `block_size` as specified in the file
header.  Padding may be necessary to ensure the following block starts
at a block alignment, and does not spill into the tail of this block.
Padding may be omitted if the block is the last block of the file, and
there is no index block.  This allows reftable to efficiently scale
down to a small number of refs.

#### ref record

A `ref_record` describes a single reference, storing both the name and
its value(s). Records are formatted as:

    varint( prefix_length )
    varint( (suffix_length << 3) | value_type )
    suffix
    value?

The `prefix_length` field specifies how many leading bytes of the
prior reference record's name should be copied to obtain this
reference's name.  This must be 0 for the first reference in any
block, and also must be 0 for any `ref_record` whose offset is listed
in the `restart_offset` table at the end of the block.

Recovering a reference name from any `ref_record` is a simple concat:

    this_name = prior_name[0..prefix_length] + suffix

The `suffix_length` value provides the number of bytes available in
`suffix` to copy from `suffix` to complete the reference name.

The `value` follows.  Its format is determined by `value_type`, one of
the following:

- `0x0`: deletion; no value data (see transactions, below)
- `0x1`: one 20-byte object id; value of the ref
- `0x2`: two 20-byte object ids; value of the ref, peeled target
- `0x3`: symref and text: `varint( text_len ) text`

Symbolic references use `0x3` with a `text` string starting with `"ref: "`,
followed by the complete name of the reference target.  No
compression is applied to the target name.  Other types of contents
that are also reference-like, such as `FETCH_HEAD` and `MERGE_HEAD`,
may also be stored using type `0x3`.

Types `0x4..0x7` are reserved for future use.

### Ref index

The ref index stores the name of the last reference from every ref
block in the file, enabling reduced disk seeks for lookups.  Any
reference can be found by searching the index, identifying the
containing block, and searching within that block.

The index may be organized into a multi-level index, where the 1st
level index block points to additional ref index blocks (2nd level),
which may in turn point to either additional index blocks (e.g. 3rd
level) or ref blocks (leaf level).  Disk reads required to access a
ref go up with higher index levels.  Multi-level indexes may be
required to ensure no single index block exceeds the file format's max
block size of `16777215` bytes (15.99 MiB).  To acheive constant O(1)
disk seeks for lookups the index must be a single level, which is
permitted to exceed the file's configured `block_size`, but not the
format's max block size of 15.99 MiB.

If present, the ref index block(s) appears after the last ref block.
The prior ref block should be padded to ensure the ref index starts on
a block alignment.

If there are at least 4 ref blocks, a ref index block should be
written to improve lookup times.  Cold reads using the index require
2 disk reads (read index, read block), and binary searching < 4 blocks
also requires <= 2 reads.  Omitting the index block from smaller files
saves space.

Index block format:

    'i'
    uint24( block_len )
    index_record+
    uint24( restart_offset )+
    uint16( restart_count )
    padding?

The index blocks begin with `block_type = 'i'` and a 3-byte
`block_len` which encodes the number of bytes in the block,
up to but not including the optional `padding`.

The `restart_offset` and `restart_count` fields are identical in
format, meaning and usage as in ref blocks.

To reduce the number of reads required for random access in very large
files the index block may be larger than the other blocks.  However,
readers must hold the entire index in memory to benefit from this, so
it's a time-space tradeoff in both file size and reader memory.

Increasing the file's `block_size` decreases the index size.
Alternatively a multi-level index may be used, keeping index blocks
within the file's `block_size`, but increasing the number of blocks
that need to be accessed.

When object blocks are present the ref index block is padded with
`padding` NULs to maintain alignment for the next block.  No padding
is necessary if log blocks or the file trailer follows the ref index.

#### index record

An index record describes the last entry in another block.
Index records are written as:

    varint( prefix_length )
    varint( (suffix_length << 3) | 0 )
    suffix
    varint( block_position )

Index records use prefix compression exactly like `ref_record`.

Index records store `block_position` after the suffix, specifying the
absolute position in bytes (from the start of the file) of the block
that ends with this reference. Readers can seek to `block_position` to
begin reading the block header.

Readers must examine the block header at `block_position` to determine
if the next block is another level index block, or the leaf-level ref
block.

#### Reading the index

Readers loading the ref index must first read the footer (below) to
obtain `ref_index_position`. If not present, the position will be 0.
The `ref_index_position` address is for the 1st level root of the ref
index.

### Obj block format

Object blocks use unique, abbreviated 2-20 byte SHA-1 keys, mapping
to ref blocks containing references pointing to that object directly,
or as the peeled value of an annotated tag.  Like ref blocks, object
blocks use the file's standard `block_size`. The abbrevation length is
available in the footer as `obj_id_len`.

To save space in small files, object blocks may be omitted if the ref
index is not present, as brute force search will only need to read a
few ref blocks.  When missing, readers should brute force a linear
search of all references to lookup by SHA-1.

An object block is written as:

    'o'
    uint24( block_len )
    obj_record+
    uint24( restart_offset )+
    uint16( restart_count )
    padding?

Fields are identical to ref block.  Binary search using the restart
table works the same as in reference blocks.

Because object identifiers are abbreviated by writers to the shortest
unique abbreviation within the reftable, obj key lengths are variable
between 2 and 20 bytes.  Readers must compare only for common prefix
match within an obj block or obj index.

Object blocks should be block aligned, according to `block_size` from
the file header.  The `padding` field is filled with NULs to maintain
alignment for the next block.

#### obj record

An `obj_record` describes a single object abbreviation, and the blocks
containing references using that unique abbreviation:

    varint( prefix_length )
    varint( (suffix_length << 3) | cnt_3 )
    suffix
    varint( cnt_large )?
    varint( block_delta )*

Like in reference blocks, abbreviations are prefix compressed within
an obj block.  On large reftables with many unique objects, higher
block sizes (64k), and higher restart interval (128), a
`prefix_length` of 2 or 3 and `suffix_length` of 3 may be common in
obj records (unique abbreviation of 5-6 raw bytes, 10-12 hex digits).

Each record contains `block_count` number of block identifiers for ref
blocks.  For 1-7 blocks the block count is stored in `cnt_3`.  When
`cnt_3 = 0` the actual block count follows in a varint, `cnt_large`.

The use of `cnt_3` bets most objects are pointed to by only a single
reference, some may be pointed to by a couple of references, and very
few (if any) are pointed to by more than 7 references.

A special case exists when `cnt_3 = 0` and `cnt_large = 0`: there
are no `block_delta`, but at least one reference starts with this
abbreviation.  A reader that needs exact reference names must scan all
references to find which specific references have the desired object.
Writers should use this format when the `block_delta` list would have
overflowed the file's `block_size` due to a high number of references
pointing to the same object.

The first `block_delta` is the absolute block identifier counting from
the start of the file.  The position of that block can be obtained by
`block_delta[0] * block_size`.  Additional `block_delta` entries are
sorted ascending and relative to the prior entry, e.g.  a reader would
perform:

    block_id = block_delta[0]
    prior = block_id
    for (j = 1; j < block_count; j++) {
      block_id = prior + block_delta[j]
      prior = block_id
    }

With a `block_id` in hand, a reader must linearly scan the ref block
at `block_id * block_size` position in the file, starting from the first
`ref_record`, testing each reference's SHA-1s (for `value_type = 0x1`
or `0x2`) for full equality.  Faster searching by SHA-1 within a
single ref block is not supported by the reftable format.  Smaller
block sizes reduce the number of candidates this step must consider.

### Obj index

The obj index stores the abbreviation from the last entry for every
obj block in the file, enabling reduced disk seeks for all lookups.
It is formatted exactly the same as the ref index, but refers to obj
blocks.

The obj index should be present if obj blocks are present, as
obj blocks should only be written in larger files.

The obj index should be block aligned, according to `block_size` from
the file header.  This requires padding the last obj block to maintain
alignment.

Readers loading the obj index must first read the footer (below) to
obtain `obj_index_position`.  If not present, the position will be 0.

### Log block format

Unlike ref and obj blocks, log block sizes are variable in size, and
do not match the `block_size` specified in the file header or footer.
Writers should choose an appropriate buffer size to prepare a log block
for deflation, such as `2 * block_size`.

A log block is written as:

    'g'
    uint24( block_len )
    zlib_deflate {
      log_record+
      uint24( restart_offset )+
      uint16( restart_count )
    }

Log blocks look similar to ref blocks, except `block_type = 'g'`.

The 4-byte block header is followed by the deflated block contents
using zlib deflate.  The `block_len` in the header is the inflated
size (including 4-byte block header), and should be used by readers to
preallocate the inflation output buffer.  A log block's `block_len`
may exceed the file's `block_size`.

Offsets within the log block (e.g.  `restart_offset`) still include
the 4-byte header.  Readers may prefer prefixing the inflation output
buffer with the 4-byte header.

Within the deflate container, a variable number of `log_record`
describe reference changes.  The log record format is described
below.  See ref block format (above) for a description of
`restart_offset` and `restart_count`.

Unlike ref blocks, log blocks are written at any alignment, without
padding.  The first log block immediately follows the end of the prior
block, which omits its trailing padding.  In very small files the log
block may appear in the first block.

Because log blocks have no alignment or padding between blocks,
readers must keep track of the bytes consumed by the inflater to
know where the next log block begins.

#### log record

Log record keys are structured as:

    ref_name '\0' reverse_int64( update_index )

where `update_index` is the unique transaction identifier.  The
`update_index` field must be unique within the scope of a `ref_name`.
See the update index section below for further details.

The `reverse_int64` function inverses the value so lexographical
ordering the network byte order encoding sorts the more recent records
with higher `update_index` values first:

    reverse_int64(int64 t) {
      return 0xffffffffffffffff - t;
    }

Log records have a similar starting structure to ref and index
records, utilizing the same prefix compression scheme applied to the
log record key described above.

```
    varint( prefix_length )
    varint( (suffix_length << 3) | log_type )
    suffix
    ( log_data | log_chained )?


    log_data {
      old_id
      new_id
      varint( time_seconds )
      sint16( tz_offset )
      varint( name_length    )  name
      varint( email_length   )  email
      varint( message_length )  message
    }

    log_chained {
      old_id
      varint( time_seconds )
      not_same_ident {
        sint16( tz_offset )
        varint( name_length    )  name
        varint( email_length   )  email
      }?
      not_same_message {
        varint( message_length )  message
      }?
    }
```

Log record entries use `log_type` to indicate what follows:

- `0x0`: deletion; no log data.
- `0x1`: standard git reflog data using `log_data` above.
- `0x2..0x3`: reserved for future use.
- `0x4..0x7`: `log_chained`, with conditional members.

The `log_type = 0x0` is mostly useful for `git stash drop`, removing
an entry from the reflog of `refs/stash` in a transaction file
(below), without needing to rewrite larger files.  Readers reading a
stack of reflogs must treat this as a deletion.

For `log_type = 0x1`, the `log_data` section follows
[git update-ref][update-ref] logging, and includes:

- two 20-byte SHA-1s (old id, new id)
- varint time in seconds since epoch (Jan 1, 1970)
- 2-byte timezone offset in minutes (signed)
- varint string of committer's name
- varint string of committer's email
- varint string of message

`tz_offset` is the absolute number of minutes from GMT the committer
was at the time of the update.  For example `GMT-0800` is encoded in
reftable as `sint16(-480)` and `GMT+0230` is `sint16(150)`.

The committer email does not contain `<` or `>`, it's the value
normally found between the `<>` in a git commit object header.

The `message_length` may be 0, in which case there was no message
supplied for the update.

For `log_type = 0x4..0x7` the `log_chained` section is used instead to
compress information that already appeared in a prior log record, up
to and including the prior restart, but not records before that
restart.  This allows readers to only handle chained records within
the span of a single restart's linear search.

The `log_chained` always includes `old_id` for this record, as `new_id` is
implied by the prior (by file order, more recent) record's `old_id`.

The `not_same_ident` block appears if `log_type & 0x1` is true,
`not_same_message` block appears if `log_type & 0x2` is true.  When
one of these blocks is missing, its values are implied by the prior
(more recent) log record.

[update-ref]: https://git-scm.com/docs/git-update-ref#_logging_updates

#### Reading the log

Readers accessing the log must first read the footer (below) to
determine the `log_position`.  The first block of the log begins at
`log_position` bytes since the start of the file.  The `log_position`
is not block aligned.

#### Importing logs

When importing from `$GIT_DIR/logs` writers should globally order all
log records roughly by timestamp while preserving file order, and
assign unique, increasing `update_index` values for each log line.
Newer log records get higher `update_index` values.

Although an import may write only a single reftable file, the reftable
file must span many unique `update_index`, as each log line requires
its own `update_index` to preserve semantics.

### Log index

The log index stores the log key (`refname \0 reverse_int64(update_index)`)
for the last log record of every log block in the file, supporting
bounded-time lookup.

A log index block must be written if 2 or more log blocks are written
to the file.  If present, the log index appears after the last log
block.  There is no padding used to align the log index to block
alignment.

Log index format is identical to ref index, except the keys are 9
bytes longer to include `'\0'` and the 8-byte
`reverse_int64(update_index)`.  Records use `block_position` to
refer to the start of a log block.

#### Reading the index

Readers loading the log index must first read the footer (below) to
obtain `log_index_position`. If not present, the position will be 0.

### Footer

After the last block of the file, a file footer is written.  It begins
like the file header, but is extended with additional data.

A 68-byte footer appears at the end:

```
    'REFT'
    uint8( version_number = 1 )
    uint24( block_size )
    uint64( min_update_index )
    uint64( max_update_index )

    uint64( ref_index_position )
    uint64( (obj_position << 5) | obj_id_len )
    uint64( obj_index_position )

    uint64( log_position )
    uint64( log_index_position )

    uint32( CRC-32 of above )
```

If a section is missing (e.g. ref index) the corresponding position
field (e.g. `ref_index_position`) will be 0.

- `obj_position`: byte position for the first obj block.
- `obj_id_len`: number of bytes used to abbreviate object identifiers
  in obj blocks.
- `log_position`: byte position for the first log block.
- `ref_index_position`: byte position for the start of the ref index.
- `obj_index_position`: byte position for the start of the obj index.
- `log_index_position`: byte position for the start of the log index.

#### Reading the footer

Readers must seek to `file_length - 68` to access the footer.  A
trusted external source (such as `stat(2)`) is necessary to obtain
`file_length`.  When reading the footer, readers must verify:

- 4-byte magic is correct
- 1-byte version number is recognized
- 4-byte CRC-32 matches the other 64 bytes (including magic, and version)

Once verified, the other fields of the footer can be accessed.

### Varint encoding

Varint encoding is identical to the ofs-delta encoding method used
within pack files.

Decoder works such as:

    val = buf[ptr] & 0x7f
    while (buf[ptr] & 0x80) {
      ptr++
      val = ((val + 1) << 7) | (buf[ptr] & 0x7f)
    }

### Binary search

Binary search within a block is supported by the `restart_offset`
fields at the end of the block.  Readers can binary search through the
restart table to locate between which two restart points the sought
reference or key should appear.

Each record identified by a `restart_offset` stores the complete key
in the `suffix` field of the record, making the compare operation
during binary search straightforward.

Once a restart point lexicographically before the sought reference has
been identified, readers can linearly scan through the following
record entries to locate the sought record, terminating if the current
record sorts after (and therefore the sought key is not present).

#### Restart point selection

Writers determine the restart points at file creation.  The process is
arbitrary, but every 16 or 64 records is recommended.  Every 16 may
be more suitable for smaller block sizes (4k or 8k), every 64 for
larger block sizes (64k).

More frequent restart points reduces prefix compression and increases
space consumed by the restart table, both of which increase file size.

Less frequent restart points makes prefix compression more effective,
decreasing overall file size, with increased penalities for readers
walking through more records after the binary search step.

A maximum of `65535` restart points per block is supported.

## Considerations

### Lightweight refs dominate

The reftable format assumes the vast majority of references are single
SHA-1 valued with common prefixes, such as Gerrit Code Review's
`refs/changes/` namespace, GitHub's `refs/pulls/` namespace, or many
lightweight tags in the `refs/tags/` namespace.

Annotated tags storing the peeled object cost only an additional 20
bytes per reference.

### Low overhead

A reftable with very few references (e.g. git.git with 5 heads)
is 274 bytes for reftable, vs. 332 bytes for packed-refs.  This
supports reftable scaling down for transaction logs (below).

### Block size

For a Gerrit Code Review type repository with many change refs, larger
block sizes (64 KiB) and less frequent restart points (every 64) yield
better compression due to more references within the block compressing
against the prior reference.

Larger block sizes reduce the index size, as the reftable will
require fewer blocks to store the same number of references.

### Minimal disk seeks

Assuming the index block has been loaded into memory, binary searching
for any single reference requires exactly 1 disk seek to load the
containing block.

### Scans and lookups dominate

Scanning all references and lookup by name (or namespace such as
`refs/heads/`) are the most common activities performed by repositories.
SHA-1s are stored twice when obj blocks are present, avoiding disk
seeks for the common cases of scan and lookup by name.

### Logs are infrequently read

Logs are infrequently accessed, but can be large.  Deflating log
blocks saves disk space, with some increased penalty at read time.

Logs are stored in an isolated section from refs, reducing the burden
on reference readers that want to ignore logs.  Further, historical
logs can be isolated into log-only files.

### Logs are read backwards

Logs are frequently accessed backwards (most recent N records for
master to answer `master@{4}`), so log records are grouped by
reference, and sorted descending by update index.

## Repository format

### Version 1

A repository must set its `$GIT_DIR/config` to configure reftable:

    [core]
        repositoryformatversion = 1
    [extensions]
        refStorage = reftable

### Layout

The `$GIT_DIR/refs` path is a file when reftable is configured, not a
directory.  This prevents loose references from being stored.

A collection of reftable files are stored in the `$GIT_DIR/reftable/`
directory:

    00000001_UF4paF
    00000002_bUVgy4

where reftable files are named by a unique name such as produced by
the function:

    mktemp "${update_index}_XXXXXX"

The stack ordering file is `$GIT_DIR/refs` and lists the current
files, one per line, in order, from oldest (base) to newest (most
recent):

    $ cat .git/refs
    00000001_UF4paF
    00000002_bUVgy4

Readers must read `$GIT_DIR/refs` to determine which files are
relevant right now, and search through the stack in reverse order
(last reftable is examined first).

Reftable files not listed in `refs` may be new (and about to be added
to the stack by the active writer), or ancient and ready to be pruned.

### Update transactions

Although reftables are immutable, mutations are supported by writing a
new reftable and atomically appending it to the stack:

1. Acquire `refs.lock`.
2. Read `refs` to determine current reftables.
3. Select `update_index` to be most recent file's `max_update_index + 1`.
4. Prepare new reftable `${update_index}_XXXXXX`, including log entries.
5. Copy `refs` to `refs.lock`, appending file from (4).
6. Rename `refs.lock` to `refs`.

During step 4 the new file's `min_update_index` and `max_update_index`
are both set to the `update_index` selected by step 3.  All log
records for the transaction use the same `update_index` in their keys.
This enables later correlation of which references were updated by the
same transaction.

Because a single `refs.lock` file is used to manage locking, the
repository is single-threaded for writers.  Writers may have to
busy-spin (with backoff) around creating `refs.lock`, for up to an
acceptable wait period, aborting if the repository is too busy to
mutate.  Application servers wrapped around repositories (e.g.  Gerrit
Code Review) can layer their own lock/wait queue to improve fairness
to writers.

### Reference deletions

Deletion of any reference can be explicitly stored by setting the
`type` to `0x0` and omitting the `value` field of the `ref_record`.
This entry shadows the reference in earlier files in the stack.

### Compaction

A partial stack of reftables can be compacted by merging references
using a straightforward merge join across reftables, selecting the
most recent value for output, and omitting deleted references that do
not appear in remaining, lower reftables.

A compacted reftable should set its `min_update_index` to the smallest of
the input files' `min_update_index`, and its `max_update_index`
likewise to the largest input `max_update_index`.

For sake of illustration, assume the stack currently consists of
reftable files (from oldest to newest): A, B, C, and D. The compactor
is going to compact B and C, leaving A and D alone.

1.  Obtain lock `refs.lock` and read the `refs` file.
2.  Obtain locks `B.lock` and `C.lock`.
    Ownership of these locks prevents other processes from trying
    to compact these files.
3.  Release `refs.lock`.
4.  Compact `B` and `C` into a new file `${min_update_index}_XXXXXX`.
5.  Reacquire lock `refs.lock`.
6.  Verify that `B` and `C` are still in the stack, in that order. This
    should always be the case, assuming that other processes are adhering
    to the locking protocol.
7.  Write the new stack to `refs.lock`, replacing `B` and `C` with the
    file from (4).
8.  Rename `refs.lock` to `refs`.
9.  Delete `B` and `C`, perhaps after a short sleep to avoid forcing
    readers to backtrack.

This strategy permits compactions to proceed independently of updates.

## Alternatives considered

### bzip packed-refs

`bzip2` can significantly shrink a large packed-refs file (e.g. 62
MiB compresses to 23 MiB, 37%).  However the bzip format does not support
random access to a single reference. Readers must inflate and discard
while performing a linear scan.

Breaking packed-refs into chunks (individually compressing each chunk)
would reduce the amount of data a reader must inflate, but still
leaves the problem of indexing chunks to support readers efficiently
locating the correct chunk.

Given the compression achieved by reftable's encoding, it does not
seem necessary to add the complexity of bzip/gzip/zlib.

### Michael Haggerty's alternate format

Michael Haggerty proposed [an alternate][mh-alt] format to reftable on
the Git mailing list.  This format uses smaller chunks, without the
restart table, and avoids block aligning with padding.  Reflog entries
immediately follow each ref, and are thus interleaved between refs.

Performance testing indicates reftable is faster for lookups (51%
faster, 11.2 usec vs.  5.4 usec), although reftable produces a
slightly larger file (+ ~3.2%, 28.3M vs 29.2M):

format    |  size  | seek cold | seek hot  |
---------:|-------:|----------:|----------:|
mh-alt    | 28.3 M | 23.4 usec | 11.2 usec |
reftable  | 29.2 M | 19.9 usec |  5.4 usec |

[mh-alt]: https://public-inbox.org/git/CAMy9T_HCnyc1g8XWOOWhe7nN0aEFyyBskV2aOMb_fe+wGvEJ7A@mail.gmail.com/

### JGit Ketch RefTree

[JGit Ketch][ketch] proposed [RefTree][reftree], an encoding of
references inside Git tree objects stored as part of the repository's
object database.

The RefTree format adds additional load on the object database storage
layer (more loose objects, more objects in packs), and relies heavily
on the packer's delta compression to save space.  Namespaces which are
flat (e.g.  thousands of tags in refs/tags) initially create very
large loose objects, and so RefTree does not address the problem of
copying many references to modify a handful.

Flat namespaces are not efficiently searchable in RefTree, as tree
objects in canonical formatting cannot be binary searched. This fails
the need to handle a large number of references in a single namespace,
such as GitHub's `refs/pulls`, or a project with many tags.

[ketch]: https://dev.eclipse.org/mhonarc/lists/jgit-dev/msg03073.html
[reftree]: https://public-inbox.org/git/CAJo=hJvnAPNAdDcAAwAvU9C4RVeQdoS3Ev9WTguHx4fD0V_nOg@mail.gmail.com/

### LMDB

David Turner proposed [using LMDB][dt-lmdb], as LMDB is lightweight
(64k of runtime code) and GPL-compatible license.

A downside of LMDB is its reliance on a single C implementation.  This
makes embedding inside JGit (a popular reimplemenation of Git)
difficult, and hoisting onto virtual storage (for JGit DFS) virtually
impossible.

A common format that can be supported by all major Git implementations
(git-core, JGit, libgit2) is strongly preferred.

[dt-lmdb]: https://public-inbox.org/git/1455772670-21142-26-git-send-email-dturner@twopensource.com/

## Future

### Longer hashes

Version will bump (e.g.  2) to indicate `value` uses a different
object id length other than 20.  The length could be stored in an
expanded file header, or hardcoded as part of the version.
