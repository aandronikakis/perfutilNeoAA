/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
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
package com.sun.max.util.perf;

import com.sun.max.annotate.*;
import com.sun.max.unsafe.Address;
import com.sun.max.unsafe.Pointer;
import com.sun.max.vm.Log;
import com.sun.max.vm.MaxineVM;
import com.sun.max.vm.thread.VmThread;
import com.sun.max.vm.thread.VmThreadMap;

import static com.sun.max.vm.thread.VmThread.getTid;

/**
 * A class that enables perf tool utilization into MaxineVM.
 * Perf can be used with numerous of perf events, individually or in groups.
 * Each perf event is modeled as a {@link PerfEvent} object instance.
 * A {@link PerfEvent} has a "type" {@link PERF_TYPE_ID} and a "config".
 * The currently available configs ({@link PERF_HW_EVENT_ID} and {@link PERF_HW_CACHE_EVENT_ID}) belong to
 * the {@link PERF_TYPE_ID#PERF_TYPE_HARDWARE} and {@link PERF_TYPE_ID#PERF_TYPE_HW_CACHE} types accordingly.
 * All {@link PerfEvent}s are accessed through the single {@link PerfUtil} instance held by the VM.
 *
 * To enable perf tool utilization in MaxineVM simply add the following code
 * at {@link com.sun.max.vm.run.java.JavaRunScheme#initialize(MaxineVM.Phase)} in case RUNNING:
 *
 *      if (PerfUtil.usePerf) {
 *          PerfUtil.initialize();
 *      }
 *
 *  TODO: document enable, disable, reset, read and close.
 *
 *  Make sure that the perf event you want to monitor is declared and initialized as a {@link PerfEvent}
 *  in this class (see
 * See perf.h for more detailed documentation.
 * https://docs.huihoo.com/doxygen/linux/kernel/3.7/include_2uapi_2linux_2perf__event_8h_source.html
 */

public class PerfUtil {

    @CONSTANT
    public static final boolean logPerf = false;

    /**
     *  Available PerfEvent types.
     *  To be passed in type field of the perf_event_attribute
     *  from perf.h
     */
    public enum PERF_TYPE_ID {
        PERF_TYPE_HARDWARE(0),
        PERF_TYPE_SOFTWARE(1),
        PERF_TYPE_TRACEPOINT(2),
        PERF_TYPE_HW_CACHE(3),
        PERF_TYPE_RAW(4),
        PERF_TYPE_BREAKPOINT(5);

        public final int value;

        PERF_TYPE_ID(int i) {
            value = i;
        }
    }

    /**
     *  Available PerfEvent configs.
     *  To be passed in type field of the perf_event_attribute
     *  from perf.h
     */

    /**
     * Perf event "configs" available for the {@link PERF_TYPE_ID#PERF_TYPE_HARDWARE} "type".
     * attr.config
     */
    public enum PERF_HW_EVENT_ID {
        PERF_COUNT_HW_CPU_CYCLES(0),
        PERF_COUNT_HW_INSTRUCTIONS(1),
        PERF_COUNT_HW_CACHE_REFERENCES(2),
        PERF_COUNT_HW_CACHE_MISSES(3),
        PERF_COUNT_HW_BRANCH_INSTRUCTIONS(4),
        PERF_COUNT_HW_BRANCH_MISSES(5),
        PERF_COUNT_HW_BUS_CYCLES(6),
        PERF_COUNT_HW_STALLED_CYCLES_FRONTEND(7),
        PERF_COUNT_HW_STALLED_CYCLES_BACKEND(8),
        PERF_COUNT_HW_REF_CPU_CYCLES(9);

        public final int value;

        PERF_HW_EVENT_ID(int i) {
            value = i;
        }
    }

