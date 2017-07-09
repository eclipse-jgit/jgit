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
storing 41 bytes.  This negatively affects the number of inodes
available when a large number of repositories are stored on the same
filesystem.  Readers are also penalized due to the larger number of
syscalls required to traverse and read the `$GIT_DIR/refs` directory.

### Objectives

- Near constant time lookup for any single reference, even when the
  repository is cold and not in process or kernel cache.
- Occupy less disk space for large repositories.
- Support atomic pushes with lower copying penalities.

### Description

A reftable file is a portable binary file format customized for
reference storage. References are sorted, enabling linear scans,
binary search lookup, and range scans.

Storage in the file is organized into blocks.  Prefix compression
is used within a single block to reduce disk space.  Block size is
tunable by the writer.

### Performance

Space used, packed-refs vs. reftable:

repository | packed-refs | reftable | % original | avg ref
-----------|------------:|---------:|-----------:|---------:
android    |      62.2 M |   27.7 M |     44.4%  | 33 bytes
rails      |       1.8 M |  896.2 K |     47.6%  | 29 bytes
git        |      78.7 K |   27.9 K |     40.0%  | 43 bytes
git (heads)|       332 b |    204 b |     61.4%  | 34 bytes

Scan (read 866k refs) and lookup (single ref from 866k refs):

format      | scan    | lookup
------------|--------:|---------------:
packed-refs |  380 ms | 375420.0 usec
reftable    |  125 ms |     42.3 usec

## Details

### Peeling

References in a reftable are always peeled.

### Reference name encoding

Reference names should be encoded with UTF-8.

### Ordering

Blocks are lexicographically ordered by their first reference.


## File format

### Header

A 8-byte header appears at the beginning of each file:

- 4-byte magic is: `\'1', 'R', 'E', 'F'`
- 1-byte version number, `1`.
- 3-byte `block_size` in bytes (network byte order).

### Block size

The `block_size` is arbitrarily determined by the writer, and does not
have to be a power of 2.  The block size must be larger than the
longest reference name used in the repository, as references cannot
span blocks.

### First block

The first block shares the same block as the file header, and is 8
bytes smaller than all other blocks in the file.  The first block
immediately begins after the file header, at offset 8.

### Block format

A block is written as:

    ref_record*
    padding?
    int32( restart_offset )*
    int32( record_end_offset )
    int32( number_of_restarts )

Blocks begin with a variable number of `ref_record`, describing
reference names and values. The format is described below.

The middle of the record may be filled with `padding` NUL bytes to
fill out the block to the common `block_size` as specified in the file
header.  Padding may be necessary to ensure `number_of_restarts`
occupies the last 4 bytes of the block.  Padding may be omitted if the
block is the last block of the file, and there is no index block.
This allows reftable to efficiently scale down to a small number of
refs.

A variable number of 4-byte, network byte order `restart_offset`
values follows the padding.  Offsets are relative to the start of the
block and refer to the first byte of any `ref_record` whose name has
not been prefixed compressed.  Readers can start linear scans from any
of these records.

The 4-byte, network byte order `record_end_offset` follows, providing
the block-relative offset after the end of the last `ref_record`.  If
`padding` is present this is the offset of the first byte of padding,
or the first byte of the first `restart_offset` entry.

The 4-byte, network byte order `number_of_restarts` stores the number
of entries in the `restart_offset` list.  Readers can use the restart
count to binary search between restarts before starting a linear scan.
This field must be the last 4 bytes of the block; the `padding` field
must be used to ensure this is true.

#### ref record

A `ref_record` describes a single reference, storing both the name and
its value(s). Records are formatted as:

    varint( prefix_length )
    varint( (suffix_length << 2) | type )
    suffix
    value?

The `prefix_length` field specifies how many leading bytes of the
prior reference record's name should be copied to obtain this
reference's name.  This must be 0 for the first reference in any
block, and also must be 0 for any `ref_record` whose offset is listed
in the `restart_offset` table at the end of the block.

Recovering a reference name from any `ref_record` is a simple concat:

    this_name = prior_name[0..prefix_length] + suffix

The second varint carries both `suffix_length` and `type`.  The
`suffix_length` value provides the number of bytes to copy from
`suffix` to complete the reference name.

The `value` immediately follows.  Its format is determined by `type`,
a 2 bit code, one of the following:

- `0x0`: deletion; no value data (see transactions, below)
- `0x1`: one 20-byte object id; value of the ref
- `0x2`: two 20-byte object ids; value of the ref, peeled target
- `0x3`: symbolic reference: `varint( target_len ) target`

