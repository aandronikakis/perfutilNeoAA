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

import com.sun.max.lang.ISA;
import com.sun.max.platform.Platform;
import com.sun.max.unsafe.Address;
import com.sun.max.unsafe.Pointer;
import com.sun.max.vm.Intrinsics;
import com.sun.max.vm.Log;
import com.sun.max.vm.monitor.modal.modehandlers.lightweight.LightweightLockword;
import com.sun.max.vm.runtime.FatalError;
import com.sun.max.vm.thread.VmThread;
import com.sun.max.vm.thread.VmThreadLocal;

import static com.sun.max.vm.profilers.tracing.numa.NUMAProfiler.getNUMAProfilerPrintOutput;
import static com.sun.max.vm.profilers.tracing.numa.NUMAProfiler.getNUMAProfilerVerbose;

/**
 * Thread instance unique identification is critical in the context of {@link NUMAProfiler}.
 * {@link VmThread#id} and/or {@link VmThread#name} can potentially be reused by different {@link VmThread} instances.
 * Consequently, they are insufficient in uniquely identifying an application thread.
 *
 * {@code ThreadNameInventory} is a workaround in uniquely identifying a Java Application Thread to support
 * object access profiling per thread in the context of {@link NUMAProfiler}.
 * It essentially maps each {@link VmThread} instance with a unique "key" which is kept in {@link VmThreadLocal#THREAD_NAME_KEY}.
 * It is also used as the {@link LightweightLockword} AllocId value in each object's misc word
 * to indicate the {@link VmThread} instance that allocated the object.
 *
 * {@code ThreadNameInventory} stores a set of metadata for each profiled {@link VmThread} instance including: thread name, thread class name, tid and live status.
 * They are stored in 4 parallel arrays of size {@code size}.
 * Their index is the "key".
 *
 * NUMAProfiler can't be aware of the allocator thread for objects allocated before profiling being enabled.
 * Accesses to those objects are highlighted as "Unknown" and reserve the position 0 of the arrays.
 *
 * See also:    {@link LightweightLockword}
 *              {@link VmThreadLocal#THREAD_NAME_KEY}
 */
public class ThreadInventory {

    // num of elements in the inventory
    private static int elements;

    /**
     * The parallel arrays that consist the inventory.
     */
    private static String[] threadName;
    private static String[] threadType;
    private static int[] osTid;
    private static boolean[] isLive;

    private static boolean verbose;

    // Pre-allocated String objs to be used when allocations are disabled
    private static String fatalErrorMessage;
    private static String startStr;
    private static String endStr;

    /**
     * {@code size} is equal to 2 ^ {@link LightweightLockword#ALLOCATORID_FIELD_WIDTH}.
     */
    private static final int size = 1 << LightweightLockword.ALLOCATORID_FIELD_WIDTH;

    public ThreadInventory() {
        verbose = getNUMAProfilerVerbose();
        if (verbose) {
            Log.print("[VerboseMsg @ ThreadNameInventory.ThreadNameInventory()]: Initialize ThreadName Inventory with size ");
            Log.println(size);
        }
        threadName = new String[size];
        threadType = new String[size];
        osTid = new int[size];
        isLive = new boolean[size];

        // reserve position 0 for Unknown (objects allocated prior to profiling)
        threadName[0] = "Unknown";
        threadType[0] = "Unknown";
        osTid[0] = 0;
        isLive[0] = true;

        elements = 1;

        // Prepare String objs in case they are needed when allocations are disabled
        fatalErrorMessage = "NUMAProfiler does not support profiling of more than " + ((1 << LightweightLockword.THREADID_FIELD_WIDTH) - 1) + " threads simultaneously (check \"recursive-threads\" BootImageGenerator option).";
        startStr = "start";
        endStr = "end";
    }

    /**
     * Find the next available key for a new tid.
     */
    public int nextAvailableKey(int value) {
        int next = -1;
        for (int i = 1; i < size; i++) {
            if (osTid[i] == value) {
                next = i;
                break;
            }
        }
        if (next == -1) {
            // does not exist -> return 1st not live
            for (int i = 1; i < size; i++) {
                if (!isLive[i]) {
                    next = i;
                    break;
                }
            }
        }
        return next;
    }

    public static int getIndex() {
        return elements;
    }

    public int add(Pointer tla) {
        int key;

        // guard
        FatalError.check(elements < size, fatalErrorMessage);

        // gather elements to store in the inventory
        VmThread thread = VmThread.fromTLA(tla);
        String name = thread.getName();
        String type = thread.javaThread().getClass().getName();
        int tid = thread.tid();

        key = nextAvailableKey(tid);
        // write THREAD_NAME_KEY where key points, add to inventory, return key to update threadNameKey in ProfilingArtifact
        VmThreadLocal.THREAD_INVENTORY_KEY.store3(tla, Address.fromInt(key));
        threadName[key] = name;
        threadType[key] = type;
        osTid[key] = VmThread.fromTLA(tla).tid();
        isLive[key] = true;

        elements++;

        if (getNUMAProfilerPrintOutput()) {
            logThread(key, true);
        }

        return key;
    }

    /**
     * Set {@code isLive} to false during a GC for all thread instances.
     * In the next mutation phase the yet live threads will be re-entered in the inventory.
     */
    public void update() {
        if (verbose) {
            Log.println("[VerboseMsg @ ThreadInventory.update()]: Set status to \"dead\" for all");
        }
        for (int i = 1; i < size; i++) {
            setStatus(i, false);
        }
        elements = 1;
    }

    /**
     * Log Thread Instance.
     * format: (threadInventory);start/end;name;type;tid;key;timestamp
     */
    public void logThread(int key, boolean start) {
        if (Platform.platform().isa == ISA.AMD64) {
            final String phase = start ? startStr : endStr;
            Log.print("(threadInventory)");
            Log.print(";");
            Log.print(NUMAProfiler.profilingCycle);
            Log.print(";");
            Log.print(phase);
            Log.print(";");
            Log.print(getName(key));
            Log.print(";");
            Log.print(getType(key));
            Log.print(";");
            Log.print(getTID(key));
            Log.print(";");
            Log.print(key);
            Log.print(";");
            Log.println(Intrinsics.getTicks());
        }
    }

    /**
     * Print Thread Inventory.
     */
    public void print() {
        for (int key = 1; key < size; key++) {
            if (isLive[key]) {
                Log.print(key);
                Log.print(";");
                Log.print(getName(key));
                Log.print(";");
                Log.print(getType(key));
                Log.print(";");
                Log.println(getTID(key));
            }
        }
    }

    /**
     * Get all attributes of a thread instance in the inventory only by key.
     * @param key should correspond to a {@link VmThreadLocal#THREAD_NAME_KEY} value.
     */
    public static String getName(int key) {
        return threadName[key];
    }
    public static String getType(int key) {
        return threadType[key];
    }
    public static int getTID(int key) {
        return osTid[key];
    }

    public static void setStatus(int key, boolean value) {
        isLive[key] = value;
    }
}
