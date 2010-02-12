/*
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
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

package org.eclipse.jgit.lib;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.zip.Deflater;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.transport.PackedObjectInfo;
import org.eclipse.jgit.util.NB;

/**
 * <p>
 * PackWriter class is responsible for generating pack files from specified set
 * of objects from repository. This implementation produce pack files in format
 * version 2.
 * </p>
 * <p>
 * Source of objects may be specified in two ways:
 * <ul>
 * <li>(usually) by providing sets of interesting and uninteresting objects in
 * repository - all interesting objects and their ancestors except uninteresting
 * objects and their ancestors will be included in pack, or</li>
 * <li>by providing iterator of {@link RevObject} specifying exact list and
 * order of objects in pack</li>
 * </ul>
 * Typical usage consists of creating instance intended for some pack,
 * configuring options, preparing the list of objects by calling
 * {@link #preparePack(Iterator)} or
 * {@link #preparePack(Collection, Collection)}, and finally
 * producing the stream with {@link #writePack(OutputStream)}.
 * </p>
 * <p>
 * Class provide set of configurable options and {@link ProgressMonitor}
 * support, as operations may take a long time for big repositories. Deltas
 * searching algorithm is <b>NOT IMPLEMENTED</b> yet - this implementation
 * relies only on deltas and objects reuse.
 * </p>
 * <p>
 * This class is not thread safe, it is intended to be used in one thread, with
 * one instance per created pack. Subsequent calls to writePack result in
 * undefined behavior.
 * </p>
 */
public class PackWriter {
	/**
	 * Title of {@link ProgressMonitor} task used during counting objects to
	 * pack.
	 *
	 * @see #preparePack(Collection, Collection)
	 */
	public static final String COUNTING_OBJECTS_PROGRESS = "Counting objects";

	/**
	 * Title of {@link ProgressMonitor} task used during searching for objects
	 * reuse or delta reuse.
	 *
	 * @see #writePack(OutputStream)
	 */
	public static final String SEARCHING_REUSE_PROGRESS = "Compressing objects";

	/**
	 * Title of {@link ProgressMonitor} task used during writing out pack
	 * (objects)
	 *
	 * @see #writePack(OutputStream)
	 */
	public static final String WRITING_OBJECTS_PROGRESS = "Writing objects";

	/**
	 * Default value of deltas reuse option.
	 *
	 * @see #setReuseDeltas(boolean)
	 */
	public static final boolean DEFAULT_REUSE_DELTAS = true;

	/**
	 * Default value of objects reuse option.
	 *
	 * @see #setReuseObjects(boolean)
	 */
	public static final boolean DEFAULT_REUSE_OBJECTS = true;

	/**
	 * Default value of delta base as offset option.
	 *
	 * @see #setDeltaBaseAsOffset(boolean)
	 */
	public static final boolean DEFAULT_DELTA_BASE_AS_OFFSET = false;

	/**
	 * Default value of maximum delta chain depth.
	 *
	 * @see #setMaxDeltaDepth(int)
	 */
	public static final int DEFAULT_MAX_DELTA_DEPTH = 50;

	private static final int PACK_VERSION_GENERATED = 2;

	@SuppressWarnings("unchecked")
	private final List<ObjectToPack> objectsLists[] = new List[Constants.OBJ_TAG + 1];
	{
		objectsLists[0] = Collections.<ObjectToPack> emptyList();
		objectsLists[Constants.OBJ_COMMIT] = new ArrayList<ObjectToPack>();
		objectsLists[Constants.OBJ_TREE] = new ArrayList<ObjectToPack>();
		objectsLists[Constants.OBJ_BLOB] = new ArrayList<ObjectToPack>();
		objectsLists[Constants.OBJ_TAG] = new ArrayList<ObjectToPack>();
	}

	private final ObjectIdSubclassMap<ObjectToPack> objectsMap = new ObjectIdSubclassMap<ObjectToPack>();

	// edge objects for thin packs
	private final ObjectIdSubclassMap<ObjectId> edgeObjects = new ObjectIdSubclassMap<ObjectId>();

	private final Repository db;