Symbolic references use a varint length followed by a variable number
of bytes to encode the complete reference target.  No compression is
applied to the target name.

### Index block

The index stores the name of the last reference from every block in
the file, enabling constant O(1) disk seeks for all lookups.  Any
reference can be found by binary searching the index, identifying the
containing block, and searching within that block.

If present, the index block appears after the last block of the file.

An index block should only be written if there are at least 4 blocks
in the file, as cold reads using the index requires 2 disk reads, and
binary searching <= 4 blocks also requires <= 2 reads.  Omitting the
index block from smaller files saves space.

Index block format:

    '\1' 'i'
    index_record*
    int32( restart_offset )*
    int32( record_end_offset )
    int32( number_of_restarts )

Index blocks begin with a magic prefix, `\1i`, where other blocks
would have started with `\0` for the first ref record's prefix length.
This supports stopping sequential scans at the index block, without
prior knowledge of its position.

Unlike other blocks, the index block is not padded.

The `restart_offset`, `record_end_offset`, and `number_of_restarts`
fields are identical in format, meaning and usage as in `ref_record`.

To reduce the number of reads required for random access in very large
files, the index block may be larger than the other blocks.  However,
readers must hold the entire index in memory to benefit from this, so
its a time-space tradeoff in both file size, and reader memory.
Increasing the block size in the writer decreases the index size.

#### index record

An index record describes the last reference of another block.
Index records are written as:

    varint( prefix_length )
    varint( (suffix_length << 2) )
    suffix
    varint( block_idx )

Index records use prefix compression exactly like `ref_record`.  The
`suffix_length` is shifted 2 bits without a `type` to simplify unified
reader/writer code for both block types.

Index records store `block_idx` after the suffix, specifying which
block of the file ends with this reference. The block is located at
position `block_idx * block_size`.

### Reading the index

Readers loading the index must first read the footer (below) to
determine `index_size`.  The index is located at position:

    file_length - (index_size + 16)

### Footer

After the last block of the file (or index block, if present), a file
footer is written.  This is similar in structure to the file header,
but extended with additional data.

A 16-byte footer appears at the end:

- 4-byte magic is: `\'1', 'R', 'E', 'F'`
- 1-byte version number, 1.
- 3-byte `block_size` in bytes (network byte order).
- 4-byte `index_size` in bytes (network byte order).
- 4-byte CRC-32 of the preceding 12 bytes (network byte order).

Like the index block magic header, the footer begins with `\1R` to
allow sequential scans to recognize the end of file has been reached.

#### Reading the footer

Readers must seek to `file_length - 16` to access the footer.  A
trusted external source (such as `stat(2)`) is necessary to obtain
`file_length`.  When reading the footer, readers must verify:

- 4-byte magic is correct
- 1-byte version number is recognized
- 4-byte CRC-32 matches the other 12 bytes read

Once verified, the `block_size` and `index_size` may be accessed from
the footer.

### Varint encoding

Varint encoding is identical to the ofs-delta encoding method used
within pack files.

Decoder works such as:

    val = buf[ptr] & 0x7f
    while (buf[ptr] & 0x80) {
      ptr++
      val++
      val = val << 7
      val = val | (buf[ptr] & 0x7f)
    }

### Binary search

Binary search within a block is supported by the `restart_offset`
fields at the end of the block.  Readers can binary search through the
restart table to locate between which two restart points the sought
reference should appear.

Each reference identified by a `restart_offset` stores the complete
reference name in the `suffix` field of the `ref_record`, making the
compare operation during the binary search straightforward.

Once a restart point lexicographically before the sought reference has
been identified, readers can linearly scan through the following
`ref_record` entries to locate the sought reference, stopping when the
current `ref_record` sorts after (and therefore the sought reference
is not present).

#### Restart point selection

Writers determine the restart points at file creation.  The process is
arbitrary, but every 16 or 64 references is recommended.  Every 16 may
be more suitable for smaller block sizes (4k or 8k), every 64 for
larger block sizes (64k).

More frequent restart points reduces prefix compression and increases
space consumed by the restart table, both of which will increase the
overall file size.

Less frequent restart points makes prefix compression more effective,
decreasing overall file size, with increased penalities for readers
who must walk through more references after the binary search step.

## Considerations

### Lightweight refs dominate

The reftable format assumes the vast majority of references are single
SHA-1 valued with common prefixes, such as Gerrit Code Review's
`refs/changes/` namespace, GitHub's `refs/pulls/` namespace, or many
lightweight tags in the `refs/tags/` namespace.

Annotated tags storing the peeled object cost only an additional 20
bytes per reference.

