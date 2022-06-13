/*
 * Copyright (C) 2022, GerritForge Ltd
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.revwalk;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;

import java.io.IOException;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.stream.Stream;

/**
 * A queue of commits sorted by commit time order using a Java PriorityQueue.
 */
public class DateRevPriorityQueue extends DateRevQueue {
    private PriorityQueue<RevCommit> priorityQueue;

    /**
     * Create an empty date queue.
     */
    public DateRevPriorityQueue() {
        this(false);
    }

    DateRevPriorityQueue(boolean firstParent) {
        this(firstParent, 1024);
    }

    DateRevPriorityQueue(boolean firstParent, int initialCapacity) {
        super(firstParent);
        initPriorityQueue(initialCapacity);
    }

    private void initPriorityQueue(int initialCapacity) {
        priorityQueue = new PriorityQueue<>(initialCapacity,
                Comparator.comparingInt(RevCommit::getCommitTime).reversed());
    }

    DateRevPriorityQueue(Generator s) throws MissingObjectException,
            IncorrectObjectTypeException, IOException {
        this(s.firstParent);
        for (; ; ) {
            final RevCommit c = s.next();
            if (c == null)
                break;
            add(c);
        }
    }

    void setCapacity(int capacity) {
        if(capacity > 0) {
            Stream<RevCommit> currentElements = priorityQueue.stream();
            initPriorityQueue(capacity);
            currentElements.forEach(priorityQueue::add);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(RevCommit c) {
        priorityQueue.add(c);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RevCommit next() {
        return priorityQueue.poll();
    }

    /**
     * Peek at the next commit, without removing it.
     *
     * @return the next available commit; null if there are no commits left.
     */
    public RevCommit peek() {
        return priorityQueue.peek();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        priorityQueue.clear();
    }

    @Override
    boolean everbodyHasFlag(int f) {
        return !priorityQueue.stream().filter(c -> (c.flags & f) == 0).findAny().isPresent();
    }

    @Override
    boolean anybodyHasFlag(int f) {
        return priorityQueue.stream().filter(c -> (c.flags & f) != 0).findAny().isPresent();
    }

    @Override
    int outputType() {
        return outputType | SORT_COMMIT_TIME_DESC;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder s = new StringBuilder();
        priorityQueue.forEach(c -> describe(s, c));
        return s.toString();
    }
}