	private PackOutputStream out;

	private final Deflater deflater;

	private ProgressMonitor initMonitor;

	private ProgressMonitor writeMonitor;

	private final byte[] buf = new byte[16384]; // 16 KB

	private final WindowCursor windowCursor = new WindowCursor();

	private List<ObjectToPack> sortedByName;

	private byte packcsum[];

	private boolean reuseDeltas = DEFAULT_REUSE_DELTAS;

	private boolean reuseObjects = DEFAULT_REUSE_OBJECTS;

	private boolean deltaBaseAsOffset = DEFAULT_DELTA_BASE_AS_OFFSET;

	private int maxDeltaDepth = DEFAULT_MAX_DELTA_DEPTH;

	private int outputVersion;

	private boolean thin;

	private boolean ignoreMissingUninteresting = true;

	/**
	 * Create writer for specified repository.
	 * <p>
	 * Objects for packing are specified in {@link #preparePack(Iterator)} or
	 * {@link #preparePack(Collection, Collection)}.
	 *
	 * @param repo
	 *            repository where objects are stored.
	 * @param monitor
	 *            operations progress monitor, used within
	 *            {@link #preparePack(Iterator)},
	 *            {@link #preparePack(Collection, Collection)}
	 *            , or {@link #writePack(OutputStream)}.
	 */
	public PackWriter(final Repository repo, final ProgressMonitor monitor) {
		this(repo, monitor, monitor);
	}

	/**
	 * Create writer for specified repository.
	 * <p>
	 * Objects for packing are specified in {@link #preparePack(Iterator)} or
	 * {@link #preparePack(Collection, Collection)}.
	 *
	 * @param repo
	 *            repository where objects are stored.
	 * @param imonitor
	 *            operations progress monitor, used within
	 *            {@link #preparePack(Iterator)},
	 *            {@link #preparePack(Collection, Collection)}
	 * @param wmonitor
	 *            operations progress monitor, used within
	 *            {@link #writePack(OutputStream)}.
	 */
	public PackWriter(final Repository repo, final ProgressMonitor imonitor,
			final ProgressMonitor wmonitor) {
		this.db = repo;
		initMonitor = imonitor == null ? NullProgressMonitor.INSTANCE : imonitor;
		writeMonitor = wmonitor == null ? NullProgressMonitor.INSTANCE : wmonitor;
		this.deflater = new Deflater(db.getConfig().getCore().getCompression());
		outputVersion = repo.getConfig().getCore().getPackIndexVersion();
	}

	/**
	 * Check whether object is configured to reuse deltas existing in
	 * repository.
	 * <p>
	 * Default setting: {@value #DEFAULT_REUSE_DELTAS}
	 * </p>
	 *
	 * @return true if object is configured to reuse deltas; false otherwise.
	 */
	public boolean isReuseDeltas() {
		return reuseDeltas;
	}

	/**
	 * Set reuse deltas configuration option for this writer. When enabled,
	 * writer will search for delta representation of object in repository and
	 * use it if possible. Normally, only deltas with base to another object
	 * existing in set of objects to pack will be used. Exception is however
	 * thin-pack (see
	 * {@link #preparePack(Collection, Collection)} and
	 * {@link #preparePack(Iterator)}) where base object must exist on other
	 * side machine.
	 * <p>
	 * When raw delta data is directly copied from a pack file, checksum is
	 * computed to verify data.
	 * </p>
	 * <p>
	 * Default setting: {@value #DEFAULT_REUSE_DELTAS}
	 * </p>
	 *
	 * @param reuseDeltas
	 *            boolean indicating whether or not try to reuse deltas.
	 */
	public void setReuseDeltas(boolean reuseDeltas) {
		this.reuseDeltas = reuseDeltas;
	}

	/**
	 * Checks whether object is configured to reuse existing objects
	 * representation in repository.
	 * <p>
	 * Default setting: {@value #DEFAULT_REUSE_OBJECTS}
	 * </p>
	 *
	 * @return true if writer is configured to reuse objects representation from
	 *         pack; false otherwise.
	 */
	public boolean isReuseObjects() {
		return reuseObjects;
	}