### Low overhead

A reftable with very few references (e.g.  git.git with 5 heads) uses
only 204 bytes for reftable vs.  332 bytes for packed-refs.  This
supports reftable scaling down, to be used for transaction logs
(below).

### Block size

For a Gerrit Code Review type repository with many change refs, larger
block sizes (64 KiB) and less frequent restart points (every 64) yield
better compression due to more references within the block able to
compress against the prior reference.

Larger block sizes reduces the index size, as the reftable will
require fewer blocks to store the same number of references.

### Minimal disk seeks

Assuming the index block has been loaded into memory, binary searching
for any single reference requires exactly 1 disk seek to load the
containing block.

## Repository format

When reftable is stored in a file-backed Git repository, the stack is
represented as a series of reftable files:

    $GIT_DIR/reftable
    $GIT_DIR/reftable.1
    $GIT_DIR/reftable.2
    $GIT_DIR/reftable.3
    ...
    $GIT_DIR/reftable.10

where a larger suffix ordinal indicates a more recent table.

### Transactions

Although reftables are immutable, they can be stacked in a search
pattern, with each reference transaction adding a new reftable to the
top of the stack.  Readers scan down the reftable stack from
most-recent (`reftable.10`) to the base file (`reftable`).

### Update process

Updating references follows an update protocol:

1. Atomically create `$GIT_DIR/reftable.lock`.
2. `readdir($GIT_DIR)` to determine the highest suffix ordinal, `n`.
3. Compute the update transaction (e.g. compare expected values).
4. Write only modified references as a reftable to `reftable.lock`.
5. Rename `reftable.lock` to `reftable.${n + 1}`. 

Because a single `reftable.lock` file is used to manage locking, the
repository is single-threaded for writers.  Writers may have to
busy-spin (with some small backoff) around creating `reftable.lock`,
for up to an acceptable wait period, aborting if the repository is too
busy to mutate.  Application servers wrapped around repositories (e.g.
Gerrit Code Review) can layer their own in memory thread lock/wait
queue to provide fairness.

### Reference deletions

Deletion of any reference can be explicitly stored by setting the
`type` to `0x0` and omitting the `value` field of the `ref_record`.
This entry shadows the reference in lower files in the stack.

### Compaction

A stack of reftables can be compacted by merging references using a
straightforward merge join across all reftables, selecting the most
recent value for output, and omitting deleted references that do not
appear in remaining, lower reftables.

The stack can be collapsed as part of any update transaction.  If the
current number of files is larger than a threshold (e.g.  4), writers
can perform an lstat(2) on each reftable file to determine how many
bytes would have to be read/copied from an existing file into the
new file, enabling deletion of the existing file.

Writers can select to collapse the most recent files (e.g.  10, 9, 8,
...), up to a collapse IO threshold (e.g.  4 MiB).  Each file selected
for collapse must have its references merged into the new reftable
that is being prepared.

Compaction is similar to the update process, but an explicit temporary
file must be used:

1. Atomically create `$GIT_DIR/reftable.lock`.
2. `readdir($GIT_DIR)` to determine the highest suffix ordinal, `n`.
3. Compute the update transaction (e.g. compare expected values).
4. Select files from (2) to collapse as part of this transaction.
5. Create temp file by `mktemp("$GIT_DIR/.reftableXXXXXX")`.
6. Write modified and collapsed references to temp file.
7. Rename temp file to `reftable.${n + 1}`. 
8. Delete collapsed files `reftable.${n}`, `reftable.${n - 1}`, ...
9. Delete `reftable.lock`.

Because `reftable.9` can disappear after `reftable.10` is created,
readers receiving ENOENT when opening `reftable.9` must peform
another readdir to look for new reftables.

Rebuilding the base `$GIT_TABLE/reftable` follows the same protocol,
except in step 7 the temp file is renamed to `reftable`, and step 8
removes all files with an ordinal suffix.

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

Given the compression ratios achieved by reftable's simple encoding
(e.g.  44%), without using a standard compression algorithm, it does
not seem necessary to add the complexity of bzip/gzip/zlib.

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
difficult, and hositing onto virtual storage (for JGit DFS) virtually
impossible.

A common format that can be supported by all major Git implementations
(git-core, JGit, libgit2) is strongly preferred.

[dt-lmdb]: https://public-inbox.org/git/1455772670-21142-26-git-send-email-dturner@twopensource.com/

## Future

### Longer hashes

Version will bump (e.g.  2) to indicate `value` uses a different
object id length other than 20.  The length could be stored in an
expanded file header, or hardcoded as part of the version.
