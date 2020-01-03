/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.pack;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.BitmapIndex.BitmapBuilder;
import org.eclipse.jgit.lib.ProgressMonitor;

/**
 * Extension of {@link org.eclipse.jgit.lib.ObjectReader} that supports reusing
 * objects in packs.
 * <p>
 * {@code ObjectReader} implementations may also optionally implement this
 * interface to support
 * {@link org.eclipse.jgit.internal.storage.pack.PackWriter} with a means of
 * copying an object that is already in pack encoding format directly into the
 * output stream, without incurring decompression and recompression overheads.
 */
public interface ObjectReuseAsIs {
	/**
	 * Allocate a new {@code PackWriter} state structure for an object.
	 * <p>
	 * {@link org.eclipse.jgit.internal.storage.pack.PackWriter} allocates these
	 * objects to keep track of the per-object state, and how to load the
	 * objects efficiently into the generated stream. Implementers may subclass
	 * this type with additional object state, such as to remember what file and
	 * offset contains the object's pack encoded data.
	 *
	 * @param objectId
	 *            the id of the object that will be packed.
	 * @param type
	 *            the Git type of the object that will be packed.
	 * @return a new instance for this object.
	 */
	ObjectToPack newObjectToPack(AnyObjectId objectId, int type);

	/**
	 * Select the best object representation for a packer.
	 * <p>
	 * Implementations should iterate through all available representations of
	 * an object, and pass them in turn to the PackWriter though
	 * {@link org.eclipse.jgit.internal.storage.pack.PackWriter#select(ObjectToPack, StoredObjectRepresentation)}
	 * so the writer can select the most suitable representation to reuse into
	 * the output stream.
	 * <p>
	 * If the implementation returns CachedPack from
	 * {@link #getCachedPacksAndUpdate(BitmapBuilder)} it must consider the
	 * representation of any object that is stored in any of the offered
	 * CachedPacks. PackWriter relies on this behavior to prune duplicate
	 * objects out of the pack stream when it selects a CachedPack and the
	 * object was also reached through the thin-pack enumeration.
	 * <p>
	 * The implementation may choose to consider multiple objects at once on
	 * concurrent threads, but must evaluate all representations of an object
	 * within the same thread.
	 *
	 * @param packer
	 *            the packer that will write the object in the near future.
	 * @param monitor
	 *            progress monitor, implementation should update the monitor
	 *            once for each item in the iteration when selection is done.
	 * @param objects
	 *            the objects that are being packed.
	 * @throws org.eclipse.jgit.errors.MissingObjectException
	 *             there is no representation available for the object, as it is
	 *             no longer in the repository. Packing will abort.
	 * @throws java.io.IOException
	 *             the repository cannot be accessed. Packing will abort.
	 */
	void selectObjectRepresentation(PackWriter packer,
			ProgressMonitor monitor, Iterable<ObjectToPack> objects)
			throws IOException, MissingObjectException;

	/**
	 * Write objects to the pack stream in roughly the order given.
	 *
	 * {@code PackWriter} invokes this method to write out one or more objects,
	 * in approximately the order specified by the iteration over the list. A
	 * simple implementation of this method would just iterate the list and
	 * output each object:
	 *
	 * <pre>
	 * for (ObjectToPack obj : list)
	 *   out.writeObject(obj)
	 * </pre>
	 *
	 * However more sophisticated implementors may try to perform some (small)
	 * reordering to access objects that are stored close to each other at
	 * roughly the same time. Implementations may choose to write objects out of
	 * order, but this may increase pack file size due to using a larger header
	 * format to reach a delta base that is later in the stream. It may also
	 * reduce data locality for the reader, slowing down data access.
	 *
	 * Invoking
	 * {@link org.eclipse.jgit.internal.storage.pack.PackOutputStream#writeObject(ObjectToPack)}
	 * will cause
	 * {@link #copyObjectAsIs(PackOutputStream, ObjectToPack, boolean)} to be
	 * invoked recursively on {@code this} if the current object is scheduled
	 * for reuse.
	 *
	 * @param out
	 *            the stream to write each object to.
	 * @param list
	 *            the list of objects to write. Objects should be written in
	 *            approximately this order. Implementors may resort the list
	 *            elements in-place during writing if desired.
	 * @throws java.io.IOException
	 *             the stream cannot be written to, or one or more required
	 *             objects cannot be accessed from the object database.
	 */
	void writeObjects(PackOutputStream out, List<ObjectToPack> list)
			throws IOException;

	/**
	 * Output a previously selected representation.
	 * <p>
	 * {@code PackWriter} invokes this method only if a representation
	 * previously given to it by {@code selectObjectRepresentation} was chosen
	 * for reuse into the output stream. The {@code otp} argument is an instance
	 * created by this reader's own {@code newObjectToPack}, and the
	 * representation data saved within it also originated from this reader.
	 * <p>
	 * Implementors must write the object header before copying the raw data to
	 * the output stream. The typical implementation is like:
	 *
	 * <pre>
	 * MyToPack mtp = (MyToPack) otp;
	 * byte[] raw;
	 * if (validate)
	 * 	 raw = validate(mtp); // throw SORNAE here, if at all
	 * else
	 * 	 raw = readFast(mtp);
	 * out.writeHeader(mtp, mtp.inflatedSize);
	 * out.write(raw);
	 * </pre>
	 *
	 * @param out
	 *            stream the object should be written to.
	 * @param otp
	 *            the object's saved representation information.
	 * @param validate
	 *            if true the representation must be validated and not be
	 *            corrupt before being reused. If false, validation may be
	 *            skipped as it will be performed elsewhere in the processing
	 *            pipeline.
	 * @throws org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException
	 *             the previously selected representation is no longer
	 *             available. If thrown before {@code out.writeHeader} the pack
	 *             writer will try to find another representation, and write
	 *             that one instead. If throw after {@code out.writeHeader},
	 *             packing will abort.
	 * @throws java.io.IOException
	 *             the stream's write method threw an exception. Packing will
	 *             abort.
	 */
	void copyObjectAsIs(PackOutputStream out, ObjectToPack otp,
			boolean validate) throws IOException,
			StoredObjectRepresentationNotAvailableException;

	/**
	 * Append an entire pack's contents onto the output stream.
	 * <p>
	 * The entire pack, excluding its header and trailing footer is sent.
	 *
	 * @param out
	 *            stream to append the pack onto.
	 * @param pack
	 *            the cached pack to send.
	 * @throws java.io.IOException
	 *             the pack cannot be read, or stream did not accept a write.
	 */
	void copyPackAsIs(PackOutputStream out, CachedPack pack)
			throws IOException;

	/**
	 * Obtain the available cached packs that match the bitmap and update
	 * the bitmap by removing the items that are in the CachedPack.
	 * <p>
	 * A cached pack has known starting points and may be sent entirely as-is,
	 * with almost no effort on the sender's part.
	 *
	 * @param needBitmap
	 *            the bitmap that contains all of the objects the client wants.
	 * @return the available cached packs.
	 * @throws java.io.IOException
	 *             the cached packs cannot be listed from the repository.
	 *             Callers may choose to ignore this and continue as-if there
	 *             were no cached packs.
	 */
	Collection<CachedPack> getCachedPacksAndUpdate(
			BitmapBuilder needBitmap) throws IOException;
}