	/**
	 * Set reuse objects configuration option for this writer. If enabled,
	 * writer searches for representation in a pack file. If possible,
	 * compressed data is directly copied from such a pack file. Data checksum
	 * is verified.
	 * <p>
	 * Default setting: {@value #DEFAULT_REUSE_OBJECTS}
	 * </p>
	 *
	 * @param reuseObjects
	 *            boolean indicating whether or not writer should reuse existing
	 *            objects representation.
	 */
	public void setReuseObjects(boolean reuseObjects) {
		this.reuseObjects = reuseObjects;
	}

	/**
	 * Check whether writer can store delta base as an offset (new style
	 * reducing pack size) or should store it as an object id (legacy style,
	 * compatible with old readers).
	 * <p>
	 * Default setting: {@value #DEFAULT_DELTA_BASE_AS_OFFSET}
	 * </p>
	 *
	 * @return true if delta base is stored as an offset; false if it is stored
	 *         as an object id.
	 */
	public boolean isDeltaBaseAsOffset() {
		return deltaBaseAsOffset;
	}

	/**
	 * Set writer delta base format. Delta base can be written as an offset in a
	 * pack file (new approach reducing file size) or as an object id (legacy
	 * approach, compatible with old readers).
	 * <p>
	 * Default setting: {@value #DEFAULT_DELTA_BASE_AS_OFFSET}
	 * </p>
	 *
	 * @param deltaBaseAsOffset
	 *            boolean indicating whether delta base can be stored as an
	 *            offset.
	 */
	public void setDeltaBaseAsOffset(boolean deltaBaseAsOffset) {
		this.deltaBaseAsOffset = deltaBaseAsOffset;
	}

	/**
	 * Get maximum depth of delta chain set up for this writer. Generated chains
	 * are not longer than this value.
	 * <p>
	 * Default setting: {@value #DEFAULT_MAX_DELTA_DEPTH}
	 * </p>
	 *
	 * @return maximum delta chain depth.
	 */
	public int getMaxDeltaDepth() {
		return maxDeltaDepth;
	}

	/**
	 * Set up maximum depth of delta chain for this writer. Generated chains are
	 * not longer than this value. Too low value causes low compression level,
	 * while too big makes unpacking (reading) longer.
	 * <p>
	 * Default setting: {@value #DEFAULT_MAX_DELTA_DEPTH}
	 * </p>
	 *
	 * @param maxDeltaDepth
	 *            maximum delta chain depth.
	 */
	public void setMaxDeltaDepth(int maxDeltaDepth) {
		this.maxDeltaDepth = maxDeltaDepth;
	}

	/** @return true if this writer is producing a thin pack. */
	public boolean isThin() {
		return thin;
	}

	/**
	 * @param packthin
	 *            a boolean indicating whether writer may pack objects with
	 *            delta base object not within set of objects to pack, but
	 *            belonging to party repository (uninteresting/boundary) as
	 *            determined by set; this kind of pack is used only for
	 *            transport; true - to produce thin pack, false - otherwise.
	 */
	public void setThin(final boolean packthin) {
		thin = packthin;
	}

	/**
	 * @return true to ignore objects that are uninteresting and also not found
	 *         on local disk; false to throw a {@link MissingObjectException}
	 *         out of {@link #preparePack(Collection, Collection)} if an
	 *         uninteresting object is not in the source repository. By default,
	 *         true, permitting gracefully ignoring of uninteresting objects.
	 */
	public boolean isIgnoreMissingUninteresting() {
		return ignoreMissingUninteresting;
	}

	/**
	 * @param ignore
	 *            true if writer should ignore non existing uninteresting
	 *            objects during construction set of objects to pack; false
	 *            otherwise - non existing uninteresting objects may cause
	 *            {@link MissingObjectException}
	 */
	public void setIgnoreMissingUninteresting(final boolean ignore) {
		ignoreMissingUninteresting = ignore;
	}

