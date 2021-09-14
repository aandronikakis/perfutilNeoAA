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

import com.sun.max.memory.VirtualMemory;
import com.sun.max.unsafe.Pointer;
import com.sun.max.unsafe.Size;
import com.sun.max.unsafe.Word;
import com.sun.max.vm.Log;
import com.sun.max.vm.reference.Reference;
import com.sun.max.vm.thread.VmThread;

/**
 * A LIFO queue to store the {@link Reference}s of either {@link RecordBuffer}s or {@link AllocationsCounter}s of the threads that have already been terminated.
 * It uses an off-heap {@link Pointer} array to store the {@link Reference}s.
 * It works as a LIFO queue.
 */

public class ProfilingArtifactsQueue {

    public Pointer queue;
    public int index;
    public int length; // The maximum item capacity of the queue (arbitrary).

    final static int sizeOfReference = Word.size();
    final static int expandStep = 100; // arbitrary

    public ProfilingArtifactsQueue() {
        length = 200;
        queue = VirtualMemory.allocate(Size.fromInt(length).times(sizeOfReference), VirtualMemory.Type.DATA);
        for (int i = 0; i < length; i++) {
            queue.setReference(i, Reference.zero());
        }
        index = 0;
    }

    public boolean isEmpty() {
        if (index == 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * The method to insert an element in the end of the queue.
     */
    public void add(Pointer tla, Reference buffer) {
        if (NUMAProfiler.getNUMAProfilerVerbose()) {
            Log.print("[VerboseMsg @ ProfilingArtifactsQueue.add()]: Thread ");
            Log.print(VmThread.fromTLA(tla).id());
            Log.print(" Reference: ");
            Log.println(buffer);
        }
        if (index >= length) {
            expand();
        }
        queue.setReference(index, buffer);
        index++;
    }

    /**
     * The method to remove from the queue.
     */
    public ProfilingArtifact remove() {
        index--;
        return ProfilingArtifact.asArtifact(queue.getReference(index).toJava());
    }

    /**
     * Walk the queue in a LIFO manner, print and de-allocate each removed buffer.
     */
    public void print(int profilingCycle) {
        while (!isEmpty()) {
            ProfilingArtifact artifact = remove();
            if (NUMAProfiler.getNUMAProfilerVerbose()) {
                Log.print("[VerboseMsg @ ProfilingArtifactsQueue.print()]: ");
                Log.print(artifact.getSimpleName());
                Log.print(" of Thread ");
                Log.print(artifact.getThreadId());
                Log.println(" is printing from Queue.");
            }
            artifact.print(profilingCycle, 1);
            artifact.deallocateArtifact();
        }
    }

    /**
     * Dynamic self-expand of the queue.
     */
    public void expand() {
        int newLength = length + expandStep;
        Pointer newQueue = VirtualMemory.allocate(Size.fromInt(newLength).times(sizeOfReference), VirtualMemory.Type.DATA);
        // copy old
        for (int i = 0; i < length; i++) {
            newQueue.setReference(i, queue.getReference(i));
        }
        // set new
        for (int i = length; i < newLength; i++) {
            newQueue.setReference(i, Reference.zero());
        }
        // swap old queue with new
        Pointer oldQueue = queue;
        queue = newQueue;
        // de-allocate old
        VirtualMemory.deallocate(oldQueue.asAddress(), Size.fromInt(length).times(sizeOfReference), VirtualMemory.Type.DATA);
        length = newLength;
    }
}
