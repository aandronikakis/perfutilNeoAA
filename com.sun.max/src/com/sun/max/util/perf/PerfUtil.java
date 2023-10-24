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
import com.sun.max.vm.profilers.tracing.numa.*;
import com.sun.max.platform.OS;
import com.sun.max.platform.Platform;
import com.sun.max.unsafe.Address;
import com.sun.max.unsafe.Pointer;
import com.sun.max.vm.Log;
import com.sun.max.vm.runtime.FatalError;
import com.sun.max.vm.thread.VmThread;
import com.sun.max.vm.thread.VmThreadMap;

import static com.sun.max.vm.thread.VmThread.getTid;

import static com.sun.max.vm.VMOptions.*;
import static com.sun.max.vm.MaxineVM.*;
import com.sun.max.vm.*;

/**
 * A class that enables perf tool utilization into MaxineVM.
 *
 * Perf can be used with numerous of perf events, individually or in groups. Each perf event is
 * modeled as a {@link PerfEvent} object instance. Each perf event group is modeled as a
 * {@link PerfEventGroup} object instance.
 * A {@link PerfEvent} has a "type" {@link PERF_TYPE_ID} and a "config". The currently available
 * configs ({@link PERF_HW_EVENT_ID} and {@link PERF_HW_CACHE_EVENT_ID}) belong to the
 * {@link PERF_TYPE_ID#PERF_TYPE_HARDWARE} and {@link PERF_TYPE_ID#PERF_TYPE_HW_CACHE} types
 * accordingly.
 * TODO: extend perf events (more hw and cache events as well as sw events).
 * TODO: can we support that the current implementation enables every possible perf event utilization?
 *
 * Event Granularity:
 * Typically, each {@link PerfEvent} should be part of a {@link PerfEventGroup}.
 * This approach ensures that a set of mathematically related events are all simultaneously set,
 * reset, enabled, disabled and read so that measurements cover the same period of time. However,
 * the current implementation is not restrictive by providing the freedom of choice over the
 * granularity, with very few changes. Groups with one single {@link PerfEvent} are also allowed.
 * All {@link PerfEventGroup}s are accessed through the {@link #perfEventGroups} array held by the
 * {@link PerfUtil} class.
 *
 * Enable PerfUtil:
 * To enable perf tool utilization in MaxineVM simply add the following code at
 * {@link com.sun.max.vm.run.java.JavaRunScheme#initialize(MaxineVM.Phase)} in case RUNNING:
 *
 *  if (PerfUtil.usePerf) {
 *      PerfUtil.initialize();
 *  }
 *
 * PerfUtil's modes:
 * There are 3 available modes:
 *  1) Specific thread on specific core mode - (STSC)
 *  2) Specific thread on any core mode - (STAC)
 *  3) Any thread on specific core mode - (ATSC)
 * Each mode refers to the scope of a {@link PerfEventGroup}, thus a mode-specific method is available
 * for each *Action* might be needed by a group. The Actions define the PerfUtil's API!
 * The actions are classified to:
 *  1) Set - initialize the group. Then reset it and enable it. Available methods:
 *      {@link #perfGroupSetSpecificThreadSpecificCore}
 *      {@link #perfGroupSetSpecificThreadAnyCore}
 *      {@link #perfGroupSetAnyThreadSpecificCore}
 *      {@link #perfGroupSetAnyThreadAllCores}
 *  2) Read & Reset - disable the group, read the values, reset and re-enable. Available methods:
 *      {@link #perfGroupReadAndResetSpecificThreadSpecificCore}
 *      {@link #perfGroupReadAndResetSpecificThreadAnyCore}
 *      {@link #perfGroupReadAndResetAnyThreadSpecificCore}
 *      {@link #perfGroupReadAndResetAnyThreadAllCores}
 *  3) Close - close the group. Available methods:
 *      {@link #perfGroupClose}
 *
 * Implement a new {@link PerfEvent}:
 * Make sure that the perf event you want to monitor has its {@link PERF_TYPE_ID} and config defined
 * under the proper enum in this class. Also define a unique Maxine-internal {@link MAXINE_PERF_EVENT_GROUP_ID}.
 * Then include the event into one of the already existing {@link MAXINE_PERF_EVENT_GROUP_ID}s.
 * In case it cannot be co-located with any of the already defined groups, define a new one.
 *
 * TODO: include (where?) use-case examples
 *
 * To define a new perf event or for more detailed documentation, see perf.h:
 * https://docs.huihoo.com/doxygen/linux/kernel/3.7/include_2uapi_2linux_2perf__event_8h_source.html
 */

