/*
 * Copyright (c) 2022, APT Group, Department of Computer Science,
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
package com.sun.max.vm.numa;

import com.sun.max.unsafe.Pointer;
import com.sun.max.util.perf.PerfEventGroup;
import com.sun.max.util.perf.PerfUtil;
import com.sun.max.vm.Log;
import com.sun.max.vm.thread.VmThread;
import com.sun.max.vm.thread.VmThreadMap;

import static com.sun.max.util.perf.PerfUtil.MAXINE_PERF_EVENT_GROUP_ID.*;
import static com.sun.max.vm.MaxineVM.NUMALog;
import static com.sun.max.vm.thread.VmThreadLocal.ETLA;

public class HWCountersHandler {

    /**
     * Which threads to profile.
     */
    public static final Pointer.Predicate profilingPredicate = new Pointer.Predicate() {
        @Override
        public boolean evaluate(Pointer tla) {
            VmThread vmThread = VmThread.fromTLA(tla);
            return vmThread.javaThread() != null &&
                    !vmThread.isVmOperationThread() &&
                    !vmThread.getName().equals("Signal Dispatcher") &&
                    !vmThread.getName().equals("Reference Handler") &&
                    !vmThread.getName().equals("Finalizer") &&
                    !vmThread.getName().equals("NUMA-Awareness-Thread");
        }
    };

    /**
     * Enable a HW counter for a thread.
     * @param thread the target thread.
     * @param eventGroup the HW counter to be enabled.
     */
    public static void enableHWCounter(VmThread thread, PerfUtil.MAXINE_PERF_EVENT_GROUP_ID eventGroup) {
        final int id = thread.id();
        final int tid = thread.tid();
        final String name = thread.getName();
        //Log.println("Enable HW Counters for: " + id + " " + tid + " " + name);
        PerfUtil.perfGroupSetSpecificThreadSpecificCore(eventGroup, id, tid, name, -1);
    }

    /**
     * Enable HW counters for main thread.
     */
    public static void enableHWCountersMainThread() {
        final VmThread mainThread = VmThread.mainThread;
        enableHWCounter(mainThread, CPU_CYCLES_SINGLE);
        enableHWCounter(mainThread, INSTRUCTIONS_SINGLE);
    }

    /**
     * Enable HW counters for an application thread.
     * @param thread the application thread.
     */
    public static void enableHWCountersApplicationThread(VmThread thread) {
        if (profilingPredicate.evaluate(thread.tla())) {
            //final boolean lockDisabledSafepoints = Log.lock();
            enableHWCounter(thread, CPU_CYCLES_SINGLE);
            enableHWCounter(thread, INSTRUCTIONS_SINGLE);
            //Log.unlock(lockDisabledSafepoints);
        }
    }

    /**
     * Read a HW counter for a thread.
     * @param thread the target thread.
     * @param eventGroup the HW counter to be read.
     */
    private static long readCounter(VmThread thread, PerfUtil.MAXINE_PERF_EVENT_GROUP_ID eventGroup) {
        int groupIndex = PerfEventGroup.uniqueGroupId(-1, thread.id(), eventGroup.value);
        final PerfEventGroup group = PerfUtil.perfEventGroups[groupIndex];
        if (group == null) {
            // thread has already closed
            return -1;
        }
        group.readGroup();
        group.resetGroup();
        // scale value
        float eventCount = group.perfEvents[0].value;
        float time = group.timeRunningPercentage;
        long value = (long)(eventCount * ((long) time / 100));
        // store to ProfilingData
        return value;
        //ProfilingData.add(thread, eventGroup, value);
    }

    /**
     * A procedure to read all the HW counters of a thread.
     */
    private static final Pointer.Procedure readHWCounters = new Pointer.Procedure() {
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            final VmThread thread = VmThread.fromTLA(etla);
            if (NUMALog) {
                Log.println("Read HW Counters for: " + thread.id() + " " + thread.tid() + " " + thread.getName());
            }
            long instructions = readCounter(thread, INSTRUCTIONS_SINGLE);
            long cpuCycles = readCounter(thread, CPU_CYCLES_SINGLE);
            ProfilingData.add(thread, instructions, cpuCycles);
        }
    };

    /**
     * Read all HW counters of all profiled threads.
     */
    public static void readHWCounters() {
        synchronized (VmThreadMap.THREAD_LOCK) {
            VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, readHWCounters);
            Log.println("Profile: (" + ProfilingData.data.size() + " threads)");
        }
    }

}
