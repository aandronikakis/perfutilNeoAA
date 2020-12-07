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

import com.sun.max.memory.VirtualMemory;
import com.sun.max.unsafe.Pointer;
import com.sun.max.unsafe.Size;
import com.sun.max.unsafe.Word;
import com.sun.max.vm.Log;
import com.sun.max.vm.reference.Reference;
import com.sun.max.vm.thread.VmThread;

public class RecordBufferQueue {

    public Pointer queue;
    public int index;
    public int length;
    public int size;

    final static int sizeOfReference = Word.size();

    public RecordBufferQueue() {
        length = 200;
        size = length * sizeOfReference;
        queue = VirtualMemory.allocate(Size.fromInt(size).times(sizeOfReference), VirtualMemory.Type.DATA);
        for (int i = 0; i < size; i += sizeOfReference) {
            queue.setReference(i, Reference.zero());
        }
        index = 0;
    }

    public void add(Pointer tla, Reference buffer) {
        Log.print("[RecordBufferQueue] add(): Thread ");
        Log.print(VmThread.fromTLA(tla).id());
        Log.print(" Reference: ");
        Log.println(buffer);
        queue.setReference(index, buffer);
        index++;
    }

    public void print(int profilingCycle) {
        Log.print("[RecordBufferQueue] print(): index = ");
        Log.println(index);

        for (int i = 0; i < index; i++) {
            Log.println(queue.getReference(i));
            RecordBuffer.asRecordBuffer(queue.getReference(i).toJava()).print(profilingCycle, 1);
        }
    }
}
