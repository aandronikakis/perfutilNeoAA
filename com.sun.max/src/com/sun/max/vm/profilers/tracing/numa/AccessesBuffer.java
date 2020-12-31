/*
 * Copyright (c) 2020, APT Group, School of Computer Science,
 * The University of Manchester. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package com.sun.max.vm.profilers.tracing.numa;

import com.sun.max.annotate.INTRINSIC;
import com.sun.max.unsafe.Pointer;
import com.sun.max.vm.reference.Reference;
import com.sun.max.vm.runtime.FatalError;

import static com.sun.max.vm.intrinsics.MaxineIntrinsicIDs.UNSAFE_CAST;
import static com.sun.max.vm.thread.VmThreadLocal.ACCESSES_BUFFER;

public class AccessesBuffer {
    /**
     * An AccessesBuffer instance is a thread local object that stores the number of object accesses the thread performs.
     * They are broken down per type (rows) and per thread that allocated the accessed object (columns).
     * The values are stores in the counterSet multi-dimensional array.
     *
     *                               allocator thread id
     *             type            | 0 | 1 | 2 | ... | N |
     * +-------------------------------------------------+
     * 0) LOCAL_TUPLE_WRITE        |   |   |   | ... |   |
     * 1) INTERNODE_TUPLE_WRITE    |   |   |   | ... |   |
     * 2) INTERBLADE_TUPLE_WRITE   |   |   |   | ... |   |
     * 3) LOCAL_ARRAY_WRITE        |   |   |   | ... |   |
     * 4) INTERNODE_ARRAY_WRITE    |   |   |   | ... |   |
     * 5) INTERBLADE_ARRAY_WRITE   |   |   |   | ... |   |
     * 6) LOCAL_TUPLE_READ         |   |   |   | ... |   |
     * 7) INTERNODE_TUPLE_READ     |   |   |   | ... |   |
     * 8) INTERBLADE_TUPLE_READ    |   |   |   | ... |   |
     * 9) LOCAL_ARRAY_READ         |   |   |   | ... |   |
     * 10) INTERNODE_ARRAY_READ    |   |   |   | ... |   |
     * 11) INTERBLADE_ARRAY_READ   |   |   |   | ... |   |
     * +-------------------------------------------------+
     *
     * The above structure is stored in the counterSet array.
     * Each cell represents an individual counter.
     *
     * Note: AllocatorId = 0 denotes that the accessed object has been allocated in an early phase of the vm.
     */

    public int[][] counterSet;
    final int numOfAccessTypes = 12;
    /**
     * Arbitrarily set to support up to 16 threads.
     * In case more are needed, it will self expand.
     */
    public int numOfThreads = 17;

    public AccessesBuffer() {
        counterSet = new int[numOfAccessTypes][numOfThreads];
    }

    public void increment(int accessType, int allocatorId) {
        try {
            counterSet[accessType][allocatorId]++;
        } catch (ArrayIndexOutOfBoundsException ex) {
            expand(allocatorId);
            counterSet[accessType][allocatorId]++;
        }
    }

    public void expand(int faultyIndex) {
        int newSize = faultyIndex + 1;
        int[][] newCounters = new int[numOfAccessTypes][newSize];

        //transfer values
        for (int i = 0; i < numOfAccessTypes; i++) {
            for (int j = 0; j < numOfThreads; j++) {
                newCounters[i][j] = counterSet[i][j];
            }
        }
        numOfThreads = newSize;
        counterSet = newCounters;
    }

    @INTRINSIC(UNSAFE_CAST)
    public static native AccessesBuffer asAccessesBuffer(Object object);

    public static void setForCurrentThread(Pointer etla, AccessesBuffer buffer) {
        ACCESSES_BUFFER.store(etla, Reference.fromJava(buffer));
    }

    public static AccessesBuffer getForCurrentThread(Pointer etla) {
        final Reference reference = ACCESSES_BUFFER.loadRef(etla);
        if (reference.isZero()) {
            FatalError.unexpected("Access Buffer is null");
        }
        return asAccessesBuffer(reference.toJava());
    }
}
