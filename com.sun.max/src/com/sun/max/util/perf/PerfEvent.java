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

import com.sun.max.annotate.C_FUNCTION;
import com.sun.max.unsafe.Pointer;
import com.sun.max.vm.layout.Layout;
import com.sun.max.vm.reference.Reference;

import com.sun.max.util.perf.PerfUtil.*;


public class PerfEvent {

    MAXINE_PERF_EVENT_GROUP_ID groupId;
    MAXINE_PERF_EVENT_ID groupLeaderId;
    MAXINE_PERF_EVENT_ID eventId;
    int core;
    int thread;
    long value;

    public static int coreBits = 0;
    public static int threadBits = 0;
    public static int eventBits = 0;

    /**
     *
     * @param group a {@link MAXINE_PERF_EVENT_GROUP_ID} value
     * @param eventId a {@link MAXINE_PERF_EVENT_ID} value
     * @param type for the perf_event_attribute
     * @param config for the perf_event_attribute
     * @param thread it's the thread id since we are still in the jvm
     * @param core the physical core the event is measured on
     * @param groupLeaderId the group leader event (-1 if the current event is the leader)
     */
    public PerfEvent(MAXINE_PERF_EVENT_GROUP_ID group, MAXINE_PERF_EVENT_ID eventId, PERF_TYPE_ID type, int config, int thread, int tid, int core, MAXINE_PERF_EVENT_ID groupLeaderId) {
        this.groupId = group;
        this.eventId = eventId;
        this.groupLeaderId = groupLeaderId;
        this.thread = thread;
        this.core = core;
        perfEventCreate(uniqueEventId(core, thread, eventId.value), type.value, config, thread, tid, core, uniqueEventId(core, thread, groupLeaderId.value));
    }

    public void enable() {
        perfEventEnable(uniqueEventId(core, thread, eventId.value));
    }

    public void disable() {
        perfEventDisable(uniqueEventId(core, thread, eventId.value));
    }

    public void reset() {
        perfEventReset(uniqueEventId(core, thread, eventId.value));
    }

    public void read(long[] values) {
        int dataOffset = Layout.longArrayLayout().getElementOffsetFromOrigin(0).toInt();
        perfEventRead(uniqueEventId(core, thread, eventId.value), Reference.fromJava(values).toOrigin().plus(dataOffset));
    }

    public void close() {
        perfEventClose(uniqueEventId(core, thread, eventId.value));
    }

    /**
     * A unique perf event id is a bitmask.
     * The bitmask is the concatenation of: eventId, threadId, anyThreadBit, coreId, anyCoreBit.
     *
     * @param coreId the core that the perf event should be attached on ( -1 for any core)
     * @param threadId the thread that the perf event should be attached on (-1 for any thread)
     * @param eventId the {@link MAXINE_PERF_EVENT_ID} value of the perf event
     * NOTE: coreId = -1 and threadId = -1 configuration is invalid.
     *
     * The {@code anyCoreBit} is a flag for the "measure on ANY core" configuration.
     * The {@code anyThreadBit} is a flag for the "measure ANY thread on a specified core" configuration.
     * Consequently, anyCore/ThreadBit is set to 1 only if the given coreId/threadId is -1.
     *
     * So the format of the unique perf event id is (from lsb to msb):
     * end bit = start bit + ( length - 1)
     * start bit = previous end + 1
     *
     *                      length (in bits)            start bit                                       end bit
     * coreAnyBit:                  1                       0                                               0
     * coreId:              {@link #coreBits}               1                                       {@link #coreBits}
     * threadAnyBit:                1               {@link #coreBits} + 1                           {@link #coreBits} + 1
     * threadId:            {@link #threadBits}     {@link #coreBits} + 2                           {@link #coreBits} + {@link #threadBits} + 1
     * eventId:             {@link #eventBits}      {@link #coreBits} + {@link #threadBits} + 2     {@link #coreBits} + {@link #threadBits} + {@link #eventBits} + 1
     *
     */
    public static int uniqueEventId(int coreId, int threadId, int eventId) {
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
        return coreAnyBit | coreId << 1 | threadAnyBit << (coreBits + 1) | threadId << (coreBits + 2) | eventId << (coreBits + threadBits + 2);
    }

    public static int maxUniquePerfEvents(int cores, int maxThreads, int maxEvents) {
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
        maxEvents = maxEvents - 1;
        while (maxEvents != 0) {
            eventBits++;
            maxEvents = maxEvents >> 1;
        }
        return (int) Math.pow(2, coreBits + threadBits + eventBits + 2);
    }

    @C_FUNCTION
    public static native void perfEventCreate(int id, int type, int config, int thread, int tid, int core, int groupLeaderId);

    @C_FUNCTION
    public static native void perfEventEnable(int id);

    @C_FUNCTION
    public static native void perfEventDisable(int id);

    @C_FUNCTION
    public static native void perfEventReset(int id);

    @C_FUNCTION
    public static native void perfEventRead(int id, Pointer values);

    @C_FUNCTION
    public static native void perfEventClose(int id);

}