	/**
	 * Set the pack index file format version this instance will create.
	 *
	 * @param version
	 *            the version to write. The special version 0 designates the
	 *            oldest (most compatible) format available for the objects.
	 * @see PackIndexWriter
	 */
	public void setIndexVersion(final int version) {
		outputVersion = version;
	}

	/**
	 * Returns objects number in a pack file that was created by this writer.
	 *
	 * @return number of objects in pack.
	 */
	public int getObjectsNumber() {
		return objectsMap.size();
	}

	/**
	 * Prepare the list of objects to be written to the pack stream.
	 * <p>
	 * Iterator <b>exactly</b> determines which objects are included in a pack
	 * and order they appear in pack (except that objects order by type is not
	 * needed at input). This order should conform general rules of ordering
	 * objects in git - by recency and path (type and delta-base first is
	 * internally secured) and responsibility for guaranteeing this order is on
	 * a caller side. Iterator must return each id of object to write exactly
	 * once.
	 * </p>
	 * <p>
	 * When iterator returns object that has {@link RevFlag#UNINTERESTING} flag,
	 * this object won't be included in an output pack. Instead, it is recorded
	 * as edge-object (known to remote repository) for thin-pack. In such a case
	 * writer may pack objects with delta base object not within set of objects
	 * to pack, but belonging to party repository - those marked with
	 * {@link RevFlag#UNINTERESTING} flag. This type of pack is used only for
	 * transport.
	 * </p>
	 *
	 * @param objectsSource
	 *            iterator of object to store in a pack; order of objects within
	 *            each type is important, ordering by type is not needed;
	 *            allowed types for objects are {@link Constants#OBJ_COMMIT},
	 *            {@link Constants#OBJ_TREE}, {@link Constants#OBJ_BLOB} and
	 *            {@link Constants#OBJ_TAG}; objects returned by iterator may
	 *            be later reused by caller as object id and type are internally
	 *            copied in each iteration; if object returned by iterator has
	 *            {@link RevFlag#UNINTERESTING} flag set, it won't be included
	 *            in a pack, but is considered as edge-object for thin-pack.
	 * @throws IOException
	 *             when some I/O problem occur during reading objects.
	 */
	public void preparePack(final Iterator<RevObject> objectsSource)
			throws IOException {
		while (objectsSource.hasNext()) {
			addObject(objectsSource.next());
		}
	}

	/**
	 * Prepare the list of objects to be written to the pack stream.
	 * <p>
	 * Basing on these 2 sets, another set of objects to put in a pack file is
	 * created: this set consists of all objects reachable (ancestors) from
	 * interesting objects, except uninteresting objects and their ancestors.
	 * This method uses class {@link ObjectWalk} extensively to find out that
	 * appropriate set of output objects and their optimal order in output pack.
	 * Order is consistent with general git in-pack rules: sort by object type,
	 * recency, path and delta-base first.
	 * </p>
	 *
	 * @param interestingObjects
	 *            collection of objects to be marked as interesting (start
	 *            points of graph traversal).
	 * @param uninterestingObjects
	 *            collection of objects to be marked as uninteresting (end
	 *            points of graph traversal).
	 * @throws IOException
	 *             when some I/O problem occur during reading objects.
	 */
	public void preparePack(
			final Collection<? extends ObjectId> interestingObjects,
			final Collection<? extends ObjectId> uninterestingObjects)
			throws IOException {
		ObjectWalk walker = setUpWalker(interestingObjects,
				uninterestingObjects);
		findObjectsToPack(walker);
	}

	/**
	 * Determine if the pack file will contain the requested object.
	 *
	 * @param id
	 *            the object to test the existence of.
	 * @return true if the object will appear in the output pack file.
	 */
	public boolean willInclude(final AnyObjectId id) {
		return objectsMap.get(id) != null;
	}

	/**
	 * Computes SHA-1 of lexicographically sorted objects ids written in this
	 * pack, as used to name a pack file in repository.
	 *
	 * @return ObjectId representing SHA-1 name of a pack that was created.
	 */
	public ObjectId computeName() {
		final MessageDigest md = Constants.newMessageDigest();
		for (ObjectToPack otp : sortByName()) {
			otp.copyRawTo(buf, 0);
			md.update(buf, 0, Constants.OBJECT_ID_LENGTH);
		}
		return ObjectId.fromRaw(md.digest());
	}

