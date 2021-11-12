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

import com.sun.max.unsafe.Address;
import com.sun.max.unsafe.Pointer;
import com.sun.max.vm.Log;
import com.sun.max.vm.monitor.modal.modehandlers.lightweight.LightweightLockword;
import com.sun.max.vm.runtime.FatalError;
import com.sun.max.vm.thread.VmThread;
import com.sun.max.vm.thread.VmThreadLocal;

import static com.sun.max.vm.profilers.tracing.numa.NUMAProfiler.getNUMAProfilerVerbose;

/**
 * {@link com.sun.max.vm.MaxineVM} assigns its Java {@link com.sun.max.vm.thread.VmThread}s to native pthreads.
 * When a Java thread starts/stops more than once, it might be re-assigned to different pthreads.
 * Consequently, there is no way to uniquely identify a Java thread by its {@link VmThread#id()}
 * because this value refers to the native pthread id.
 *
 * {@code ThreadNameInventory} is a workaround in uniquely identifying a Java Application Thread to support
 * object access profiling per thread in the context of {@link NUMAProfiler}.
 *
 * {@code ThreadNameInventory} stores a set of metadata for each profiled thread instance including thread name, thread class name and tid.
 * They are stored in 3 parallel arrays of size {@code size}.
 * The {@code index} as the arrays' index is the id that uniquely maps to a profiled thread instance.
 * The {@code index} is stored in the AllocatorId bit-field of each allocated object's misc word (see {@link LightweightLockword}).
 *
 * Accesses in objects that had been allocated before profiling being enabled (and consequently NUMAProfiler is not aware of the allocator thread)
 * are highlighted as "Unknown" (see {@link ThreadInventory#inventory}[0]).
 *
 * See also:    {@link com.sun.max.vm.monitor.modal.modehandlers.lightweight.LightweightLockword}
 *              {@link VmThreadLocal#THREAD_NAME_KEY}
 */
public class ThreadInventory {

    private static int index;

    private static String[] threadName;
    private static String[] threadType;
    private static int[] osTid;

    private static boolean verbose;

    /**
     * {@code size} is equal to 2 ^ {@link LightweightLockword#ALLOCATORID_FIELD_WIDTH}.
     */
    private static final int size = 1 << LightweightLockword.ALLOCATORID_FIELD_WIDTH;

    public ThreadInventory() {
        if (getNUMAProfilerVerbose()) {
            Log.print("[VerboseMsg @ ThreadNameInventory.ThreadNameInventory()]: Initialize ThreadName Inventory with size ");
            Log.println(size);
        }
        threadName = new String[size];
        threadType = new String[size];
        osTid = new int[size];

        threadName[0] = "Unknown";
        threadType[0] = "Unknown";
        osTid[0] = 0;

        index = 1;

        verbose = true;
    }

    /**
     * Backwards search to find the last entry.
     * @param value
     * @return
     */
    public int inventoryContainsThread(int value) {
        for (int i = index - 1; i > 0; i--) {
            if (osTid[i] == value) {
                return i;
            }
        }
        return 0;
    }

    public static int getIndex() {
        return index;
    }

    public int addToInventory(Pointer etla, boolean isLive) {
        if (index < size) {

            VmThread thread = VmThread.fromTLA(etla);
            String name = thread.getName();
            String type = thread.javaThread().getClass().getName();
            int tid = thread.tid;

            int key = inventoryContainsThread(tid);
            if (key == 0) {
                // does not exist, write THREAD_NAME_KEY where current index points, add inventory, return index to update threadNameKey in ProfilingArtifact
                VmThreadLocal.THREAD_NAME_KEY.store(etla, Address.fromInt(index));
                threadName[index] = name;
                threadType[index] = type;
                osTid[index] = VmThread.fromTLA(etla).tid;
                if (isLive) {
                    Log.print("[Add to Inventory]: Live Thread: ");
                } else {
                    Log.print("[Add to Inventory]: New Thread: ");
                }
                Log.print(name);
                Log.print(" | ");
                Log.print(type);
                Log.print(" | ");
                Log.println(tid);
                index++;
            } else {
                // already exists, write THREAD_NAME_KEY and return key to update threadNameKey in ProfilingArtifact
                VmThreadLocal.THREAD_NAME_KEY.store(etla, Address.fromInt(key));
                Log.println("=== Thread with same tid exists !!!! ===");
                return key;
            }
        } else {
            // Profiling supports up to 256 threads because allocator thread id need to be stored in object's misc word.
            // The available bits for allocator thread id are only 8.
            FatalError.unexpected("Profiling threads exceed the 1 << 8 limit.");
        }
        return index - 1;
    }

    public static String getName(int index) {
        return threadName[index];
    }
    public static String getType(int index) {
        return threadType[index];
    }
    public static int getTID(int index) {
        return osTid[index];
    }
}
