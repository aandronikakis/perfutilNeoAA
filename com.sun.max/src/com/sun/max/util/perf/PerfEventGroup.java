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

    MAXINE_PERF_EVENT_GROUP_ID groupId;
    int numOfEvents;
    PerfEvent[] perfEvents;

    public PerfEventGroup(MAXINE_PERF_EVENT_GROUP_ID group) {
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
        perfEvents[0] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.LLC_MISSES_GROUP, MAXINE_PERF_EVENT_ID.LLC_READ_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_READ_MISS.value, groupLeaderId);
        perfEvents[1] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.LLC_MISSES_GROUP, MAXINE_PERF_EVENT_ID.LLC_WRITE_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_WRITE_MISS.value, groupLeaderId);
    }

    public void createMiscGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.MISC_GROUP;
        final MAXINE_PERF_EVENT_ID groupLeaderId = MAXINE_PERF_EVENT_ID.INSTRUCTIONS;
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        //hwInstructionsEvent
        perfEvents[0] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.MISC_GROUP, MAXINE_PERF_EVENT_ID.INSTRUCTIONS, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_INSTRUCTIONS.value, groupLeaderId);
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
    }

    public void readGroup() {
        long[] valuesBuffer = new long[numOfEvents];

        // call read from group leader
        perfEvents[0].read(valuesBuffer);

        // store the read values to their dedicated PerfEvent objects
        for (int i = 0; i < numOfEvents; i++) {
            perfEvents[i].value = valuesBuffer[i];
        }
    }

    public void printGroup() {
        Log.print("Print ");
        Log.println(groupId);

        for (int i = 0; i < numOfEvents; i++) {
            Log.print(perfEvents[i].eventId);
            Log.print(" = ");
            Log.println(perfEvents[i].value);
        }
    }

}