	/**
	 * Create an index file to match the pack file just written.
	 * <p>
	 * This method can only be invoked after {@link #preparePack(Iterator)} or
	 * {@link #preparePack(Collection, Collection)} has been
	 * invoked and completed successfully. Writing a corresponding index is an
	 * optional feature that not all pack users may require.
	 *
	 * @param indexStream
	 *            output for the index data. Caller is responsible for closing
	 *            this stream.
	 * @throws IOException
	 *             the index data could not be written to the supplied stream.
	 */
	public void writeIndex(final OutputStream indexStream) throws IOException {
		final List<ObjectToPack> list = sortByName();
		final PackIndexWriter iw;
		if (outputVersion <= 0)
			iw = PackIndexWriter.createOldestPossible(indexStream, list);
		else
			iw = PackIndexWriter.createVersion(indexStream, outputVersion);
		iw.write(list, packcsum);
	}

	private List<ObjectToPack> sortByName() {
		if (sortedByName == null) {
			sortedByName = new ArrayList<ObjectToPack>(objectsMap.size());
			for (List<ObjectToPack> list : objectsLists) {
				for (ObjectToPack otp : list)
					sortedByName.add(otp);
			}
			Collections.sort(sortedByName);
		}
		return sortedByName;
	}

	/**
	 * Write the prepared pack to the supplied stream.
	 * <p>
	 * At first, this method collects and sorts objects to pack, then deltas
	 * search is performed if set up accordingly, finally pack stream is
	 * written. {@link ProgressMonitor} tasks {@value #SEARCHING_REUSE_PROGRESS}
	 * (only if reuseDeltas or reuseObjects is enabled) and
	 * {@value #WRITING_OBJECTS_PROGRESS} are updated during packing.
	 * </p>
	 * <p>
	 * All reused objects data checksum (Adler32/CRC32) is computed and
	 * validated against existing checksum.
	 * </p>
	 *
	 * @param packStream
	 *            output stream of pack data. The stream should be buffered by
	 *            the caller. The caller is responsible for closing the stream.
	 * @throws IOException
	 *             an error occurred reading a local object's data to include in
	 *             the pack, or writing compressed object data to the output
	 *             stream.
	 */
	public void writePack(OutputStream packStream) throws IOException {
		if (reuseDeltas || reuseObjects)
			searchForReuse();

		out = new PackOutputStream(packStream);

		writeMonitor.beginTask(WRITING_OBJECTS_PROGRESS, getObjectsNumber());
		writeHeader();
		writeObjects();
		writeChecksum();

		windowCursor.release();
		writeMonitor.endTask();
	}

	private void searchForReuse() throws IOException {
		initMonitor.beginTask(SEARCHING_REUSE_PROGRESS, getObjectsNumber());
		final Collection<PackedObjectLoader> reuseLoaders = new ArrayList<PackedObjectLoader>();
		for (List<ObjectToPack> list : objectsLists) {
			for (ObjectToPack otp : list) {
				if (initMonitor.isCancelled())
					throw new IOException(
							"Packing cancelled during objects writing");
				reuseLoaders.clear();
				searchForReuse(reuseLoaders, otp);
				initMonitor.update(1);
			}
		}

		initMonitor.endTask();
	}

	private void searchForReuse(
			final Collection<PackedObjectLoader> reuseLoaders,
			final ObjectToPack otp) throws IOException {
		db.openObjectInAllPacks(otp, reuseLoaders, windowCursor);
		if (reuseDeltas) {
			selectDeltaReuseForObject(otp, reuseLoaders);
		}
		// delta reuse is preferred over object reuse
		if (reuseObjects && !otp.hasReuseLoader()) {
			selectObjectReuseForObject(otp, reuseLoaders);
		}
	}

