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
import com.sun.max.vm.heap.Heap;

import static com.sun.max.util.perf.PerfEvent.uniqueEventId;

public class PerfEventGroup {

    public PerfEvent[] perfEvents;

    public MAXINE_PERF_EVENT_GROUP_ID groupId;
    public int thread;
    public int tid;
    public String threadName;
    public int core;
    public int numOfEvents;
    public long timeEnabled;
    public long timeRunning;
    public int timeRunningPercentage;
    public boolean isClosed;

    public static int coreBits = 0;
    public static int threadBits = 0;
    public static int groupBits = 0;

    public long[] valuesBuffer;
    public long[] timesBuffer;
    public PerfEventGroup(MAXINE_PERF_EVENT_GROUP_ID group, int threadId, int tid, String threadName, int core) {

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
        this.threadName = threadName;
        this.core = core;
        this.timeEnabled = 0;
        this.timeRunning = 0;
        this.isClosed = false;
        switch (group) {
            case SW_GROUP:
                createSWGroup();
                break;
            case HW_GROUP:
                createHWGroup();
                break;
            case LLC_READS_GROUP:
                createLLCReadsGroup();
                break;
            case LLC_WRITES_GROUP:
                createLLCWritesGroup();
                break;
            case NODE_READS_GROUP:
                createNODEReadsGroup();
                break;
            case NODE_WRITES_GROUP:
                createNODEWritesGroup();
                break;
            case CACHE_ACCESSES_GROUP:
                createCacheAccessesGroup();
                break;
            case CACHE_MISSES_GROUP:
                createCacheMissesGroup();
                break;
            case NODE_MISSES_GROUP:
                createNodeMissesGroup();
                break;
            case UNCORE_IMC_0_CPU_0_GROUP:
                createUncoreiMC0GroupCPU0();
                break;
            case UNCORE_IMC_1_CPU_0_GROUP:
                createUncoreiMC1GroupCPU0();
                break;
            case UNCORE_IMC_2_CPU_0_GROUP:
                createUncoreiMC2GroupCPU0();
                break;
            case UNCORE_IMC_3_CPU_0_GROUP:
                createUncoreiMC3GroupCPU0();
                break;
            case UNCORE_IMC_0_CPU_1_GROUP:
                createUncoreiMC0GroupCPU1();
                break;
            case UNCORE_IMC_1_CPU_1_GROUP:
                createUncoreiMC1GroupCPU1();
                break;
            case UNCORE_IMC_2_CPU_1_GROUP:
                createUncoreiMC2GroupCPU1();
                break;
            case UNCORE_IMC_3_CPU_1_GROUP:
                createUncoreiMC3GroupCPU1();
                break;
            case CPU_CYCLES_SINGLE:
                createCyclesSingle();
                break;
            case INSTRUCTIONS_SINGLE:
                createInstructionsSingle();
                break;
            case BRANCH_INSTRUCTIONS_SINGLE:
                createBranchInstructionsGroup();
                break;
            case BRANCH_MISSES_SINGLE:
                createBranchMissesGroup();
                break;
            case DTLB_READS_SINGLE:
                createDTLBReadsSingle();
                break;
            case DTLB_WRITES_SINGLE:
                createDTLBWritesSingle();
                break;
            case DTLB_READ_MISSES_SINGLE:
                createDTLBReadMissesSingle();
                break;
            case DTLB_WRITE_MISSES_SINGLE:
                createDTLBWriteMissesSingle();
                break;
            case L1D_READS_SINGLE:
                createL1DReadsSingle();
                break;
            case L1D_WRITES_SINGLE:
                createL1DWritesSingle();
                break;
            case L1D_READ_MISSES_SINGLE:
                createL1DReadMissesSingle();
                break;
            case L1D_WRITE_MISSES_SINGLE:
                createL1DWriteMissesSingle();
                break;
            case LLC_READS_SINGLE:
                createLLCReadsSingle();
                break;
            case LLC_WRITES_SINGLE:
                createLLCWritesSingle();
                break;
            case LLC_READ_MISSES_SINGLE:
                createLLCReadMissesSingle();
                break;
            case LLC_WRITE_MISSES_SINGLE:
                createLLCWriteMissesSingle();
                break;
            case NODE_READ_MISSES_SINGLE:
                createNodeReadMissesSingle();
                break;
            case NODE_WRITE_MISSES_SINGLE:
                createNodeWriteMissesSingle();
                break;
            case LLC_PREFETCH_SINGLE:
                createLLCPrefetchSingle();
                break;
            case LLC_PREFETCH_MISSES_SINGLE:
                createLLCPrefetchMissesSingle();
                break;
            case NODE_PREFETCH_SINGLE:
                createNodePrefetchSingle();
                break;
            case NODE_PREFETCH_MISSES_SINGLE:
                createNodePrefetchMissesSingle();
                break;
        }
        timesBuffer = new long[2];
        valuesBuffer = new long[numOfEvents];
    }

