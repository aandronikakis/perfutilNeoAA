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

import com.sun.max.util.perf.PerfUtil;
import com.sun.max.vm.Log;
import com.sun.max.vm.thread.VmThread;

import java.util.ArrayList;

public class ProfilingData {

    public static ArrayList<DataBucket> instructions = new ArrayList<DataBucket>();
    public static long instrTotal = 0;
    public static long workerInstrTotal = 0;
    public static ArrayList<DataBucket> cpuCycles = new ArrayList<DataBucket>();

    public static ArrayList<DataBucket> workers = new ArrayList<DataBucket>();
    public static int numOfWorkers;
    public static double workerInstructionsImbalance = 0;
    public static double mainThreadHWInstructionsPercentage = 0;

    public static void add(VmThread thread, PerfUtil.MAXINE_PERF_EVENT_GROUP_ID eventGroup, long value) {
        switch (eventGroup) {
            case INSTRUCTIONS_SINGLE:
                if (value != 0) {
                    instructions.add(new DataBucket(thread, value));
                    instrTotal += value;
                }
                break;
            case CPU_CYCLES_SINGLE:
                cpuCycles.add(new DataBucket(thread, value));
                break;
        }
    }

    public static void process() {
        long meanInstructions = instrTotal / instructions.size();

        // find workers # and worker instructions mean
        for (DataBucket entry:instructions) {
            float percentage = Math.round(((float) entry.eventValue / instrTotal) * 100);
            // filter out those with low share of instructions (probably not workers)
            if (percentage >= 4) {
                // filter out main thread from workers
                if (!entry.threadName.equals("main")) {
                    workers.add(entry);
                    workerInstrTotal += entry.eventValue;
                } else {
                    mainThreadHWInstructionsPercentage = percentage;
                }
                Log.println("Instructions of " + entry.threadName + " = " + entry.eventValue + "(" + percentage + "%)");
            }
        }
        numOfWorkers = workers.size();

        // do not continue if no workers found
        if (numOfWorkers == 0) {
            return;
        }

        long meanWorkerInstructions = workerInstrTotal / numOfWorkers;

        // calculate HW Instructions imbalance of workers
        double tmp = 0;
        for (DataBucket entry:workers) {
            //stdev of workers
            double squrDiffToMean = Math.pow(entry.eventValue - meanWorkerInstructions, 2);
            tmp += squrDiffToMean;
        }
        double meanDeviation = tmp / numOfWorkers;
        double standardDeviation = Math.sqrt(meanDeviation);
        // imbalance = ( stdev / mean ) * 100 %
        workerInstructionsImbalance = Math.round((standardDeviation / meanWorkerInstructions) * 100);
        Log.println("HW Instructions imbalance = " + workerInstructionsImbalance + ", workers = " + numOfWorkers);

    }

    public static void clear() {
        instructions.clear();
        instrTotal = 0;
        cpuCycles.clear();
        workers.clear();
        numOfWorkers = 0;
        workerInstrTotal = 0;
    }

    public static class DataBucket {
        int threadId;
        int tid;
        String threadName;
        long eventValue;

        public DataBucket(VmThread thread, long value) {
            this.threadId = thread.id();
            this.tid = thread.tid();
            this.threadName = thread.getName();
            this.eventValue = value;
        }
    }
}