    /**
     * Perf event "configs" available for the {@link PERF_TYPE_ID#PERF_TYPE_HW_CACHE} "type".
     * For {@link PERF_TYPE_ID#PERF_TYPE_HW_CACHE} events, the config is a bitmask.
     *      lowest 8 bits: a cache type from {@link PERF_HW_CACHE_TYPE_ID}
     *      bits 8 to 15: a cache operation from {@link PERF_HW_CACHE_OP_ID}
     *      bits 16 to 23: a cache result from {@link PERF_HW_CACHE_OP_RESULT_ID}
     *
     * Inspired by: https://code.woboq.org/qt5/qtbase/src/testlib/qbenchmarkperfevents.cpp.html
     */

    public enum PERF_HW_CACHE_EVENT_ID {
        CACHE_L1D_READ(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1D.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_L1D_WRITE(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1D.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_WRITE.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_L1D_PREFETCH(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1D.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_L1I_READ(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1I.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_L1I_PREFETCH(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1I.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_LLC_READ(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_LL.value  | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_LLC_WRITE(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_LL.value  | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_WRITE.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_LLC_PREFETCH(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_LL.value  | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_L1D_READ_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1D.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_L1D_WRITE_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1D.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_WRITE.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_L1D_PREFETCH_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1D.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_L1I_READ_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1I.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_L1I_PREFETCH_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1I.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_LLC_READ_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_LL.value  | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_LLC_WRITE_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_LL.value  | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_WRITE.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_LLC_PREFETCH_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_LL.value  | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_BRANCH_READ(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_BPU.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_BRANCH_READ_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_BPU.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16);

        public final int value;

        PERF_HW_CACHE_EVENT_ID(int i) {
            value = i;
        }
    }

    /**
     * The "cache type" building block of {@link PERF_TYPE_ID#PERF_TYPE_HW_CACHE} config.
     * !They are not configs by themselves!
     */
    public enum PERF_HW_CACHE_TYPE_ID {
        PERF_COUNT_HW_CACHE_L1D(0),
        PERF_COUNT_HW_CACHE_L1I(1),
        PERF_COUNT_HW_CACHE_LL(2),
        PERF_COUNT_HW_CACHE_DTLB(3),
        PERF_COUNT_HW_CACHE_ITLB(4),
        PERF_COUNT_HW_CACHE_BPU(5),
        PERF_COUNT_HW_CACHE_NODE(6);

        public final int value;

        PERF_HW_CACHE_TYPE_ID(int i) {
            value = i;
        }
    }

    /**
     * The "cache operation" building block of {@link PERF_TYPE_ID#PERF_TYPE_HW_CACHE} config.
     * !They are not configs by themselves!
     */
    public enum PERF_HW_CACHE_OP_ID {
        PERF_COUNT_HW_CACHE_OP_READ(0),
        PERF_COUNT_HW_CACHE_OP_WRITE(1),
        PERF_COUNT_HW_CACHE_OP_PREFETCH(2);

        public final int value;

        PERF_HW_CACHE_OP_ID(int i) {
            value = i;
        }
    }

    /**
     * The "cache result" building block of {@link PERF_TYPE_ID#PERF_TYPE_HW_CACHE} config.
     * !They are not configs by themselves!
     */
    public enum PERF_HW_CACHE_OP_RESULT_ID {
        PERF_COUNT_HW_CACHE_RESULT_ACCESS(0),
        PERF_COUNT_HW_CACHE_RESULT_MISS(1);

        public final int value;

        PERF_HW_CACHE_OP_RESULT_ID(int i) {
            value = i;
        }
    }

    /**
     * The unit of scheduling in perf is not an individual {@link PerfEvent}, but rather a {@link PerfEventGroup},
     * which may contain one or more {@link PerfEvent}s (potentially on different PMUs). The notion of
     * a {@link PerfEventGroup} is useful for ensuring that a set of mathematically related events are all
     * simultaneously measured for the same period of time.
     *
     * (Inspired by: https://hadibrais.wordpress.com/2019/09/06/the-linux-perf-event-scheduling-algorithm/)
     *
     */
    public enum MAXINE_PERF_EVENT_GROUP_ID {
        LLC_MISSES_GROUP(0),
        MISC_GROUP(1);

        public final int value;

        MAXINE_PERF_EVENT_GROUP_ID(int i) {
            value = i;
        }
    }

    /**
     * A list of the currently implemented {@link PerfEvent}s in MaxineVM.
     * For a new implementation:
     *  a) include a new {@link MAXINE_PERF_EVENT_ID} value,
     *  b) declare the new {@link PerfEvent} as a {@link PerfUtil} field in a new or existing {@link PerfEventGroup}
     *  c) initialize the {@link PerfEventGroup} if it is new.
     */
    public enum MAXINE_PERF_EVENT_ID {
        CACHE_MISSES(0),
        LLC_READ_MISSES(1),
        LLC_WRITE_MISSES(2),
        INSTRUCTIONS(3);

        public final int value;

        MAXINE_PERF_EVENT_ID(int i) {
            value = i;
        }
    }

    public static PerfEventGroup[] perfEventGroups;

    public static int numOfSupportedPerfEventGroups = MAXINE_PERF_EVENT_GROUP_ID.values().length;
    public static int numOfSupportedPerfEvents = MAXINE_PERF_EVENT_ID.values().length;
    public static int numOfUniquePerfGroups;
    public static int numOfUniquePerfEvents;

    /**
     * Max num of threads currently supported.
     * This number is arbitrary. Theoretically it can be any number.
     */
    final static int numOfThreads = 64;

    /**
     * The num of cores of the machine.
     */
    final static int numOfCores = Runtime.getRuntime().availableProcessors();

    public static boolean isInitialized = false;

    /**
     * This thread map holds the tid of each VmThread with {@link VmThread#id()} as index.
     * A VmThread's tid write in the map should be injected in VmThread's creation method.
     */
    public static int[] tidMap = new int[numOfThreads];

    public static int iteration = 0;

    /**
     * PerfUtil's modes.
     *
     * There are 3 available modes:
     * 1) Specific thread on specific core mode - (STSC)
     * 2) Specific thread on any core mode - (STAC)
     * 3) Any thread on specific core mode - (ATSC)
     *
     * Each mode refers to the scope of a {@link PerfEventGroup}, thus a mode-specific method is available for each action might be needed from a group.
     * The actions are classified to:
     * 1) Set - initialize the group. Then reset it and enable it. Availabe methods:
     *      {@link #perfGroupSetSpecificThreadSpecificCore(MAXINE_PERF_EVENT_GROUP_ID, int, int)}
     *      {@link #perfGroupSetSpecificThreadAnyCore(MAXINE_PERF_EVENT_GROUP_ID, int)}
     *      {@link #perfGroupSetAnyThreadSpecificCore(MAXINE_PERF_EVENT_GROUP_ID, int)}
     * 2) Read & Reset - disable the group, read the values, reset and re-enable
     * 3) Close -
     */

    public PerfUtil() {
    }

    public static void initialize() {
        if (logPerf) {
            Log.print("[PerfUtil constructor] PerfUtil initialization by thread ");
            Log.println(VmThread.current().id());
        }

        numOfUniquePerfGroups = PerfEventGroup.maxUniqueEventGroups(numOfCores, numOfThreads, numOfSupportedPerfEventGroups);
        numOfUniquePerfEvents = PerfEvent.maxUniquePerfEvents(numOfCores, numOfThreads, numOfSupportedPerfEvents);

        perfEventGroups = new PerfEventGroup[numOfUniquePerfGroups];
        perfUtilInit(numOfUniquePerfEvents);

        isInitialized = true;
    }

    public static void createGroup(MAXINE_PERF_EVENT_GROUP_ID group, int threadId, int tid, int core) {
        //MaxineVM.perfUtil.perfEventGroups[threadId][group.value] = new PerfEventGroup(group, threadId, core);
        int groupIndex = PerfEventGroup.uniqueGroupId(core, threadId, group.value);
        perfEventGroups[groupIndex] = new PerfEventGroup(group, threadId, tid, core);
    }

    /**
     * Calls the native {@link VmThread#getTid()} method which returns the tid of the calling thread.
     * Keeping and updating the tid map is essential for the whole PerfUtil functionality.
     * Despite the fact that inside the JVM we deal with threadIds, perf needs the actual tid of the
     * thread to be attached on.
     * NOTE: this method is effective only if is being called by the thread of which the tid is being updated.
     * @param threadId
     */
    public static void updateTidMap(int threadId) {
        tidMap[threadId] = getTid();
    }

    /**
     * In "specific thread on specific core" and "specific thread on any core" modes we target a specific thread.
     * Given that, we should take under consideration each thread's nature.
     * In MaxineVM all threads are instances of VmThread class but they are classified in two categories, the daemon
     * and non-daemon threads. Daemon threads are those dedicated to vm-internal tasks. Non-daemon are the application
     * threads. Daemon and non-daemon threads are initialized during different vm states and thus they should be handled accordingly.
     *
     * For more accurate results in any "specific thread" mode, it is suggested to set the perf group as soon as possible
     * after a thread's spawn. However, this is not really possible for daemon threads.
     *
     * So, for daemon threads:
     * It is suggested to inject a call of perfGroupSetSpecificThread* method at {@linkplain com.sun.max.vm.run.java.JavaRunScheme}
     * in case RUNNING. Alternatively, {@linkplain #setAllDaemonThreads(MAXINE_PERF_EVENT_GROUP_ID, int)} can be used to set all daemon threads.
     *
     * For non-daemon threads:
     * A Perf Event Group should have been set BEFORE thread's runnable execution, so it is suggested to inject a call
     * of perfGroupSetSpecificThread* in {@link VmThread#add(int, boolean, Address, Pointer, Pointer, Pointer, Pointer)}
     * exactly before the executeRunnable(thread) try/catch statement.
     *
     * To guard the injected call from daemon threads:
     *
     * if (MaxineVM.isRunning()) {
     *      final boolean lockDisabledSafepoints = Log.lock();
     *      perfGroupSetSpecificThread*();
     *      Log.unlock(lockDisabledSafepoints);
     *  }
     *
     * @param group the perf group of choice
     * @param threadId the threadId of the perf-ed thread
     * @param core the coreId of choice for "specific thread on specific core" mode. Hardcoded to -1 for "specific thread on any core"
     */
    public static void perfGroupSetSpecificThreadSpecificCore(MAXINE_PERF_EVENT_GROUP_ID group, int threadId, int core) {
        int tid = tidMap[threadId];
        PerfUtil.createGroup(group, threadId, tid, core);

        int groupIndex = PerfEventGroup.uniqueGroupId(core, threadId, group.value);
        PerfUtil.perfEventGroups[groupIndex].resetGroup();
        PerfUtil.perfEventGroups[groupIndex].enableGroup();
    }

    public static void perfGroupSetSpecificThreadAnyCore(MAXINE_PERF_EVENT_GROUP_ID group, int threadId) {
        int tid = tidMap[threadId];
        PerfUtil.createGroup(group, threadId, tid, -1);

        int groupIndex = PerfEventGroup.uniqueGroupId(-1, threadId, group.value);
        PerfUtil.perfEventGroups[groupIndex].resetGroup();
        PerfUtil.perfEventGroups[groupIndex].enableGroup();
    }

    public static void perfGroupSetAnyThreadSpecificCore(MAXINE_PERF_EVENT_GROUP_ID group, int core) {
        PerfUtil.createGroup(group, -1, -1, core);
        int groupIndex = PerfEventGroup.uniqueGroupId(core, -1, group.value);
        PerfUtil.perfEventGroups[groupIndex].resetGroup();
        PerfUtil.perfEventGroups[groupIndex].enableGroup();
    }

    /**
     * Is a wrapper method to set a perf Group in ATSC mode for all available cores.
     * @param group
     */
    public static void perfGroupSetAnyThreadAllCores(MAXINE_PERF_EVENT_GROUP_ID group) {
        for (int core = 0; core < numOfCores; core++) {
            perfGroupSetAnyThreadSpecificCore(group, core);
        }
    }

    /**
     * Call this method from JavaRunScheme phase RUNNING.
     * @param group
     * @param core
     */
    public static void setAllDaemonThreads(MAXINE_PERF_EVENT_GROUP_ID group, int core) {
        for (int threadId = 1; threadId <= 5; threadId++) {
            if (logPerf) {
                Log.print("Set daemon thread ");
                Log.println(threadId);
            }
            perfGroupSetSpecificThreadSpecificCore(group, threadId, core);
        }
    }

    /**
     * To be used for "specific thread and specific core" or "specific thread on any core" modes.
     * It should be injected AFTER a thread's runnable execution.
     *
     * Proposed point of injection: in {@link VmThread#add(int, boolean, Address, Pointer, Pointer, Pointer, Pointer)}
     * exactly after the executeRunnable(thread) try/catch statement.
     *
     * Same parameters as {@link #nonDaemonThreadPerfReadAndReset(MAXINE_PERF_EVENT_GROUP_ID, int, int)}
     */
    public static void nonDaemonThreadPerfReadAndReset(MAXINE_PERF_EVENT_GROUP_ID group, int threadId, int core) {
        if (!VmThread.current().javaThread().isDaemon()) {
            // with this check we apply only on application (non-daemon) threads
            final boolean lock = Log.lock();
            int groupIndex = PerfEventGroup.uniqueGroupId(core, threadId, group.value);
            PerfUtil.perfEventGroups[groupIndex].disableGroup();
            PerfUtil.perfEventGroups[groupIndex].readGroup();
            PerfUtil.perfEventGroups[groupIndex].printGroup();
            Log.unlock(lock);
        }
    }

    public static void daemonThreadPerfSet(MAXINE_PERF_EVENT_GROUP_ID group, int threadId, int core) {
        int tid = PerfUtil.tidMap[threadId];
        PerfUtil.createGroup(group, threadId, tid, core);

        int groupIndex = PerfEventGroup.uniqueGroupId(core, threadId, group.value);
        PerfUtil.perfEventGroups[groupIndex].resetGroup();
        PerfUtil.perfEventGroups[groupIndex].enableGroup();
    }

    /**
     * Call this method from JavaRunScheme phase RUNNING.
     * @param group
     * @param core
     */
    public static void setAllDaemonThreads(MAXINE_PERF_EVENT_GROUP_ID group, int core) {
        for (int threadId = 1; threadId <= 5; threadId++) {
            Log.print("Set daemon thread ");
            Log.println(threadId);
            daemonThreadPerfSet(group, threadId, core);
        }
    }

    /**
     * place at.
     * JDK_java_lang_Runtime.gc()
     * MaxineVm.exit()
     */
    public static void groupReadAndResetForAllDaemonThreads() {
        int liveThreadCount = VmThreadMap.getLiveTheadCount();

        for (int threadId = 1; threadId <= 5; threadId++) {
            /*if (threadId == 6) {
                continue;
            }*/
            int tid = tidMap[threadId];
            int groupIndex = PerfEventGroup.uniqueGroupId(-1, threadId, PerfUtil.MAXINE_PERF_EVENT_GROUP_ID.LLC_MISSES_GROUP.value);
            perfEventGroups[groupIndex].disableGroup();
            perfEventGroups[groupIndex].readGroup();
            perfEventGroups[groupIndex].printGroup();

            perfEventGroups[groupIndex].resetGroup();
            perfEventGroups[groupIndex].enableGroup();
        }
    }

    @C_FUNCTION
    public static native Pointer perfUtilInit(int numOfEvents);

}