    public boolean isClosed() {
        return isClosed;
    }

    public void createLLCReadsGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.LLC_READS_GROUP;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES.value, groupId.value);
        numOfEvents = 6;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_CPU_CYCLES.value, thread, tid, core, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_INSTRUCTIONS, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_INSTRUCTIONS.value, thread, tid, core, groupLeaderId);
        perfEvents[2] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_READS, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_L1D_READ.value, thread, tid, core, groupLeaderId);
        perfEvents[3] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_READ_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_L1D_READ_MISS.value, thread, tid, core, groupLeaderId);
        perfEvents[4] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_READS, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_READ.value, thread, tid, core, groupLeaderId);
        perfEvents[5] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_READ_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_READ_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createLLCWritesGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.LLC_WRITES_GROUP;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES.value, groupId.value);
        numOfEvents = 6;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_CPU_CYCLES.value, thread, tid, core, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_INSTRUCTIONS, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_INSTRUCTIONS.value, thread, tid, core, groupLeaderId);
        perfEvents[2] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_WRITES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_L1D_WRITE.value, thread, tid, core, groupLeaderId);
        // l1d write misses is not supported by intel skylake/kaby lake architectures..
        perfEvents[3] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_WRITE_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_L1D_WRITE_MISS.value, thread, tid, core, groupLeaderId);
        perfEvents[4] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_WRITES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_WRITE.value, thread, tid, core, groupLeaderId);
        perfEvents[5] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_WRITE_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_WRITE_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createNODEReadsGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.NODE_READS_GROUP;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES.value, groupId.value);
        numOfEvents = 4;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_CPU_CYCLES.value, thread, tid, core, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_INSTRUCTIONS, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_INSTRUCTIONS.value, thread, tid, core, groupLeaderId);
        perfEvents[2] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_READS, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_NODE_READ.value, thread, tid, core, groupLeaderId);
        perfEvents[3] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_READ_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_NODE_READ_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createNODEWritesGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.NODE_WRITES_GROUP;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES.value, groupId.value);
        numOfEvents = 4;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_CPU_CYCLES.value, thread, tid, core, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_INSTRUCTIONS, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_INSTRUCTIONS.value, thread, tid, core, groupLeaderId);
        perfEvents[2] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_WRITES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_NODE_WRITE.value, thread, tid, core, groupLeaderId);
        perfEvents[3] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_WRITE_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_NODE_WRITE_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createSWGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.SW_GROUP;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.SW_PAGE_FAULTS.value, groupId.value);
        numOfEvents = 5;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.SW_GROUP, MAXINE_PERF_EVENT_ID.SW_PAGE_FAULTS, PERF_TYPE_ID.PERF_TYPE_SOFTWARE, PERF_SW_EVENT_ID.PERF_COUNT_SW_PAGE_FAULTS.value, thread, tid, core, groupLeaderId);
        perfEvents[1] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.SW_GROUP, MAXINE_PERF_EVENT_ID.SW_CONTEXT_SWITCHES, PERF_TYPE_ID.PERF_TYPE_SOFTWARE, PERF_SW_EVENT_ID.PERF_COUNT_SW_CONTEXT_SWITCHES.value, thread, tid, core, groupLeaderId);
        perfEvents[2] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.SW_GROUP, MAXINE_PERF_EVENT_ID.SW_CPU_MIGRATIONS, PERF_TYPE_ID.PERF_TYPE_SOFTWARE, PERF_SW_EVENT_ID.PERF_COUNT_SW_CPU_MIGRATIONS.value, thread, tid, core, groupLeaderId);
        perfEvents[3] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.SW_GROUP, MAXINE_PERF_EVENT_ID.SW_PAGE_FAULTS_MIN, PERF_TYPE_ID.PERF_TYPE_SOFTWARE, PERF_SW_EVENT_ID.PERF_COUNT_SW_PAGE_FAULTS_MIN.value, thread, tid, core, groupLeaderId);
        perfEvents[4] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.SW_GROUP, MAXINE_PERF_EVENT_ID.SW_PAGE_FAULTS_MAJ, PERF_TYPE_ID.PERF_TYPE_SOFTWARE, PERF_SW_EVENT_ID.PERF_COUNT_SW_PAGE_FAULTS_MAJ.value, thread, tid, core, groupLeaderId);
    }

    public void createHWGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.HW_GROUP;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES.value, groupId.value);
        numOfEvents = 6;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.HW_GROUP, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_CPU_CYCLES.value, thread, tid, core, groupLeaderId);
        perfEvents[1] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.HW_GROUP, MAXINE_PERF_EVENT_ID.HW_INSTRUCTIONS, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_INSTRUCTIONS.value, thread, tid, core, groupLeaderId);
        perfEvents[2] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.HW_GROUP, MAXINE_PERF_EVENT_ID.HW_CACHE_REFERENCES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_CACHE_REFERENCES.value, thread, tid, core, groupLeaderId);
        perfEvents[3] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.HW_GROUP, MAXINE_PERF_EVENT_ID.HW_CACHE_MISSES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_CACHE_MISSES.value, thread, tid, core, groupLeaderId);
        perfEvents[4] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.HW_GROUP, MAXINE_PERF_EVENT_ID.HW_STALLED_CYCLES_BACKEND, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_BRANCH_INSTRUCTIONS.value, thread, tid, core, groupLeaderId);
        perfEvents[5] = new PerfEvent(MAXINE_PERF_EVENT_GROUP_ID.HW_GROUP, MAXINE_PERF_EVENT_ID.HW_REF_CPU_CYCLES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_BRANCH_MISSES.value, thread, tid, core, groupLeaderId);
    }

    public void createCacheAccessesGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.CACHE_ACCESSES_GROUP;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES.value, groupId.value);
        numOfEvents = 6;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_CPU_CYCLES.value, thread, tid, core, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_INSTRUCTIONS, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_INSTRUCTIONS.value, thread, tid, core, groupLeaderId);
        perfEvents[2] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_READS, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_L1D_READ.value, thread, tid, core, groupLeaderId);
        perfEvents[3] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_WRITES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_L1D_WRITE.value, thread, tid, core, groupLeaderId);
        perfEvents[4] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_READS, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_READ.value, thread, tid, core, groupLeaderId);
        perfEvents[5] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_WRITES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_WRITE.value, thread, tid, core, groupLeaderId);
    }

    public void createCyclesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.CPU_CYCLES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_CPU_CYCLES.value, thread, tid, core, groupLeaderId);
    }

    public void createInstructionsSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.INSTRUCTIONS_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_INSTRUCTIONS.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_INSTRUCTIONS, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_INSTRUCTIONS.value, thread, tid, core, groupLeaderId);
    }

    public void createBranchInstructionsGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.BRANCH_INSTRUCTIONS_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_BRANCH_INSTRUCTIONS.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_BRANCH_INSTRUCTIONS, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_BRANCH_INSTRUCTIONS.value, thread, tid, core, groupLeaderId);
    }

    public void createBranchMissesGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.BRANCH_MISSES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_BRANCH_MISSES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_BRANCH_MISSES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_BRANCH_MISSES.value, thread, tid, core, groupLeaderId);
    }

    public void createDTLBReadsSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.DTLB_READS_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_DTLB_READS.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_DTLB_READS, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_DTLB_READ.value, thread, tid, core, groupLeaderId);
    }

    public void createDTLBWritesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.DTLB_WRITES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_DTLB_WRITES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_DTLB_WRITES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_DTLB_WRITE.value, thread, tid, core, groupLeaderId);
    }

    public void createDTLBReadMissesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.DTLB_READ_MISSES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_DTLB_READ_MISSES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_DTLB_READ_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_DTLB_READ_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createDTLBWriteMissesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.DTLB_WRITE_MISSES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_DTLB_WRITE_MISSES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_DTLB_WRITE_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_DTLB_WRITE_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createL1DReadsSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.L1D_READS_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_READS.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_READS, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_L1D_READ.value, thread, tid, core, groupLeaderId);
    }

    public void createL1DWritesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.L1D_WRITES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_WRITES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_WRITES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_L1D_WRITE.value, thread, tid, core, groupLeaderId);
    }

    public void createL1DReadMissesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.L1D_READ_MISSES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_READ_MISSES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_READ_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_L1D_READ_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createL1DWriteMissesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.L1D_WRITE_MISSES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_WRITE_MISSES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_L1D_WRITE_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_L1D_WRITE_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createLLCReadsSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.LLC_READS_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_READS.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_READS, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_READ.value, thread, tid, core, groupLeaderId);
    }

    public void createLLCWritesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.LLC_WRITES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_WRITES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_WRITES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_WRITE.value, thread, tid, core, groupLeaderId);
    }

    public void createLLCReadMissesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.LLC_READ_MISSES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_READ_MISSES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_READ_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_READ_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createLLCWriteMissesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.LLC_WRITE_MISSES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_WRITE_MISSES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_WRITE_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_WRITE_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createNodeReadMissesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.NODE_READ_MISSES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_READ_MISSES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_READ_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_NODE_READ_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createNodeWriteMissesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.NODE_WRITE_MISSES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_WRITE_MISSES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_WRITE_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_NODE_WRITE_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createLLCPrefetchSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.LLC_PREFETCH_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_PREFETCH.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_PREFETCH, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_PREFETCH.value, thread, tid, core, groupLeaderId);
    }

    public void createLLCPrefetchMissesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.LLC_PREFETCH_MISSES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_PREFETCH_MISSES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_PREFETCH_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_PREFETCH_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createNodePrefetchSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.NODE_PREFETCH_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_PREFETCH.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_PREFETCH, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_NODE_PREFETCH.value, thread, tid, core, groupLeaderId);
    }

    public void createNodePrefetchMissesSingle() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.NODE_PREFETCH_MISSES_SINGLE;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_PREFETCH_MISSES.value, groupId.value);
        numOfEvents = 1;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_PREFETCH_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_NODE_PREFETCH_MISS.value, thread, tid, core, groupLeaderId);
    }

    public void createCacheMissesGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.CACHE_MISSES_GROUP;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES.value, groupId.value);
        numOfEvents = 6;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_CPU_CYCLES.value, thread, tid, core, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_INSTRUCTIONS, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_INSTRUCTIONS.value, thread, tid, core, groupLeaderId);
        perfEvents[2] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_READ_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_READ_MISS.value, thread, tid, core, groupLeaderId);
        perfEvents[3] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_LLC_WRITE_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_LLC_WRITE_MISS.value, thread, tid, core, groupLeaderId);
        perfEvents[4] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_BRANCH_INSTRUCTIONS, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_BRANCH_INSTRUCTIONS.value, thread, tid, core, groupLeaderId);
        perfEvents[5] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_BRANCH_MISSES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_BRANCH_MISSES.value, thread, tid, core, groupLeaderId);
    }

    public void createNodeMissesGroup() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.NODE_MISSES_GROUP;
        final int groupLeaderId = uniqueEventId(core, thread, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES.value, groupId.value);
        numOfEvents = 4;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CPU_CYCLES, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_CPU_CYCLES.value, thread, tid, core, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_INSTRUCTIONS, PERF_TYPE_ID.PERF_TYPE_HARDWARE, PERF_HW_EVENT_ID.PERF_COUNT_HW_INSTRUCTIONS.value, thread, tid, core, groupLeaderId);
        perfEvents[2] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_READ_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_NODE_READ_MISS.value, thread, tid, core, groupLeaderId);
        perfEvents[3] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.HW_CACHE_NODE_WRITE_MISSES, PERF_TYPE_ID.PERF_TYPE_HW_CACHE, PERF_HW_CACHE_EVENT_ID.CACHE_NODE_WRITE_MISS.value, thread, tid, core, groupLeaderId);
    }

    /**
     * Intel Xeon E5-2690, dual socket machine specific.
     */
    public void createUncoreiMC0GroupCPU0() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.UNCORE_IMC_0_CPU_0_GROUP;
        final int groupLeaderId = uniqueEventId(0, -1, MAXINE_PERF_EVENT_ID.UNCORE_IMC_0_MEM_READ.value, groupId.value);
        numOfEvents = 2;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_0_MEM_READ, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_0, PERF_UNCORE_IMC_EVENT_ID.MEM_READ.value, -1, -1, 0, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_0_MEM_WRITE, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_0, PERF_UNCORE_IMC_EVENT_ID.MEM_WRITE.value, -1, -1, 0, groupLeaderId);
    }

    public void createUncoreiMC0GroupCPU1() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.UNCORE_IMC_0_CPU_1_GROUP;
        final int groupLeaderId = uniqueEventId(1, -1, MAXINE_PERF_EVENT_ID.UNCORE_IMC_0_MEM_READ.value, groupId.value);
        numOfEvents = 2;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_0_MEM_READ, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_0, PERF_UNCORE_IMC_EVENT_ID.MEM_READ.value, -1, -1, 1, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_0_MEM_WRITE, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_0, PERF_UNCORE_IMC_EVENT_ID.MEM_WRITE.value, -1, -1, 1, groupLeaderId);
    }

    public void createUncoreiMC1GroupCPU0() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.UNCORE_IMC_1_CPU_0_GROUP;
        final int groupLeaderId = uniqueEventId(0, -1, MAXINE_PERF_EVENT_ID.UNCORE_IMC_1_MEM_READ.value, groupId.value);
        numOfEvents = 2;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_1_MEM_READ, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_1, PERF_UNCORE_IMC_EVENT_ID.MEM_READ.value, -1, -1, 0, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_1_MEM_WRITE, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_1, PERF_UNCORE_IMC_EVENT_ID.MEM_WRITE.value, -1, -1, 0, groupLeaderId);
    }

    public void createUncoreiMC1GroupCPU1() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.UNCORE_IMC_1_CPU_1_GROUP;
        final int groupLeaderId = uniqueEventId(1, -1, MAXINE_PERF_EVENT_ID.UNCORE_IMC_1_MEM_READ.value, groupId.value);
        numOfEvents = 2;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_1_MEM_READ, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_1, PERF_UNCORE_IMC_EVENT_ID.MEM_READ.value, -1, -1, 1, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_1_MEM_WRITE, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_1, PERF_UNCORE_IMC_EVENT_ID.MEM_WRITE.value, -1, -1, 1, groupLeaderId);
    }

    public void createUncoreiMC2GroupCPU0() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.UNCORE_IMC_2_CPU_0_GROUP;
        final int groupLeaderId = uniqueEventId(0, -1, MAXINE_PERF_EVENT_ID.UNCORE_IMC_2_MEM_READ.value, groupId.value);
        numOfEvents = 2;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_2_MEM_READ, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_2, PERF_UNCORE_IMC_EVENT_ID.MEM_READ.value, -1, -1, 0, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_2_MEM_WRITE, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_2, PERF_UNCORE_IMC_EVENT_ID.MEM_WRITE.value, -1, -1, 0, groupLeaderId);
    }

    public void createUncoreiMC2GroupCPU1() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.UNCORE_IMC_2_CPU_1_GROUP;
        final int groupLeaderId = uniqueEventId(1, -1, MAXINE_PERF_EVENT_ID.UNCORE_IMC_2_MEM_READ.value, groupId.value);
        numOfEvents = 2;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_2_MEM_READ, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_2, PERF_UNCORE_IMC_EVENT_ID.MEM_READ.value, -1, -1, 1, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_2_MEM_WRITE, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_2, PERF_UNCORE_IMC_EVENT_ID.MEM_WRITE.value, -1, -1, 1, groupLeaderId);
    }

    public void createUncoreiMC3GroupCPU0() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.UNCORE_IMC_3_CPU_0_GROUP;
        final int groupLeaderId = uniqueEventId(0, -1, MAXINE_PERF_EVENT_ID.UNCORE_IMC_3_MEM_READ.value, groupId.value);
        numOfEvents = 2;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_3_MEM_READ, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_3, PERF_UNCORE_IMC_EVENT_ID.MEM_READ.value, -1, -1, 0, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_3_MEM_WRITE, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_3, PERF_UNCORE_IMC_EVENT_ID.MEM_WRITE.value, -1, -1, 0, groupLeaderId);
    }

    public void createUncoreiMC3GroupCPU1() {
        groupId = MAXINE_PERF_EVENT_GROUP_ID.UNCORE_IMC_3_CPU_1_GROUP;
        final int groupLeaderId = uniqueEventId(1, -1, MAXINE_PERF_EVENT_ID.UNCORE_IMC_3_MEM_READ.value, groupId.value);
        numOfEvents = 2;
        perfEvents = new PerfEvent[numOfEvents];
        perfEvents[0] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_3_MEM_READ, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_3, PERF_UNCORE_IMC_EVENT_ID.MEM_READ.value, -1, -1, 1, groupLeaderId);
        perfEvents[1] = new PerfEvent(groupId, MAXINE_PERF_EVENT_ID.UNCORE_IMC_3_MEM_WRITE, PERF_TYPE_ID.PERF_TYPE_UNCORE_IMC_3, PERF_UNCORE_IMC_EVENT_ID.MEM_WRITE.value, -1, -1, 1, groupLeaderId);
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
        // call read from group leader
        perfEvents[0].read(timesBuffer, valuesBuffer);

        // store the time values to their dedicated PerfEventGroup instance fields
        timeEnabled = timesBuffer[0];
        timeRunning = timesBuffer[1];
        //calculate the percentage of time the group has been actually monitored
        //timeRunningPercentage = (int) (timeRunning / timeEnabled) * 100;
        if (timeEnabled != 0) {
            timeRunningPercentage = (int) ((timeRunning / (float) timeEnabled) * 100);
        } else {
            timeRunningPercentage = 0;
        }
        if (PerfUtil.logPerf) {
            Log.print(groupId);
            Log.print(" of thread ");
            Log.print(thread);
            Log.print(" | time Enabled: ");
            Log.print(timeEnabled);
            Log.print(" time Running: ");
            Log.print(timeRunning);
            Log.print(" ");
            Log.println(timeRunningPercentage);
        }

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
            Log.print("[PerfEventGroup] Print group ");
            Log.println(groupId);
        }

        for (int i = 0; i < numOfEvents; i++) {
            Log.print("(PerfUtil);");
            Log.print(PerfUtil.iteration);
            Log.print(";");
            Log.print(perfEvents[i].groupId);
            Log.print(";");
            Log.print(tid);
            Log.print(";");
            Log.print(threadName);
            Log.print(";");
            Log.print(core);
            Log.print(";");
            Log.print(perfEvents[i].eventId);
            Log.print(";");
            Log.print(perfEvents[i].value);
            Log.print(";");
            Log.println(timeRunningPercentage);
        }
    }

    public void closeGroup() {
        isClosed = true;
        if (PerfUtil.logPerf) {
            Log.print("[PerfEventGroup] Close group ");
            Log.print(groupId);
            Log.print(" for thread ");
            Log.println(thread);
        }
        for (int i = 0; i < numOfEvents; i++) {
            perfEvents[i].close();
        }
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
