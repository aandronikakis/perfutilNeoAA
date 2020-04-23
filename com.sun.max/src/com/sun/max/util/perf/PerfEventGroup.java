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

import com.sun.max.vm.Log;
import com.sun.max.util.perf.PerfUtil.*;

public class PerfEventGroup {

    PerfEvent[] perfEvents;

    MAXINE_PERF_EVENT_GROUP_ID groupId;
    int thread;
    int tid;
    int core;
    int numOfEvents;

    public static int coreBits = 0;
    public static int threadBits = 0;
    public static int groupBits = 0;

    public PerfEventGroup(MAXINE_PERF_EVENT_GROUP_ID group, int threadId, int tid, int core) {
        if (PerfUtil.logPerf) {
            Log.print("[PerfEventGroup] create ");
            Log.print(group);
            Log.print(" for thread ");
            Log.print(threadId);
            Log.print(" with tid ");
            Log.print(tid);
            Log.print(" on core ");
            Log.println(core);
        }
        this.thread = threadId;
        this.tid = tid;
        this.core = core;
        switch (group) {
            case LLC_MISSES_GROUP:
                createLLCMissesGroup();
                break;
            case MISC_GROUP:
                createMiscGroup();
                break;
        }
    }

    public void createLLCMissesGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.LLC_MISSES_GROUP;
        final MAXINE_PERF_EVENT_ID groupLeaderId = MAXINE_PERF_EVENT_ID.LLC_READ_MISSES;
        numOfEvents = 2;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.LLC_MISSES_GROUP, MAXINE_PERF_EVENT_ID.LLC_READ_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_READ_MISS.value, thread, tid, core, groupLeaderId);
        perfEvents[1] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.LLC_MISSES_GROUP, MAXINE_PERF_EVENT_ID.LLC_WRITE_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_WRITE_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createMiscGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.MISC_GROUP;
        final MAXINE_PERF_EVENT_ID groupLeaderId = MAXINE_PERF_EVENT_ID.INSTRUCTIONS;
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        //hwInstructionsEvent
        perfEvents[0] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.MISC_GROUP, MAXINE_PERF_EVENT_ID.INSTRUCTIONS, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_INSTRUCTIONS.value, thread, tid, core, groupLeaderId);
    }

    public void enableGroup() {
        // enable the group leader
        perfEvents[0].enable();
    }

    public void disableGroup() {
        // disable the group leader
        perfEvents[0].disable();
    }

    public void resetGroup() {
        // reset the group leader
        perfEvents[0].reset();
        //readGroup();
    }

    public void readGroup() {
        long[] valuesBuffer = new long[numOfEvents];

        // call read from group leader
        perfEvents[0].read(valuesBuffer);

        // store the read values to their dedicated PerfEvent objects
        for (int i = 0; i < numOfEvents; i++) {
            if (PerfUtil.logPerf) {
                Log.print("  ==> Value read = ");
                Log.println(valuesBuffer[i]);
            }
            perfEvents[i].value = valuesBuffer[i];
        }
    }

    public void printGroup() {
        if (PerfUtil.logPerf) {
            Log.print("Print group ");
            Log.print(groupId);
        }

        for (int i = 0; i < numOfEvents; i++) {
            Log.print("== Iteration ");
            Log.print(PerfUtil.iteration);
            Log.print(", thread ");
            Log.print(thread);
            Log.print(", core ");
            Log.print(core);
            Log.print(" : ");
            Log.print(perfEvents[i].eventId);
            Log.print(" = ");
            Log.println(perfEvents[i].value);
        }
        //Log.unlock(lock);
    }

    /**
     * A unique perf event group id is a bitmask.
     * The bitmask is the concatenation of: groupId, threadId, anyThreadBit, coreId, anyCoreBit.
     *
     * @param coreId the core that the perf event should be attached on ( -1 for any core)
     * @param threadId the thread that the perf event should be attached on (-1 for any thread)
     * @param groupId the {@link MAXINE_PERF_EVENT_GROUP_ID} value of the perf event group
     * NOTE: coreId = -1, threadId = -1 configuration is invalid.
     *
     * The {@code anyCoreBit} is a flag for the "measure on ANY core" configuration.
     * The {@code anyThreadBit} is a flag for the "measure ANY thread on a specified core" configuration.
     * Consequently, anyCore/ThreadBit is set to 1 only if the given coreId/threadId is -1.
     *
     * So the format of the unique perf event group id is (from lsb to msb):
     * end bit = start bit + ( length - 1)
     * start bit = previous end + 1
     *
     *                      length (in bits)            start bit                                       end bit
     * coreAnyBit:                  1                       0                                               0
     * coreId:              {@link #coreBits}               1                                       {@link #coreBits}
     * threadAnyBit:                1               {@link #coreBits} + 1                           {@link #coreBits} + 1
     * threadId:            {@link #threadBits}     {@link #coreBits} + 2                           {@link #coreBits} + {@link #threadBits} + 1
     * groupId:             {@link #groupBits}      {@link #coreBits} + {@link #threadBits} + 2     {@link #coreBits} + {@link #threadBits} + {@link #groupBits} + 1
     *
     */
    public static int uniqueGroupId(int coreId, int threadId, int groupId) {
        int coreAnyBit = 0;
        int threadAnyBit = 0;
        if (coreId == -1) {
            coreId = 0;
            coreAnyBit = 1;
        }
        if (threadId == -1) {
            threadId = 0;
            threadAnyBit = 1;
        }
        return coreAnyBit | coreId << 1 | threadAnyBit << (coreBits + 1) | threadId << (coreBits + 2) | groupId << (coreBits + threadBits + 2);
    }

    public static int maxUniqueEventGroups(int cores, int maxThreads, int maxGroups) {
        cores = cores - 1;
        while (cores != 0) {
            coreBits++;
            cores = cores >> 1;
        }
        maxThreads = maxThreads - 1;
        while (maxThreads != 0) {
            threadBits++;
            maxThreads = maxThreads >> 1;
        }
        maxGroups = maxGroups - 1;
        while (maxGroups != 0) {
            groupBits++;
            maxGroups = maxGroups >> 1;
        }
        return (int) Math.pow(2, coreBits + threadBits + groupBits + 2);
    }

}