public class PerfUtil {

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
        PERF_TYPE_BREAKPOINT(5),

        PERF_TYPE_UNCORE_IMC_0(8),
        PERF_TYPE_UNCORE_IMC_1(9),
        PERF_TYPE_UNCORE_IMC_2(10),
        PERF_TYPE_UNCORE_IMC_3(11);

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
     * Perf event "configs" available for the {@link PERF_TYPE_ID#PERF_TYPE_SOFTWARE} "type".
     * attr.config
     */
    public enum PERF_SW_EVENT_ID {
        PERF_COUNT_SW_CPU_CLOCK(0),
        PERF_COUNT_SW_TASK_CLOCK(1),
        PERF_COUNT_SW_PAGE_FAULTS(2),
        PERF_COUNT_SW_CONTEXT_SWITCHES(3),
        PERF_COUNT_SW_CPU_MIGRATIONS(4),
        PERF_COUNT_SW_PAGE_FAULTS_MIN(5),
        PERF_COUNT_SW_PAGE_FAULTS_MAJ(6),
        PERF_COUNT_SW_ALIGNMENT_FAULTS(7),
        PERF_COUNT_SW_EMULATION_FAULTS(8),
        PERF_COUNT_SW_DUMMY(9),
        PERF_COUNT_SW_BPF_OUTPUT(10);

        public final int value;

        PERF_SW_EVENT_ID(int i) {
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
        CACHE_DTLB_READ(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_DTLB.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_DTLB_WRITE(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_DTLB.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_WRITE.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_L1D_READ(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1D.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_L1D_WRITE(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1D.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_WRITE.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_L1D_PREFETCH(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1D.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_L1I_READ(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1I.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_L1I_PREFETCH(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1I.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_LLC_READ(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_LL.value  | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_LLC_WRITE(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_LL.value  | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_WRITE.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_LLC_PREFETCH(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_LL.value  | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_NODE_READ(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_NODE.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_NODE_WRITE(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_NODE.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_WRITE.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_NODE_PREFETCH(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_NODE.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_ACCESS.value << 16),
        CACHE_DTLB_READ_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_DTLB.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_DTLB_WRITE_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_DTLB.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_WRITE.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_L1D_READ_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1D.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_L1D_WRITE_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1D.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_WRITE.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_L1D_PREFETCH_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1D.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_L1I_READ_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1I.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_L1I_PREFETCH_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_L1I.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_LLC_READ_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_LL.value  | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_LLC_WRITE_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_LL.value  | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_WRITE.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_LLC_PREFETCH_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_LL.value  | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_NODE_READ_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_NODE.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_READ.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_NODE_WRITE_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_NODE.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_WRITE.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
        CACHE_NODE_PREFETCH_MISS(PERF_HW_CACHE_TYPE_ID.PERF_COUNT_HW_CACHE_NODE.value | PERF_HW_CACHE_OP_ID.PERF_COUNT_HW_CACHE_OP_PREFETCH.value << 8 | PERF_HW_CACHE_OP_RESULT_ID.PERF_COUNT_HW_CACHE_RESULT_MISS.value << 16),
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

    public enum PERF_UNCORE_IMC_EVENT_ID{
        MEM_READ(0x304),        //0x304
        MEM_WRITE(0xc04);       //0xc04

        public final int value;

        PERF_UNCORE_IMC_EVENT_ID(int i) {
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
        SW_GROUP(0),
        HW_GROUP(1),
        LLC_READS_GROUP(2),
        LLC_WRITES_GROUP(3),
        NODE_READS_GROUP(4),
        NODE_WRITES_GROUP(5),
        CACHE_ACCESSES_GROUP(6),
        CACHE_MISSES_GROUP(7),
        NODE_MISSES_GROUP(8),
        // All the Uncore Groups are Intel Xeon E5-2690, dual socket machine specific.
        // They may work in other microarchitectures as well but they are not tested.
        UNCORE_IMC_0_CPU_0_GROUP(9),
        UNCORE_IMC_1_CPU_0_GROUP(10),
        UNCORE_IMC_2_CPU_0_GROUP(11),
        UNCORE_IMC_3_CPU_0_GROUP(12),
        UNCORE_IMC_0_CPU_1_GROUP(13),
        UNCORE_IMC_1_CPU_1_GROUP(14),
        UNCORE_IMC_2_CPU_1_GROUP(15),
        UNCORE_IMC_3_CPU_1_GROUP(16),

        /**
         * Every event is supported for single utilization (not as part of a group).
         * This is achieved by the following single-event groups:
         */

        CPU_CYCLES_SINGLE(17),
        INSTRUCTIONS_SINGLE(18),
        BRANCH_INSTRUCTIONS_SINGLE(19),
        BRANCH_MISSES_SINGLE(20),
        DTLB_READS_SINGLE(21),
        DTLB_WRITES_SINGLE(22),
        DTLB_READ_MISSES_SINGLE(23),
        DTLB_WRITE_MISSES_SINGLE(24),
        L1D_READS_SINGLE(25),
        L1D_WRITES_SINGLE(26),
        L1D_READ_MISSES_SINGLE(27),
        L1D_WRITE_MISSES_SINGLE(28),
        LLC_READS_SINGLE(29),
        LLC_WRITES_SINGLE(30),
        LLC_READ_MISSES_SINGLE(31),
        LLC_WRITE_MISSES_SINGLE(32),
        NODE_READ_MISSES_SINGLE(33),
        NODE_WRITE_MISSES_SINGLE(34),
        LLC_PREFETCH_SINGLE(35),
        LLC_PREFETCH_MISSES_SINGLE(36),
        NODE_PREFETCH_SINGLE(37),
        NODE_PREFETCH_MISSES_SINGLE(38),

        NODE_PREFETCHES_GROUP(39);




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
        HW_CPU_CYCLES(0),
        HW_INSTRUCTIONS(1),
        HW_CACHE_REFERENCES(2),
        HW_CACHE_MISSES(3),
        HW_BRANCH_INSTRUCTIONS(4),
        HW_BRANCH_MISSES(5),
        HW_BUS_CYCLES(6),
        HW_STALLED_CYCLES_FRONTEND(7),
        HW_STALLED_CYCLES_BACKEND(8),
        HW_REF_CPU_CYCLES(9),

        SW_CPU_CLOCK(10),
        SW_TASK_CLOCK(11),
        SW_PAGE_FAULTS(12),
        SW_CONTEXT_SWITCHES(13),
        SW_CPU_MIGRATIONS(14),
        SW_PAGE_FAULTS_MIN(15),
        SW_PAGE_FAULTS_MAJ(16),
        SW_ALIGNMENT_FAULTS(17),
        SW_EMULATION_FAULTS(18),
        SW_DUMMY(19),
        SW_BPF_OUTPUT(20),

        HW_CACHE_LLC_READ_MISSES(21),
        HW_CACHE_LLC_WRITE_MISSES(22),
        HW_CACHE_LLC_READS(23),
        HW_CACHE_LLC_WRITES(24),
        HW_CACHE_NODE_READ_MISSES(25),
        HW_CACHE_NODE_WRITE_MISSES(26),
        HW_CACHE_NODE_READS(27),
        HW_CACHE_NODE_WRITES(28),
        HW_CACHE_L1D_READS(29),
        HW_CACHE_L1D_READ_MISSES(30),
        HW_CACHE_L1D_WRITES(31),
        HW_CACHE_L1D_WRITE_MISSES(32),

        // All the Uncore Events are Intel Xeon E5-2690, dual socket machine specific.
        // They may in other microarchitectures as well but they are not tested.
        UNCORE_IMC_0_MEM_READ(33),
        UNCORE_IMC_1_MEM_READ(34),
        UNCORE_IMC_2_MEM_READ(35),
        UNCORE_IMC_3_MEM_READ(36),

        UNCORE_IMC_0_MEM_WRITE(37),
        UNCORE_IMC_1_MEM_WRITE(38),
        UNCORE_IMC_2_MEM_WRITE(39),
        UNCORE_IMC_3_MEM_WRITE(40),

        HW_CACHE_L1I_READS(41),
        HW_CACHE_L1I_READ_MISSES(42),
        HW_CACHE_L1D_PREFETCHES(43),
        HW_CACHE_L1D_PREFETCH_MISSES(44),

        HW_CACHE_LLC_PREFETCH(45),
        HW_CACHE_LLC_PREFETCH_MISSES(46),
        HW_CACHE_NODE_PREFETCH(47),
        HW_CACHE_NODE_PREFETCH_MISSES(48),


        HW_CACHE_DTLB_READS(49),
        HW_CACHE_DTLB_WRITES(50),
        HW_CACHE_DTLB_READ_MISSES(51),
        HW_CACHE_DTLB_WRITE_MISSES(52);

        public final int value;

        MAXINE_PERF_EVENT_ID(int i) {
            value = i;
        }
    }

    public static PerfEventGroup[] perfEventGroups;

    /**
     * Max num of threads currently supported.
     * This number is arbitrary. Theoretically it can be any number.
     */
    final static int NUM_OF_THREADS = 128;

    /**
     * The num of cores of the machine.
     */
    final static int NUM_OF_CORES = Runtime.getRuntime().availableProcessors();

    final static int NUM_OF_SUPPORTED_PERF_EVENT_GROUPS = MAXINE_PERF_EVENT_GROUP_ID.values().length;
    final static int NUM_OF_SUPPORTED_PERF_EVENTS = MAXINE_PERF_EVENT_ID.values().length;
    final static int NUM_OF_UNIQUE_PERF_GROUPS = PerfEventGroup.maxUniqueEventGroups(NUM_OF_CORES, NUM_OF_THREADS, NUM_OF_SUPPORTED_PERF_EVENT_GROUPS);
    final static int NUM_OF_UNIQUE_PERF_EVENTS = PerfEvent.maxUniquePerfEvents(NUM_OF_CORES, NUM_OF_THREADS, NUM_OF_SUPPORTED_PERF_EVENTS, NUM_OF_SUPPORTED_PERF_EVENT_GROUPS);

    public static boolean isInitialized = false;

    /**
     * This thread map holds the tid of each VmThread with {@link VmThread#id()} as index.
     * A VmThread's tid write in the map should be injected in VmThread's creation method.
     */
    public static int[] tidMap = new int[NUM_OF_THREADS];

    public static int iteration = 0;

    public static boolean LogPerf = false;

    public static long startTime = 0;
    public static long elapsedTime = 0;


    @SuppressWarnings("FieldCanBeLocal")
    public static String PerfGroup  = "cacheMisses";

    static {
        VMOptions.addFieldOption("-XX:", "LogPerf", PerfUtil.class, "Verbose perfUtil output. (default: false)", MaxineVM.Phase.PRISTINE);
        VMOptions.addFieldOption("-XX:", "PerfGroup", PerfUtil.class, "Choose between the following PerfEventGroups: sw_CacheAccesses_IMCs, cacheMisses, nodePrefetches or nodeMisses. (default: 'cacheMisses')");
    }

    //public static boolean logPerf = PerfUtil.LogPerf;

    public PerfUtil() {
    }

    public static void initialize() {
        FatalError.check(Platform.platform().os == OS.LINUX, "PerfUtil is only available for Linux.");
        if (PerfUtil.LogPerf) {
            Log.print("[PerfUtil constructor] PerfUtil initialization by thread ");
            Log.println(VmThread.current().id());
        }

        perfEventGroups = new PerfEventGroup[NUM_OF_UNIQUE_PERF_GROUPS];
        perfUtilInit(NUM_OF_UNIQUE_PERF_EVENTS);

        NUMAProfiler.splitStringtoSortedIntegers(); //we need it only for the flare object policy

        isInitialized = true;
    }

    public static void createGroup(MAXINE_PERF_EVENT_GROUP_ID group, int threadId, int tid, String threadName, int core) {
        int groupIndex = PerfEventGroup.uniqueGroupId(core, threadId, group.value);
        perfEventGroups[groupIndex] = new PerfEventGroup(group, threadId, tid, threadName, core);
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
     * PerfGroup Set Methods:
     *
     * Each method name indicates the mode it should be used for after the perfGroupSet prefix.
     *
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
     * Hint: Wrap the method with as shown below for all daemon threads:
     *
     *  for (int threadId = 1; threadId <= 5; threadId++) {
     *      perfGroupSetSpecificThread*(group, threadId, core);
     *  }
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
    public static void perfGroupSetSpecificThreadSpecificCore(MAXINE_PERF_EVENT_GROUP_ID group, int threadId, int tid, String threadName, int core) {
        PerfUtil.createGroup(group, threadId, tid, threadName, core);

        int groupIndex = PerfEventGroup.uniqueGroupId(core, threadId, group.value);
        PerfUtil.perfEventGroups[groupIndex].resetGroup();
        PerfUtil.perfEventGroups[groupIndex].enableGroup();
    }

    public static void perfGroupSetSpecificThreadAnyCore(MAXINE_PERF_EVENT_GROUP_ID group, int threadId, int tid, String threadName) {
        PerfUtil.createGroup(group, threadId, tid, threadName, -1);

        int groupIndex = PerfEventGroup.uniqueGroupId(-1, threadId, group.value);
        PerfUtil.perfEventGroups[groupIndex].resetGroup();
        PerfUtil.perfEventGroups[groupIndex].enableGroup();
    }

    public static void perfGroupSetAnyThreadSpecificCore(MAXINE_PERF_EVENT_GROUP_ID group, int core) {
        PerfUtil.createGroup(group, -1, -1, null, core);
        int groupIndex = PerfEventGroup.uniqueGroupId(core, -1, group.value);
        PerfUtil.perfEventGroups[groupIndex].resetGroup();
        PerfUtil.perfEventGroups[groupIndex].enableGroup();
    }

    /**
     * A wrapper method to set a Perf Group in AnyThreadSpecificCore mode for all available cores.
     * @param group
     */
    public static void perfGroupSetAnyThreadAllCores(MAXINE_PERF_EVENT_GROUP_ID group) {
        for (int core = 0; core < NUM_OF_CORES; core++) {
            perfGroupSetAnyThreadSpecificCore(group, core);
        }
    }

    /**
     * PerfGroup Read & Reset Methods:
     *
     * Each method name indicates the mode it should be used for after the perfGroupReadAndReset prefix.
     * As for perfGroupSet methods, ReadAndReset methods need to be treated the same way.
     * A call to readAndReset methods should be injected at the point a user wants to read the event's value
     *
     * For non-daemon threads:
     * Inject AFTER a thread's runnable execution.
     *
     * Proposed point of injection: in {@link VmThread#add(int, boolean, Address, Pointer, Pointer, Pointer, Pointer)}
     * exactly after the executeRunnable(thread) try/catch statement.
     *
     *  if (!VmThread.current().javaThread().isDaemon()) {
     *      // with this check we apply only on application (non-daemon) threads
     *      final boolean lockDisabledSafepoints = Log.lock();
     *      perfGroupReadAndResetSpecificThread*();
     *      Log.unlock(lockDisabledSafepoints);
     *
     * Of course, the values of a Perf Group can be read at any later (or earlier) point, even if the thread has been terminated and detached.
     * This depends on the perf event read frequency of choice.
     * However, pay attention: The Perf Group is no longer be accessible if a new thread with the same thread id has been created.
     *
     * For daemon threads:
     * Inject at JDK_java_lang_Runtime.gc() if the read frequency of choice is per explicit GC (System.gc()).
     * Inject at MaxineVm.exit() to avoid missing the last part of vm's execution.
     *
     * Hint: Wrap the method with as shown below for all daemon threads:
     *
     *  for (int threadId = 1; threadId <= 5; threadId++) {
     *      perfGroupReadAndResetSpecificThread*();
     *  }
     */
    public static void perfGroupReadAndResetSpecificThreadSpecificCore(MAXINE_PERF_EVENT_GROUP_ID group, int threadId, int core) {
        if (PerfUtil.LogPerf) {
            Log.print("[PerfUtil] perfGroupReadAndResetSpecificThreadSpecificCore() for thread ");
            Log.println(threadId);
        }
        int groupIndex = PerfEventGroup.uniqueGroupId(core, threadId, group.value);
        PerfUtil.perfEventGroups[groupIndex].disableGroup();
        PerfUtil.perfEventGroups[groupIndex].readGroup();
        PerfUtil.perfEventGroups[groupIndex].printGroup();
        PerfUtil.perfEventGroups[groupIndex].resetGroup();
        PerfUtil.perfEventGroups[groupIndex].enableGroup();
    }

    public static void perfGroupReadAndResetWithoutEnableSpecificThreadSpecificCore(MAXINE_PERF_EVENT_GROUP_ID group, int threadId, int core) {
        if (PerfUtil.LogPerf) {
            Log.print("[PerfUtil] perfGroupReadAndResetSpecificThreadSpecificCore() for thread ");
            Log.println(threadId);
        }
        int groupIndex = PerfEventGroup.uniqueGroupId(core, threadId, group.value);
        PerfUtil.perfEventGroups[groupIndex].disableGroup();
        PerfUtil.perfEventGroups[groupIndex].readGroup();
        PerfUtil.perfEventGroups[groupIndex].printGroup();
        PerfUtil.perfEventGroups[groupIndex].resetGroup();
    }


    public static void perfGroupReadAndResetSpecificThreadAnyCore(MAXINE_PERF_EVENT_GROUP_ID group, int threadId) {
        if (PerfUtil.LogPerf) {
            Log.print("[PerfUtil] perfGroupReadAndResetSpecificThreadAnyCore() for thread ");
            Log.println(threadId);
        }
        int groupIndex = PerfEventGroup.uniqueGroupId(-1, threadId, group.value);
        PerfUtil.perfEventGroups[groupIndex].disableGroup();
        PerfUtil.perfEventGroups[groupIndex].readGroup();
        PerfUtil.perfEventGroups[groupIndex].printGroup();
        PerfUtil.perfEventGroups[groupIndex].resetGroup();
        PerfUtil.perfEventGroups[groupIndex].enableGroup();
    }

    public static void perfGroupReadAndResetWithoutEnableSpecificThreadAnyCore(MAXINE_PERF_EVENT_GROUP_ID group, int threadId) {
        if (PerfUtil.LogPerf) {
            Log.print("[PerfUtil] perfGroupReadAndResetSpecificThreadAnyCore() for thread ");
            Log.println(threadId);
        }
        int groupIndex = PerfEventGroup.uniqueGroupId(-1, threadId, group.value);
        PerfUtil.perfEventGroups[groupIndex].disableGroup();
        PerfUtil.perfEventGroups[groupIndex].readGroup();
        PerfUtil.perfEventGroups[groupIndex].printGroup();
        PerfUtil.perfEventGroups[groupIndex].resetGroup();
    }

    public static void perfGroupReadAndResetAnyThreadSpecificCore(MAXINE_PERF_EVENT_GROUP_ID group, int core) {
        if (PerfUtil.LogPerf) {
            Log.print("[PerfUtil] perfGroupReadAndResetAnyThreadSpecificCore() for core ");
            Log.println(core);
        }
        int groupIndex = PerfEventGroup.uniqueGroupId(core, -1, group.value);
        PerfUtil.perfEventGroups[groupIndex].disableGroup();
        PerfUtil.perfEventGroups[groupIndex].readGroup();
        PerfUtil.perfEventGroups[groupIndex].printGroup();
        PerfUtil.perfEventGroups[groupIndex].resetGroup();
        PerfUtil.perfEventGroups[groupIndex].enableGroup();
    }

    public static void perfGroupReadAndResetWithoutEnableAnyThreadSpecificCore(MAXINE_PERF_EVENT_GROUP_ID group, int core) {
        if (PerfUtil.LogPerf) {
            Log.print("[PerfUtil] perfGroupReadAndResetAnyThreadSpecificCore() for core ");
            Log.println(core);
        }
        int groupIndex = PerfEventGroup.uniqueGroupId(core, -1, group.value);
        PerfUtil.perfEventGroups[groupIndex].disableGroup();
        PerfUtil.perfEventGroups[groupIndex].readGroup();
        PerfUtil.perfEventGroups[groupIndex].printGroup();
        PerfUtil.perfEventGroups[groupIndex].resetGroup();
    }

    /**
     * A wrapper method to read and reset a Perf Group in AnyThreadSpecificCore mode for all available cores.
     * @param group
     */
    public static void perfGroupReadAndResetAnyThreadAllCores(MAXINE_PERF_EVENT_GROUP_ID group) {
        for (int core = 0; core < NUM_OF_CORES; core++) {
            perfGroupReadAndResetAnyThreadSpecificCore(group, core);
        }
    }

    /**
     * PerfGroup Close Method:
     *
     * Pass the MAXINE_PERF_EVENT_GROUP_ID, the threadId and the core to specify which group should be closed.
     * This method chain ends up by calling the native close function of each perf event contained in the group.
     * The native close function uses the "close" system call to close the file descriptor of the event.
     */
    public static void perfGroupClose(MAXINE_PERF_EVENT_GROUP_ID group, int threadId, int core) {
        int groupIndex = PerfEventGroup.uniqueGroupId(core, threadId, group.value);
        perfEventGroups[groupIndex].closeGroup();
    }

    /**
     * A call to Read and Reset All remaining Perf Event Groups explicitly.
     * Useful for applications that keep their threads running during the whole execution.
     * Might be slower since it iterates through the whole perfEventGroups array.
     */
    public static void explicitPerfGroupReadAndReset() {
        for (int i = 0; i < perfEventGroups.length; i++) {
            if (perfEventGroups[i] != null && !perfEventGroups[i].isClosed()) {
                MAXINE_PERF_EVENT_GROUP_ID groupId = perfEventGroups[i].groupId;
                int thread = perfEventGroups[i].thread;
                int core = perfEventGroups[i].core;
                perfGroupReadAndResetSpecificThreadSpecificCore(groupId, thread, core);
            }
        }
    }

    /**
     * A call to Read and Reset without enabling all remaining Perf Event Groups explicitly.
     * Useful for applications that keep their threads running during the whole execution.
     * Might be slower since it iterates through the whole perfEventGroups array.
     */
    public static void explicitPerfGroupReadAndResetWithoutEnable() {
        for (int i = 0; i < perfEventGroups.length; i++) {
            if (perfEventGroups[i] != null && !perfEventGroups[i].isClosed()) {
                MAXINE_PERF_EVENT_GROUP_ID groupId = perfEventGroups[i].groupId;
                int thread = perfEventGroups[i].thread;
                int core = perfEventGroups[i].core;
                perfGroupReadAndResetWithoutEnableSpecificThreadSpecificCore(groupId, thread, core);
            }
        }
    }

    /**
     * PerfGroup Close Method explicitly:
     *
     * Pass the MAXINE_PERF_EVENT_GROUP_ID, the threadId and the core to specify which group should be closed.
     * This method chain ends up by calling the native close function of each perf event contained in the groups.
     * The native close function uses the "close" system call to close the file descriptor of the event.
     */
    public static void explicitPerfGroupClose() {
        for (int i = 0; i < perfEventGroups.length; i++) {
            if (perfEventGroups[i] != null && !perfEventGroups[i].isClosed()) {
                MAXINE_PERF_EVENT_GROUP_ID groupId = perfEventGroups[i].groupId;
                int thread = perfEventGroups[i].thread;
                int core = perfEventGroups[i].core;
                perfGroupClose(groupId, thread, core);
            }
        }
    }


    @C_FUNCTION
    public static native Pointer perfUtilInit(int numOfEvents);

}
