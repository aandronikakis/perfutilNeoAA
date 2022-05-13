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

import static com.sun.max.util.perf.PerfUtil.MAXINE_PERF_EVENT_GROUP_ID.INSTRUCTIONS_SINGLE;
import static com.sun.max.vm.thread.VmThreadLocal.ETLA;

public class HWCountersHandler {

    private static final Pointer.Predicate profilingPredicate = new Pointer.Predicate() {
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

    private static final Pointer.Procedure setHWCounters = new Pointer.Procedure() {
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            final VmThread thread = VmThread.fromTLA(etla);
            ////Log.println("Enable HW Counters for: " + thread.id() + " " + thread.tid() + " " + thread.getName());
            PerfUtil.perfGroupSetSpecificThreadSpecificCore(INSTRUCTIONS_SINGLE, thread.id(), thread.tid(), thread.getName(), -1);
        }
    };

    private static final Pointer.Procedure readNDisableHWCounters = new Pointer.Procedure() {
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            final VmThread thread = VmThread.fromTLA(etla);
            ////Log.println("Read N' Disable HW Counters for: " + thread.id() + " " + thread.tid() + " " + thread.getName());
            //PerfUtil.perfGroupSetSpecificThreadSpecificCore(INSTRUCTIONS_SINGLE, thread.id(), thread.tid(), thread.getName(), -1);
            //PerfUtil.perfGroupReadAndResetSpecificThreadSpecificCore(INSTRUCTIONS_SINGLE, thread.id(), -1);
            int groupIndex = PerfEventGroup.uniqueGroupId(-1, thread.id(), INSTRUCTIONS_SINGLE.value);
            final PerfEventGroup group = PerfUtil.perfEventGroups[groupIndex];
            group.disableGroup();
            group.readGroup();
            group.resetGroup();
            long eventCount = group.perfEvents[0].value;
            long time = group.timeRunningPercentage;
            long value = eventCount * (time / 100);
            ///Log.println("Instructions = " + eventCount + " of " + time + "%. 100% is " + value);

        }
    };

    public static void enableHWCounters() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, setHWCounters);
    }

    public static void readNDisableHWCounters() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, readNDisableHWCounters);
    }

    public static void measureHWInstructions() {
        try {
            enableHWCounters();
            Thread.sleep(100);
            readNDisableHWCounters();
        } catch (InterruptedException ex) {

        }
    }
}
