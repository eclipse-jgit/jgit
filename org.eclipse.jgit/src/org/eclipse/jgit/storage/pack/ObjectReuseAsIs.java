/*
 * Copyright (C) 2010, Google Inc.
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

package org.eclipse.jgit.storage.pack;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.StoredObjectRepresentationNotAvailableException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.revwalk.RevObject;

/**
 * Extension of {@link ObjectReader} that supports reusing objects in packs.
 * <p>
 * {@code ObjectReader} implementations may also optionally implement this
 * interface to support {@link PackWriter} with a means of copying an object
 * that is already in pack encoding format directly into the output stream,
 * without incurring decompression and recompression overheads.
 */
public interface ObjectReuseAsIs {
	/**
	 * Allocate a new {@code PackWriter} state structure for an object.
	 * <p>
	 * {@link PackWriter} allocates these objects to keep track of the
	 * per-object state, and how to load the objects efficiently into the
	 * generated stream. Implementers may subclass this type with additional
	 * object state, such as to remember what file and offset contains the
	 * object's pack encoded data.
	 *
	 * @param obj
	 *            identity of the object that will be packed. The object's
	 *            parsed status is undefined here. Implementers must not rely on
	 *            the object being parsed.
	 * @return a new instance for this object.
	 */
	public ObjectToPack newObjectToPack(RevObject obj);

	/**
	 * Select the best object representation for a packer.
	 * <p>
	 * Implementations should iterate through all available representations of
	 * an object, and pass them in turn to the PackWriter though
	 * {@link PackWriter#select(ObjectToPack, StoredObjectRepresentation)} so
	 * the writer can select the most suitable representation to reuse into the
	 * output stream.
	 * <p>
	 * If the implementation returns CachedPack from {@link #getCachedPacks()},
	 * it must consider the representation of any object that is stored in any
	 * of the offered CachedPacks. PackWriter relies on this behavior to prune
	 * duplicate objects out of the pack stream when it selects a CachedPack and
	 * the object was also reached through the thin-pack enumeration.
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
	 * @throws MissingObjectException
	 *             there is no representation available for the object, as it is
	 *             no longer in the repository. Packing will abort.
	 * @throws IOException
	 *             the repository cannot be accessed. Packing will abort.
	 */
	public void selectObjectRepresentation(PackWriter packer,
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
	 * Invoking {@link PackOutputStream#writeObject(ObjectToPack)} will cause
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
	 * @throws IOException
	 *             the stream cannot be written to, or one or more required
	 *             objects cannot be accessed from the object database.
	 */
	public void writeObjects(PackOutputStream out, List<ObjectToPack> list)
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
	 * @throws StoredObjectRepresentationNotAvailableException
	 *             the previously selected representation is no longer
	 *             available. If thrown before {@code out.writeHeader} the pack
	 *             writer will try to find another representation, and write
	 *             that one instead. If throw after {@code out.writeHeader},
	 *             packing will abort.
	 * @throws IOException
	 *             the stream's write method threw an exception. Packing will
	 *             abort.
	 */
	public void copyObjectAsIs(PackOutputStream out, ObjectToPack otp,
			boolean validate) throws IOException,
			StoredObjectRepresentationNotAvailableException;

	/**
	 * Obtain the available cached packs.
	 * <p>
	 * A cached pack has known starting points and may be sent entirely as-is,
	 * with almost no effort on the sender's part.
	 *
	 * @return the available cached packs.
	 * @throws IOException
	 *             the cached packs cannot be listed from the repository.
	 *             Callers may choose to ignore this and continue as-if there
	 *             were no cached packs.
	 */
	public Collection<CachedPack> getCachedPacks() throws IOException;

	/**
	 * Append an entire pack's contents onto the output stream.
	 * <p>
	 * The entire pack, excluding its header and trailing footer is sent.
	 *
	 * @param out
	 *            stream to append the pack onto.
	 * @param pack
	 *            the cached pack to send.
	 * @param validate
	 *            if true the representation must be validated and not be
	 *            corrupt before being reused. If false, validation may be
	 *            skipped as it will be performed elsewhere in the processing
	 *            pipeline.
	 * @throws IOException
	 *             the pack cannot be read, or stream did not accept a write.
	 */
	public abstract void copyPackAsIs(PackOutputStream out, CachedPack pack,
			boolean validate) throws IOException;
}
