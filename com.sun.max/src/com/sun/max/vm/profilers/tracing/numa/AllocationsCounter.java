/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
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

import com.sun.max.annotate.INLINE;
import com.sun.max.annotate.INTRINSIC;
import com.sun.max.annotate.NEVER_INLINE;
import com.sun.max.annotate.NO_SAFEPOINT_POLLS;
import com.sun.max.unsafe.Pointer;
import com.sun.max.vm.Log;
import com.sun.max.vm.reference.Reference;
import com.sun.max.vm.thread.VmThreadLocal;

import static com.sun.max.vm.intrinsics.MaxineIntrinsicIDs.UNSAFE_CAST;

/**
 * This class inherits {@link ProfilingArtifact} and implements the {@link AllocationsCounter} type of artifact.
 * It counts the object allocations (tuples & arrays) along with size-related metrics which are described below.
 * Each {@link AllocationsCounter} is a thread-local object.
 * After a thread's termination the {@link Reference} of the {@link AllocationsCounter} is stored in the {@link ProfilingArtifactsQueue} in order to be dumped as the profiling output during the next stop-the-world phase.
 *
 * Counted metrics:
 * Tuples/Arrays count: How many tuples/arrays have been allocated.
 * Total Tuples/Arrays size: The total size of tuples/arrays allocations in bytes.
 * Total Array Length
 *
 * The above are used to calculate the average tuple/array size (in Mb) as well as the average array length.
 */

public class AllocationsCounter extends ProfilingArtifact{

    // A constant coefficient used in calculating size in Mbytes
    final static double mbCoef = 0.000000954;

    int threadId;
    long tupleCount; // object instances count
    long totalTupleSize;
    long arrayCount; // arrays count
    long totalArraySize;
    long totalArrayLength;

    double avgTupleSize;
    double avgArraySize;
    double avgArrayLength; // arrays length

    public AllocationsCounter(int threadId) {
        this.threadId = threadId;
        tupleCount = 0;
        totalTupleSize = 0;

        arrayCount = 0;
        totalArraySize = 0;
        totalArrayLength = 0;

        avgTupleSize = 0;
        avgArraySize = 0;
        avgArrayLength = 0;
    }

    @NO_SAFEPOINT_POLLS("numa profiler call chain must be atomic")
    @NEVER_INLINE
    public void count(boolean isArray, int size, int length) {
        if (isArray) {
            arrayCount = arrayCount + 1;
            totalArraySize = totalArraySize + size;
            totalArrayLength = totalArrayLength + length;
        } else {
            tupleCount = tupleCount + 1;
            totalTupleSize = totalTupleSize + size;
        }
    }

    public void print(int cycle, int b) {

        if (tupleCount > 0) {
            Log.print("(allocationsCounter);");
            Log.print(cycle);
            Log.print(';');
            Log.print(threadId);
            Log.print(';');
            Log.print("TUPLES");
            Log.print(';');

            Log.print(tupleCount);
            Log.print(';');
            // total tuple size in Mbytes
            Log.print((double) totalTupleSize * mbCoef);
            Log.print(';');
            // avg tuple size in bytes
            Log.println((double) totalTupleSize / tupleCount);
        }

        if (arrayCount > 0) {
            Log.print("(allocationsCounter);");
            Log.print(cycle);
            Log.print(';');
            Log.print(threadId);
            Log.print(';');
            Log.print("ARRAYS");
            Log.print(';');
            Log.print(arrayCount);
            Log.print(';');
            // total array size in Mbytes
            Log.print((double) totalArraySize * mbCoef);
            Log.print(';');
            // avg array size in bytes
            Log.print((double) totalArraySize / arrayCount);
            Log.print(';');
            // avg array length
            Log.println((double) totalArrayLength / arrayCount);
        }
    }

    public void resetCounter() {
        tupleCount = 0;
        totalTupleSize = 0;

        arrayCount = 0;
        totalArraySize = 0;
        totalArrayLength = 0;

        avgTupleSize = 0;
        avgArraySize = 0;
        avgArrayLength = 0;
    }

    @Override
    void deallocateAll() {

    }

    @INTRINSIC(UNSAFE_CAST)
    public static native AllocationsCounter asAllocCounter(Object object);

    @INLINE
    public static AllocationsCounter getForCurrentThread(Pointer etla) {
        final VmThreadLocal bufferPtr = VmThreadLocal.ALLOC_COUNTER_PTR;
        final Reference reference = bufferPtr.loadRef(etla);
        if (reference.isZero()) {
            return null;
        }
        final AllocationsCounter allocationsCounter = asAllocCounter(reference.toJava());
        return allocationsCounter;
    }

    @INLINE
    public static void setForCurrentThread(Pointer etla, AllocationsCounter counter) {
        VmThreadLocal.ALLOC_COUNTER_PTR.store(etla, Reference.fromJava(counter));
    }

    public static Reference getBufferReference(Pointer etla) {
        return VmThreadLocal.ALLOC_COUNTER_PTR.loadRef(etla);
    }

}