	private void selectDeltaReuseForObject(final ObjectToPack otp,
			final Collection<PackedObjectLoader> loaders) throws IOException {
		PackedObjectLoader bestLoader = null;
		ObjectId bestBase = null;

		for (PackedObjectLoader loader : loaders) {
			ObjectId idBase = loader.getDeltaBase();
			if (idBase == null)
				continue;
			ObjectToPack otpBase = objectsMap.get(idBase);

			// only if base is in set of objects to write or thin-pack's edge
			if ((otpBase != null || (thin && edgeObjects.get(idBase) != null))
			// select smallest possible delta if > 1 available
					&& isBetterDeltaReuseLoader(bestLoader, loader)) {
				bestLoader = loader;
				bestBase = (otpBase != null ? otpBase : idBase);
			}
		}

		if (bestLoader != null) {
			otp.setReuseLoader(bestLoader);
			otp.setDeltaBase(bestBase);
		}
	}

	private static boolean isBetterDeltaReuseLoader(
			PackedObjectLoader currentLoader, PackedObjectLoader loader)
			throws IOException {
		if (currentLoader == null)
			return true;
		if (loader.getRawSize() < currentLoader.getRawSize())
			return true;
		return (loader.getRawSize() == currentLoader.getRawSize()
				&& loader.supportsFastCopyRawData() && !currentLoader
				.supportsFastCopyRawData());
	}

	private void selectObjectReuseForObject(final ObjectToPack otp,
			final Collection<PackedObjectLoader> loaders) {
		for (final PackedObjectLoader loader : loaders) {
			if (loader instanceof WholePackedObjectLoader) {
				otp.setReuseLoader(loader);
				return;
			}
		}
	}

	private void writeHeader() throws IOException {
		System.arraycopy(Constants.PACK_SIGNATURE, 0, buf, 0, 4);
		NB.encodeInt32(buf, 4, PACK_VERSION_GENERATED);
		NB.encodeInt32(buf, 8, getObjectsNumber());
		out.write(buf, 0, 12);
	}

	private void writeObjects() throws IOException {
		for (List<ObjectToPack> list : objectsLists) {
			for (ObjectToPack otp : list) {
				if (writeMonitor.isCancelled())
					throw new IOException(
							"Packing cancelled during objects writing");
				if (!otp.isWritten())
					writeObject(otp);
			}
		}
	}

	private void writeObject(final ObjectToPack otp) throws IOException {
		otp.markWantWrite();
		if (otp.isDeltaRepresentation()) {
			ObjectToPack deltaBase = otp.getDeltaBase();
			assert deltaBase != null || thin;
			if (deltaBase != null && !deltaBase.isWritten()) {
				if (deltaBase.wantWrite()) {
					otp.clearDeltaBase(); // cycle detected
					otp.disposeLoader();
				} else {
					writeObject(deltaBase);
				}
			}
		}

		assert !otp.isWritten();

		out.resetCRC32();
		otp.setOffset(out.length());

		final PackedObjectLoader reuse = open(otp);
		if (reuse != null) {
			try {
				if (otp.isDeltaRepresentation()) {
					writeDeltaObjectReuse(otp, reuse);
				} else {
					writeObjectHeader(otp.getType(), reuse.getSize());
					reuse.copyRawData(out, buf, windowCursor);
				}
			} finally {
				reuse.endCopyRawData();
			}
		} else if (otp.isDeltaRepresentation()) {
			throw new IOException("creating deltas is not implemented");
		} else {
			writeWholeObjectDeflate(otp);
		}
		otp.setCRC(out.getCRC32());

		writeMonitor.update(1);
	}

	private PackedObjectLoader open(final ObjectToPack otp) throws IOException {
		for (;;) {
			PackedObjectLoader reuse = otp.useLoader();
			if (reuse == null) {
				return null;
			}

			try {
				reuse.beginCopyRawData();
				return reuse;
			} catch (IOException err) {
				// The pack we found the object in originally is gone, or
				// it has been overwritten with a different layout.
				//
				otp.clearDeltaBase();
				searchForReuse(new ArrayList<PackedObjectLoader>(), otp);
				continue;
			}
		}
	}

