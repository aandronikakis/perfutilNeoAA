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

package com.sun.max.util;

import com.sun.max.annotate.*;
import com.sun.max.unsafe.Pointer;

import com.sun.max.vm.reference.Reference;

/**
 * See perf.h for more detailed documentation.
 * https://docs.huihoo.com/doxygen/linux/kernel/3.7/include_2uapi_2linux_2perf__event_8h_source.html
 */

public class PerfUtil {

    /**
     *  Available PerfEvent types.
     *  attr.type
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
     * Common hardware events.
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

    public enum PERF_HW_CACHE_OP_ID {
        PERF_COUNT_HW_CACHE_OP_READ(0),
        PERF_COUNT_HW_CACHE_OP_WRITE(1),
        PERF_COUNT_HW_CACHE_OP_PREFETCH(2);

        public final int value;

        PERF_HW_CACHE_OP_ID(int i) {
            value = i;
        }
    }

    public enum PERF_HW_CACHE_OP_RESULT_ID {
        PERF_COUNT_HW_CACHE_RESULT_ACCESS(0),
        PERF_COUNT_HW_CACHE_RESULT_MISS(1);

        public final int value;

        PERF_HW_CACHE_OP_RESULT_ID(int i) {
            value = i;
        }
    }

    /**
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


    // list of supported perf events
    public PerfEvent cacheMissesPerfEvent;
    public PerfEvent llcMissesPerfEvent;
    public PerfEvent hwInstructionsEvent;

    public enum MAXINE_PERF_EVENT_ID {
        CACHE_MISSES(0),
        LLC_MISSES(1),
        INSTRUCTIONS(2);
        public final int value;
        MAXINE_PERF_EVENT_ID(int i) {
            value = i;
        }
    }

    public final int numOfSupportedPerfEvents = 3;

    public PerfUtil() {
        perfUtilInit(numOfSupportedPerfEvents);
        cacheMissesPerfEvent = new PerfEvent(MAXINE_PERF_EVENT_ID.CACHE_MISSES.value, "CacheMisses", PERF_TYPE_ID.PERF_TYPE_HARDWARE.value, PERF_HW_EVENT_ID.PERF_COUNT_HW_CACHE_MISSES.value);
        llcMissesPerfEvent = new PerfEvent(MAXINE_PERF_EVENT_ID.LLC_MISSES.value, "LLC Misses", PERF_TYPE_ID.PERF_TYPE_HW_CACHE.value, PERF_HW_CACHE_EVENT_ID.PERF_COUNT_HW_CACHE_LL.value);
        hwInstructionsEvent = new PerfEvent(MAXINE_PERF_EVENT_ID.INSTRUCTIONS.value, "HW Instructions", PERF_TYPE_ID.PERF_TYPE_HARDWARE.value, PERF_HW_EVENT_ID.PERF_COUNT_HW_INSTRUCTIONS.value);
    }

    public void enableAllEvents() {
        perfEventEnableAll();
    }

    public void disableAllEvents() {
        perfEventDisableAll();
    }

    public void resetAllEvents() {
        perfEventResetAll();
    }

    @C_FUNCTION
    public static native Pointer perfUtilInit(int numOfEvents);

    @C_FUNCTION
    public static native void perfEventEnableAll();

    @C_FUNCTION
    public static native void perfEventDisableAll();

    @C_FUNCTION
    public static native void perfEventResetAll();


}
