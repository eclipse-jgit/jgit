/*
 * Copyright (C) 2012, Robin Rosenberg <robin.rosenberg@dewire.com>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ObjectStream;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryTestCase;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.ObjectDirectory;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Test;

public class HugeFileTest extends RepositoryTestCase {

	String thehash = "b8cfba97c2b962a44f080b3ca4e03b3204b6a350";

	long TESTFILESIZE;

	long timestamp;

	Set<String> knowns = new HashSet<String>();

	private long outputSize;

	void setFile(long size, long time, String hash, long outputSize) {
		TESTFILESIZE = size;
		timestamp = time;
		thehash = hash;
		knowns.add(hash);
		this.outputSize = outputSize;
	}

	private final class MockFileTreeIterator extends FileTreeIterator {
		private final long filesize;

		private MockFileTreeIterator(Repository repo, long filesize) {
			super(repo);
			this.filesize = filesize;
		}

		@Override
		public long getEntryLength() {
			return filesize;
		}

		@Override
		public MetadataDiff compareMetadata(
				org.eclipse.jgit.dircache.DirCacheEntry entry) {
			return MetadataDiff.SMUDGED;
		}

		@Override
		public byte[] computeHash(InputStream in, long length)
				throws IOException {
			byte[] ret = new byte[Constants.OBJECT_ID_LENGTH];
			ObjectId.fromString(thehash).copyRawTo(ret, 0);
			return ret;
		}

		@Override
		protected Entry newFileTreeEntry(final File f) {
			return new Entry() {

				@Override
				public FileMode getMode() {
					return FileMode.REGULAR_FILE;
				}

				@Override
				public long getLength() {
					return filesize;
				}

				@Override
				public long getLastModified() {
					long now = SystemReader.getInstance().getCurrentTime();
					return now - now % 1000;
				}

				@Override
				public String getName() {
					return f.getName();
				}

				@Override
				public InputStream openInputStream() throws IOException {
					System.out.println("Opening input Stream");
					return new InputStream() {
						long pos = 0;

						@Override
						public int read() throws IOException {
							++pos;
							return 0;
						}

						@Override
						public int read(byte[] b, int off, int len)
								throws IOException {
							if (pos + len > filesize)
								len = (int) (filesize - pos);
							// for (int i = off; i < off + len; ++i)
							// b[i] = 0;
							pos += len;
							return len;
						}

						@Override
						public int available() throws IOException {
							return (int) (filesize - pos);
						}

						@Override
						public boolean markSupported() {
							return false;
						}
					};
				}
			};
		}
	}

	private long t = System.currentTimeMillis();

	private long lastt = t;

	private void measure(String name) {
		long c = System.currentTimeMillis();
		System.out.println(name + ", dt=" + (c - lastt) / 1000.0 + "s");
		lastt = c;
	}

	// @Ignore("Test takes way too long (~10 minutes) to be part of the standard suite")
	@Test
	public void testAddHugeFile() throws Exception {
		measure("Commencing test");
		db = new FileRepository(db.getDirectory()) {

			@Override
			public ObjectDirectory getObjectDatabase() {
				final ObjectDirectory base = super.getObjectDatabase();
				try {
					return new ObjectDirectory(getConfig(),
							getObjectsDirectory(), null, getFS()) {
						@Override
						public File fileFor(AnyObjectId objectId) {
							if (knowns.contains(objectId))
								return new File("dummy");
							return base.fileFor(objectId);
						}

						ObjectReader supernewReader() {
							return base.newReader();
						}

						@Override
						public ObjectReader newReader() {
							return new ObjectReader() {

								@Override
								public Collection<ObjectId> resolve(
										AbbreviatedObjectId id)
										throws IOException {
									// TODO Auto-generated method stub
									return null;
								}

								@Override
								public ObjectLoader open(AnyObjectId objectId,
										int typeHint)
										throws MissingObjectException,
										IncorrectObjectTypeException,
										IOException {
									if (!knowns.contains(objectId.getName()))
										return supernewReader().open(
												objectId);
									return new ObjectLoader() {

										@Override
										public int getType() {
											return Constants.OBJ_BLOB;
										}

										@Override
										public long getSize() {
											return TESTFILESIZE;
										}

										@Override
										public byte[] getCachedBytes()
												throws LargeObjectException {
											throw new LargeObjectException();
										}

										@Override
										public void copyTo(java.io.OutputStream out) throws MissingObjectException ,IOException {
											OutputStream dummy = new OutputStream() {

												private long count;
												@Override
												public void write(int b)
														throws IOException {
													++count;
												}
												public void write(byte[] b, int off, int len) throws IOException {
													count += len;
												}

												public void close() throws IOException {
													assertEquals(outputSize,
															count);
													super.close();
													// pretend this takes some
													// time
													try {
														Thread.sleep(2000);
													} catch (InterruptedException e) {
													}
												}
											};
											super.copyTo(dummy);
										}

										@Override
										public ObjectStream openStream()
												throws MissingObjectException,
												IOException {
											return new ObjectStream() {

												long pos;

												@Override
												public int getType() {
													return Constants.OBJ_BLOB;
												}

												@Override
												public long getSize() {
													return outputSize;
												}

												@Override
												public int read()
														throws IOException {
													if (pos >= outputSize)
														return -1;
													return 0;
												}

												@Override
												public int read(byte[] b,
														int off, int len)
														throws IOException {
													if (pos + len > outputSize)
														len = (int) (outputSize - pos);
													int ret = super.read(b,
															off, len);
													if (ret > 0)
														pos += ret;
													return ret;
												}

												@Override
												public void close()
														throws IOException {
													assertEquals(outputSize,
															pos);
												}
											};
										}

									};
								}

								@Override
								public ObjectReader newReader() {
									throw new Error();
								}
							};
						}
					};
				} catch (IOException e) {
					throw new Error(e);
				}
			}

			public org.eclipse.jgit.lib.ObjectInserter newObjectInserter() {
				final ObjectInserter standardInserter = super
						.newObjectInserter();
				return new ObjectInserter() {

					@Override
					public void release() {
					}

					@Override
					public PackParser newPackParser(InputStream in) throws IOException {
						throw new Error();
					}

					@Override
					public ObjectId insert(int objectType, long length, InputStream in)
							throws IOException {
						if (objectType != Constants.OBJ_BLOB)
							return standardInserter.insert(objectType, length,
									in);
						return ObjectId.fromString(thehash);
					}

					@Override
					public void flush() throws IOException {
						standardInserter.flush();
					}
				};
			};
		};
		File file = new File(db.getWorkTree(), "a.txt");
		setFile(4429185024L, System.currentTimeMillis(),
				"b8cfba97c2b962a44f080b3ca4e03b3204b6a350", -1);
		RandomAccessFile rf = new RandomAccessFile(file, "rw");
		// rf.setLength(0);
		rf.close();
		measure("Created file");
		Git git = new Git(db) {
			@Override
			public StatusCommand status() {
				StatusCommand ret = new StatusCommand(db);
				ret.setWorkingTreeIt(new MockFileTreeIterator(db, TESTFILESIZE));
				return ret;
			}
			public AddCommand add() {
				AddCommand ret = new AddCommand(db);
				ret.setWorkingTreeIterator(new MockFileTreeIterator(db,
						TESTFILESIZE));
				return ret;
			}
		};

		git.add().addFilepattern("a.txt").call();
		measure("Added file");
		assertEquals(
				"[a.txt, mode:100644, length:134217728, sha1:b8cfba97c2b962a44f080b3ca4e03b3204b6a350]",
				indexState(LENGTH | CONTENT_ID));

		Status status = git.status().call();
		measure("Status after add");
		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertEquals(0, status.getModified().size());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		Thread.sleep(2000);
		// Does not change anything, but modified timestamp
		rf = new RandomAccessFile(file, "rw");
		rf.write(0);
		rf.close();
		setFile(4429185024L, System.currentTimeMillis(),
				"b8cfba97c2b962a44f080b3ca4e03b3204b6a350", -1);

		status = git.status().call();
		measure("Status after non-modifying update");

		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertEquals(0, status.getModified().size());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		// Change something
		rf = new RandomAccessFile(file, "rw");
		rf.write('a');
		rf.close();
		setFile(4429185024L, System.currentTimeMillis(),
				"1111111111111111111111111111111111111111", -1);

		status = git.status().call();
		measure("Status after modifying update");

		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertCollectionEquals(Arrays.asList("a.txt"), status.getModified());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		// Truncate mod 4G and re-establish equality
		rf = new RandomAccessFile(file, "rw");
		rf.setLength(134217728L);
		rf.write(0);
		rf.close();
		setFile(134217728L, System.currentTimeMillis(),
				"2222222222222222222222222222222222222222", -1);

		status = git.status().call();
		measure("Status after truncating update");

		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertCollectionEquals(Arrays.asList("a.txt"), status.getModified());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		// Change something
		rf = new RandomAccessFile(file, "rw");
		rf.write('a');
		rf.close();

		setFile(134217728L, System.currentTimeMillis(),
				"3333333333333333333333333333333333333333", -1);

		status = git.status().call();
		measure("Status after modifying and truncating update");

		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertCollectionEquals(Arrays.asList("a.txt"), status.getModified());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		// Truncate to entry length becomes negative int
		rf = new RandomAccessFile(file, "rw");
		// rf.setLength(3429185024L);
		rf.write(0);
		rf.close();

		setFile(3429185024L, System.currentTimeMillis(),
				"59b3282f8f59f22d953df956ad3511bf2dc660fd", -1);

		git.add().addFilepattern("a.txt").call();
		measure("Added truncated file");
		assertEquals(
				"[a.txt, mode:100644, length:-865782272, sha1:59b3282f8f59f22d953df956ad3511bf2dc660fd]",
				indexState(LENGTH | CONTENT_ID));

		status = git.status().call();
		measure("Status after status on truncated file");

		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertEquals(0, status.getModified().size());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		// Change something
		rf = new RandomAccessFile(file, "rw");
		rf.write('a');
		rf.close();

		setFile(3429185024L, System.currentTimeMillis(),
				"4444444444444444444444444444444444444444", -1);

		status = git.status().call();
		measure("Status after modifying and truncating update");

		assertCollectionEquals(Arrays.asList("a.txt"), status.getAdded());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertCollectionEquals(Arrays.asList("a.txt"), status.getModified());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		git.commit().setMessage("make a commit").call();
		measure("After commit");
		status = git.status().call();
		measure("After status after commit");

		assertEquals(0, status.getAdded().size());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertCollectionEquals(Arrays.asList("a.txt"), status.getModified());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());

		setFile(3429185024L, System.currentTimeMillis(),
				"4444444444444444444444444444444444444444", 3429185024L);

		git.reset().setMode(ResetType.HARD).call();
		measure("After reset --hard");
		assertEquals(
				"[a.txt, mode:100644, length:-865782272, sha1:59b3282f8f59f22d953df956ad3511bf2dc660fd]",
				indexState(LENGTH | CONTENT_ID));

		setFile(3429185024L, System.currentTimeMillis(),
				"59b3282f8f59f22d953df956ad3511bf2dc660fd", 3429185024L);
		status = git.status().call();
		measure("Status after hard reset");

		assertEquals(0, status.getAdded().size());
		assertEquals(0, status.getChanged().size());
		assertEquals(0, status.getConflicting().size());
		assertEquals(0, status.getMissing().size());
		assertEquals(0, status.getModified().size());
		assertEquals(0, status.getRemoved().size());
		assertEquals(0, status.getUntracked().size());
	}

	private void assertCollectionEquals(Collection<?> asList,
			Collection<?> added) {
		assertEquals(asList.toString(), added.toString());
	}

}
