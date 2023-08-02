/*
 * Copyright (C) 2019, Google LLC and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import com.googlecode.javaewah.EWAHCompressedBitmap;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndex;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndexV1;
import org.eclipse.jgit.internal.storage.file.PackBitmapIndexV1.IdxPositionBitmap;
import org.eclipse.jgit.internal.storage.file.PackIndex;
import org.eclipse.jgit.internal.storage.file.PackReverseIndex;
import org.eclipse.jgit.internal.storage.file.PackReverseIndexFactory;
import org.eclipse.jgit.lib.ObjectId;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.ExplicitBooleanOptionHandler;

@Command(common = true, usage = "usage_convertRefStorage")
class ConvertRefStorage extends TextBuiltin {

	@Option(name = "--format", usage = "usage_convertRefStorageFormat")
	private String format = "reftable"; //$NON-NLS-1$

	@Option(name = "--backup", handler = ExplicitBooleanOptionHandler.class, aliases = {
			"-b" }, usage = "usage_convertRefStorageBackup")
	private boolean backup = true;

	@Option(name = "--reflogs", handler = ExplicitBooleanOptionHandler.class, aliases = {
			"-r" }, usage = "usage_convertRefStorageRefLogs")
	private boolean writeLogs = true;

	@Argument()
	String indexName;
	@Argument(index = 1)
	String bitmapName;

	@Override
	protected void run() throws Exception {
      File bitmapFile = new File(bitmapName);
      File packIndexFile = new File(indexName);

      PackIndex packIndex = PackIndex.open(packIndexFile);
			PackReverseIndex pri = PackReverseIndexFactory.computeFromIndex(packIndex);
      PackBitmapIndex map = PackBitmapIndex.open(bitmapFile, packIndex, pri);

			PackBitmapIndexV1 v1 = (PackBitmapIndexV1) map;

			List<ObjectId> commits = new ArrayList<>();
			for (int i = 0; i < v1.idxPositionBitmapList.size(); i++)  {
				IdxPositionBitmap ipb = v1.idxPositionBitmapList.get(i);
				ObjectId id = ipb.sb.toObjectId();
				commits.add(id);
			}

			for (int i = 0; i < v1.idxPositionBitmapList.size(); i++) {
				ObjectId id = commits.get(i);
				IdxPositionBitmap ipb =				v1.idxPositionBitmapList.get(i);
				boolean related = false;
				boolean ancestor = false;
				if (ipb.xorIdxPositionBitmap != null) {
					IdxPositionBitmap deltaBase = ipb.xorIdxPositionBitmap;
					ancestor =  ipb.sb.getBitmap().get(deltaBase.nthObjectId);
					related = ancestor || deltaBase.sb.getBitmap().get(ipb.nthObjectId);
				}
				int chain = 0;
				int lastPos = i;
				for (IdxPositionBitmap p = ipb; p != null; p = p.xorIdxPositionBitmap) {
					lastPos = p.xorIndex;
					chain++;
				}

				System.out.println(String.format(
						"i=%d, id=%s, base=%d (delta=%d), parent=%s, related=%s, chain=%d, root=%d",
						i, id.getName(), ipb.xorIndex, i - ipb.xorIndex, ancestor, related, chain, lastPos));
    }
	}}