	private void writeWholeObjectDeflate(final ObjectToPack otp)
			throws IOException {
		final ObjectLoader loader = db.openObject(windowCursor, otp);
		final byte[] data = loader.getCachedBytes();
		writeObjectHeader(otp.getType(), data.length);
		deflater.reset();
		deflater.setInput(data, 0, data.length);
		deflater.finish();
		do {
			final int n = deflater.deflate(buf, 0, buf.length);
			if (n > 0)
				out.write(buf, 0, n);
		} while (!deflater.finished());
	}

	private void writeDeltaObjectReuse(final ObjectToPack otp,
			final PackedObjectLoader reuse) throws IOException {
		if (deltaBaseAsOffset && otp.getDeltaBase() != null) {
			writeObjectHeader(Constants.OBJ_OFS_DELTA, reuse.getRawSize());

			final ObjectToPack deltaBase = otp.getDeltaBase();
			long offsetDiff = otp.getOffset() - deltaBase.getOffset();
			int pos = buf.length - 1;
			buf[pos] = (byte) (offsetDiff & 0x7F);
			while ((offsetDiff >>= 7) > 0) {
				buf[--pos] = (byte) (0x80 | (--offsetDiff & 0x7F));
			}

			out.write(buf, pos, buf.length - pos);
		} else {
			writeObjectHeader(Constants.OBJ_REF_DELTA, reuse.getRawSize());
			otp.getDeltaBaseId().copyRawTo(buf, 0);
			out.write(buf, 0, Constants.OBJECT_ID_LENGTH);
		}
		reuse.copyRawData(out, buf, windowCursor);
	}

	private void writeObjectHeader(final int objectType, long dataLength)
			throws IOException {
		long nextLength = dataLength >>> 4;
		int size = 0;
		buf[size++] = (byte) ((nextLength > 0 ? 0x80 : 0x00)
				| (objectType << 4) | (dataLength & 0x0F));
		dataLength = nextLength;
		while (dataLength > 0) {
			nextLength >>>= 7;
			buf[size++] = (byte) ((nextLength > 0 ? 0x80 : 0x00) | (dataLength & 0x7F));
			dataLength = nextLength;
		}
		out.write(buf, 0, size);
	}

	private void writeChecksum() throws IOException {
		packcsum = out.getDigest();
		out.write(packcsum);
	}

	private ObjectWalk setUpWalker(
			final Collection<? extends ObjectId> interestingObjects,
			final Collection<? extends ObjectId> uninterestingObjects)
			throws MissingObjectException, IOException,
			IncorrectObjectTypeException {
		final ObjectWalk walker = new ObjectWalk(db);
		walker.setRetainBody(false);
		walker.sort(RevSort.TOPO);
		walker.sort(RevSort.COMMIT_TIME_DESC, true);
		if (thin)
			walker.sort(RevSort.BOUNDARY, true);

		for (ObjectId id : interestingObjects) {
			RevObject o = walker.parseAny(id);
			walker.markStart(o);
		}
		if (uninterestingObjects != null) {
			for (ObjectId id : uninterestingObjects) {
				final RevObject o;
				try {
					o = walker.parseAny(id);
				} catch (MissingObjectException x) {
					if (ignoreMissingUninteresting)
						continue;
					throw x;
				}
				walker.markUninteresting(o);
			}
		}
		return walker;
	}

	private void findObjectsToPack(final ObjectWalk walker)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		initMonitor.beginTask(COUNTING_OBJECTS_PROGRESS,
				ProgressMonitor.UNKNOWN);
		RevObject o;

