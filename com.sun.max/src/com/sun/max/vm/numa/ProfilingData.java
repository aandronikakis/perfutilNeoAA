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

    public static ArrayList<DataBucket> data = new ArrayList<DataBucket>();

    public static long instrTotal = 0;
    public static long cyclesTotal = 0;
    public static long workerInstrTotal = 0;

    public static ArrayList<DataBucket> workers = new ArrayList<DataBucket>();
    public static int numOfWorkers;
    public static double workerInstructionsImbalance = 0;
    public static double mainThreadHWInstructionsPercentage = 0;

    public static double applicationCurrentCPI;
    public static double applicationPastCPI = 0;


    public static void add(VmThread thread, long instructions, long cpuCycles) {
        data.add(new DataBucket(thread, instructions, cpuCycles));
        instrTotal += instructions;
        cyclesTotal += cpuCycles;
    }

    public static void process() {
        long meanInstructions = instrTotal / data.size();

        applicationCurrentCPI = Math.floor(100 * ((double) cyclesTotal / instrTotal) + 0.5) / 100;

        // update single/dual node cpi
        //NUMAState.getFsmState()[1].updateCPI(applicationCurrentCPI);
        NUMAState.updateCPI(applicationCurrentCPI);

        // find workers # and worker instructions mean
        for (DataBucket entry:data) {
            entry.setIPercentage(Math.floor(((double) entry.instructions / instrTotal) * 100));
            // filter out those with low share of instructions (probably not workers)
            if (entry.iPercentage >= 4) {
                // filter out main thread from workers
                if (!entry.threadName.equals("main")) {
                    workers.add(entry);
                    workerInstrTotal += entry.instructions;
                } else {
                    mainThreadHWInstructionsPercentage = entry.iPercentage;
                }
                /*Log.println(entry.threadName +
                        ", I = " + entry.instructions +
                        ", C = " + entry.cpuCycles +
                        ", I% = " + entry.iPercentage + "%" +
                        ", CPI = " + entry.cpi +
                        ", cur CPI = " + applicationCurrentCPI +
                        ", past CPI = " + applicationPastCPI);*/
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
            double squrDiffToMean = Math.pow(entry.instructions - meanWorkerInstructions, 2);
            tmp += squrDiffToMean;
        }
        double meanDeviation = tmp / numOfWorkers;
        double standardDeviation = Math.sqrt(meanDeviation);
        // imbalance = ( stdev / mean ) * 100 %
        workerInstructionsImbalance = Math.round((standardDeviation / meanWorkerInstructions) * 100);
        //Log.println("HW Instructions imbalance = " + workerInstructionsImbalance + ", workers = " + numOfWorkers);

    }

    public static void clear() {
        data.clear();
        instrTotal = 0;
        cyclesTotal = 0;
        workers.clear();
        numOfWorkers = 0;
        workerInstrTotal = 0;

        mainThreadHWInstructionsPercentage = 0;
        applicationPastCPI = applicationCurrentCPI;
    }

    public static class DataBucket {
        int threadId;
        int tid;
        String threadName;
        long instructions;
        long cpuCycles;
        double iPercentage;
        double cpi;

        public DataBucket(VmThread thread, long instructions, long cpuCycles) {
            this.threadId = thread.id();
            this.tid = thread.tid();
            this.threadName = thread.getName();
            this.instructions = instructions;
            this.cpuCycles = cpuCycles;
            this.cpi = (instructions > 0 && cpuCycles > 0) ? Math.floor(100 * ((double) cpuCycles / instructions) + 0.5) / 100 : 0;
        }

        public void setIPercentage(double p) {
            this.iPercentage = p;
        }
    }
}
