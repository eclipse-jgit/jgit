/*
 * Copyright (C) 2025, Google LLC.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.blame;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.blame.cache.BlameCache;
import org.eclipse.jgit.blame.cache.CacheRegion;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

class InMemoryBlameCache implements BlameCache {

    private final Map<Key, List<CacheRegion>> cache = new HashMap<>();

    private final List<ObjectId> hits = new ArrayList<>();

    private final String description;

    public InMemoryBlameCache(String description) {
        this.description = description;
    }

    @Override
    public List<CacheRegion> get(Repository repo, ObjectId commitId,
                                 String path) throws IOException {
        List<CacheRegion> result = cache
                .get(new Key(commitId.name(), path));
        if (result != null) {
            hits.add(commitId);
        }
        return result;
    }

    public void put(ObjectId commitId, String path,
                    List<CacheRegion> cachedRegions) {
        cache.put(new Key(commitId.name(), path), cachedRegions);
    }

    List<ObjectId> getHits() {
        return hits;
    }

    @Override
    public String toString() {
        return "InMemoryCache: " + description;
    }

    record Key(String commitId, String path) {
    }
}