		while ((o = walker.next()) != null) {
			addObject(o);
			initMonitor.update(1);
		}
		while ((o = walker.nextObject()) != null) {
			addObject(o);
			initMonitor.update(1);
		}
		initMonitor.endTask();
	}

	/**
	 * Include one object to the output file.
	 * <p>
	 * Objects are written in the order they are added. If the same object is
	 * added twice, it may be written twice, creating a larger than necessary
	 * file.
	 *
	 * @param object
	 *            the object to add.
	 * @throws IncorrectObjectTypeException
	 *             the object is an unsupported type.
	 */
	public void addObject(final RevObject object)
			throws IncorrectObjectTypeException {
		if (object.has(RevFlag.UNINTERESTING)) {
			edgeObjects.add(object);
			thin = true;
			return;
		}

		final ObjectToPack otp = new ObjectToPack(object, object.getType());
		try {
			objectsLists[object.getType()].add(otp);
		} catch (ArrayIndexOutOfBoundsException x) {
			throw new IncorrectObjectTypeException(object,
					"COMMIT nor TREE nor BLOB nor TAG");
		} catch (UnsupportedOperationException x) {
			// index pointing to "dummy" empty list
			throw new IncorrectObjectTypeException(object,
					"COMMIT nor TREE nor BLOB nor TAG");
		}
		objectsMap.add(otp);
	}

	/**
	 * Class holding information about object that is going to be packed by
	 * {@link PackWriter}. Information include object representation in a
	 * pack-file and object status.
	 *
	 */
	static class ObjectToPack extends PackedObjectInfo {
		private ObjectId deltaBase;

		private PackedObjectLoader reuseLoader;

		/**
		 * Bit field, from bit 0 to bit 31:
		 * <ul>
		 * <li>1 bit: wantWrite</li>
		 * <li>3 bits: type</li>
		 * <li>28 bits: deltaDepth</li>
		 * </ul>
		 */
		private int flags;

		/**
		 * Construct object for specified object id. <br/> By default object is
		 * marked as not written and non-delta packed (as a whole object).
		 *
		 * @param src
		 *            object id of object for packing
		 * @param type
		 *            real type code of the object, not its in-pack type.
		 */
		ObjectToPack(AnyObjectId src, final int type) {
			super(src);
			flags |= type << 1;
		}

		/**
		 * @return delta base object id if object is going to be packed in delta
		 *         representation; null otherwise - if going to be packed as a
		 *         whole object.
		 */
		ObjectId getDeltaBaseId() {
			return deltaBase;
		}

		/**
		 * @return delta base object to pack if object is going to be packed in
		 *         delta representation and delta is specified as object to
		 *         pack; null otherwise - if going to be packed as a whole
		 *         object or delta base is specified only as id.
		 */
		ObjectToPack getDeltaBase() {
			if (deltaBase instanceof ObjectToPack)
				return (ObjectToPack) deltaBase;
			return null;
		}

		/**
		 * Set delta base for the object. Delta base set by this method is used
		 * by {@link PackWriter} to write object - determines its representation
		 * in a created pack.
		 *
		 * @param deltaBase
		 *            delta base object or null if object should be packed as a
		 *            whole object.
		 *
		 */
		void setDeltaBase(ObjectId deltaBase) {
			this.deltaBase = deltaBase;
		}

		void clearDeltaBase() {
			this.deltaBase = null;
		}

		/**
		 * @return true if object is going to be written as delta; false
		 *         otherwise.
		 */
		boolean isDeltaRepresentation() {
			return deltaBase != null;
		}

		/**
		 * Check if object is already written in a pack. This information is
		 * used to achieve delta-base precedence in a pack file.
		 *
		 * @return true if object is already written; false otherwise.
		 */
		boolean isWritten() {
			return getOffset() != 0;
		}

		PackedObjectLoader useLoader() {
			final PackedObjectLoader r = reuseLoader;
			reuseLoader = null;
			return r;
		}

		boolean hasReuseLoader() {
			return reuseLoader != null;
		}

		void setReuseLoader(PackedObjectLoader reuseLoader) {
			this.reuseLoader = reuseLoader;
		}

		void disposeLoader() {
			this.reuseLoader = null;
		}

		int getType() {
			return (flags>>1) & 0x7;
		}

		int getDeltaDepth() {
			return flags >>> 4;
		}

		void updateDeltaDepth() {
			final int d;
			if (deltaBase instanceof ObjectToPack)
				d = ((ObjectToPack) deltaBase).getDeltaDepth() + 1;
			else if (deltaBase != null)
				d = 1;
			else
				d = 0;
			flags = (d << 4) | flags & 0x15;
		}

		boolean wantWrite() {
			return (flags & 1) == 1;
		}

		void markWantWrite() {
			flags |= 1;
		}
	}
}
