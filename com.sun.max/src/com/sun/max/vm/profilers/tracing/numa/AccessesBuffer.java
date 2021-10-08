/*
 * Copyright (c) 2020-2021, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
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

import com.sun.max.annotate.INLINE;
import com.sun.max.annotate.INTRINSIC;
import com.sun.max.unsafe.Pointer;
import com.sun.max.vm.Log;
import com.sun.max.vm.reference.Reference;
import com.sun.max.vm.runtime.FatalError;
import com.sun.max.vm.thread.VmThreadLocal;

import static com.sun.max.vm.intrinsics.MaxineIntrinsicIDs.UNSAFE_CAST;
import static com.sun.max.vm.thread.VmThreadLocal.ACCESSES_BUFFER;

public class AccessesBuffer extends ProfilingArtifact{
    /**
     * This class inherits {@link ProfilingArtifact} and implements the {@link AccessesBuffer} type of artifact.
     * An {@link AccessesBuffer} instance is a thread local object ({@link VmThreadLocal#ACCESSES_BUFFER} points its {@link Reference})
     * and counts the object accesses performed by the thread.
     * The counts are stored in the {@link #counterSet} multi-dimensional array.
     * As depicted below, the rows denote an access type while the columns the id of the thread that allocated the accessed object (allocator thread id).
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
     * Note: AllocatorId = 0 denotes that the accessed object has been allocated in an early phase of the vm.
     */

    public long[][] counterSet;
    final int numOfAccessTypes = 12;
    /**
     * Arbitrarily set to support up to 16 threads.
     * In case more are needed, it will self expand.
     *
     * (Hack): Currently set to 128 + 1 to avoid expand method call.
     * TODO: Apply a proper fix to expand mechanism to avoid new object creation when allocations are not allowed.
     */
    public int numOfThreads = 129;

    public AccessesBuffer(int threadId) {
        this.threadId = threadId;
        this.simpleName = getClass().getSimpleName();
        counterSet = new long[numOfAccessTypes][numOfThreads];
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
        long[][] newCounters = new long[numOfAccessTypes][newSize];

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

    public static AccessesBuffer getForCurrentThread(Pointer etla) {
        final Reference reference = ACCESSES_BUFFER.loadRef(etla);
        if (reference.isZero()) {
            FatalError.unexpected("Access Buffer is null");
        }
        return asAccessesBuffer(reference.toJava());
    }

    @INLINE
    public static void setForCurrentThread(Pointer etla, AccessesBuffer buffer) {
        ACCESSES_BUFFER.store(etla, Reference.fromJava(buffer));
    }

    public static Reference getBufferReference(Pointer etla) {
        return VmThreadLocal.ACCESSES_BUFFER.loadRef(etla);
    }

    @Override
    int getThreadId() {
        return threadId;
    }

    @Override
    String getSimpleName() {
        return simpleName;
    }

    @Override
    void print(int profilingCycle, int b) {
        for (int type = 0; type < numOfAccessTypes; type++) {
            for (int allocatorThread = 0; allocatorThread < numOfThreads; allocatorThread++) {
                try {
                    final long count = counterSet[type][allocatorThread];
                    if (count != 0) {
                        Log.print("(accessCounter);");
                        Log.print(profilingCycle);
                        Log.print(";");
                        Log.print(NUMAProfiler.objectAccessCounterNames[type]);
                        Log.print(";");
                        Log.print(threadId);
                        Log.print(";");
                        Log.print(allocatorThread);
                        Log.print(";");
                        Log.println(count);
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    Log.print("=== ArrayIndexOutOfBoundsException at [");
                    Log.print(type);
                    Log.print(",");
                    Log.print(allocatorThread);
                    Log.print("] with max[");
                    Log.print(numOfAccessTypes);
                    Log.print(",");
                    Log.print(numOfThreads);
                    Log.println("]");

                }
            }
        }
    }

    @Override
    void resetArtifact() {
        for (int type = 0; type < numOfAccessTypes; type++) {
            for (int allocatorThread = 0; allocatorThread < numOfThreads; allocatorThread++) {
                counterSet[type][allocatorThread] = 0;
            }
        }
    }

    @Override
    public void deallocateArtifact() {

    }

}
