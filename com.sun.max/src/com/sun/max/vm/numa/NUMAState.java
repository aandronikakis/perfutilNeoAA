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

import com.sun.max.vm.Log;
import com.sun.max.vm.thread.VmThread;
import com.sun.max.vm.thread.VmThreadMap;

import static com.sun.max.vm.MaxineVM.NUMALog;


public class NUMAState {

    final static int NUM_OF_TOTAL_SYSTEM_CORES = Runtime.getRuntime().availableProcessors();
    // TODO: Parametrize
    final static int NUM_OF_NUMA_NODES = 2;
    final static int NUM_OF_SINGLE_NODE_CORES = NUM_OF_TOTAL_SYSTEM_CORES / NUM_OF_NUMA_NODES;

    final static boolean logState = false;

    public static double singleNodeCPI = 0;
    public static double dualNodeCPI = 0;
    static double cpiMargin = 0;
    final static double marginFactor = 0.15;

    /**
     * {@code fsmState} holds the previous ([0]) and current ([1]) state of the fsm.
     * start from single node by default.
     * no need to set new affinity, the jvm is already bound to node 0.
     */
    private static STATE[] fsmState = new STATE[2];
    static {
        // previous
        fsmState[0] = STATE.SINGLE_NODE;
        // current
        fsmState[1] = STATE.SINGLE_NODE;
    }

    public static STATE[] getFsmState() {
        return fsmState;
    }

    private static void setCurrentState(STATE newState) {
        fsmState[1] = newState;
    }

    private static STATE getCurrentState() {
        return fsmState[1];
    }

    private static void setPreviousState(STATE state) {
        fsmState[0] = state;
    }

    private static STATE getPreviousState() {
        return fsmState[0];
    }

    public static void fsmTick() {
        determineCurrentState();
        act();
        //ready for next tick
        setPreviousState(getCurrentState());
        // clear profiling data
        ProfilingData.clear();
    }

    /**
     * Determine in which state we are.
     */
    private static void determineCurrentState() {
        // criteria and cases
        if (ProfilingData.mainThreadHWInstructionsPercentage > 80) {
            // fop, jython, luindex, mnemonics, dotty, scala-doku, scala-kmeans
            setCurrentState(STATE.SINGLE_NODE);
        } else if (ProfilingData.numOfWorkers <= NUM_OF_SINGLE_NODE_CORES) {
            // als, chi-square, gauss-mix, movie-lens, neo4j-analytics, log-regression
            setCurrentState(STATE.TLP_BOUND);
        } else if (ProfilingData.workerInstructionsImbalance > 90) {
            // par-mnemonics, rx-scrabble
            setCurrentState(STATE.EMBARRASSINGLY_IMBALANCED);
        } else {
            // parallel (imbalanced & explicitly parallel)
            if (singleNodeCPI == 0) {
                // parallel for the first time
                // run again on single node to measure cpi
                setCurrentState(STATE.PARALLEL_ON_SINGLE_NODE);
            } else {
                // parallel for the second time OR we know that scaling is beneficial
                cpiMargin = singleNodeCPI * marginFactor;
                if (dualNodeCPI == 0 || dualNodeCPI <= (singleNodeCPI + cpiMargin)) {
                    // free to scale
                    setCurrentState(STATE.PARALLEL_ON_ALL_NODES);
                    AwarenessThread.sleepMore();
                } else {
                    // come back to single node
                    setCurrentState(STATE.PARALLEL_ON_SINGLE_NODE);
                    AwarenessThread.sleepMore();
                }
            }
        }
        //Log.println("Live thread count = " + VmThreadMap.getLiveTheadCount());
        //Log.println("State = " + fsmState + " in checkState()");
        //PerfUtil.perfGroupSetSpecificThreadSpecificCore(INSTRUCTIONS_SINGLE, thread.id, -1);
        //enableHWCounters();
        //Thread.sleep(0);
        //} catch (InterruptedException ex) {

        //}
    }

    public static void updateCPI(double newCPI) {
        fsmState[1].updateCPI(newCPI);
    }

    private static boolean isChanged() {
        return fsmState[1] != fsmState[0];
    }

    private static void act() {
        if (isChanged()) {
            fsmState[1].act();
        } else {
            if (NUMALog || logState) {
                Log.println("Do nothing, alredy on: " + fsmState[1]);
            }
        }
    }

    enum STATE {
        SINGLE_NODE {
            @Override
            public void act() {
                if (NUMALog || logState) {
                    Log.println("Act as Single-threaded");
                }
                NUMAConfigurations.setLocalNodeAffinityForAllThreads();
            }

            @Override
            public void updateCPI(double cpi) { }
        },
        TLP_BOUND {
            @Override
            public void act() {
                if (NUMALog || logState) {
                    Log.println("Act as TLP-Bound");
                }
                NUMAConfigurations.setLocalNodeAffinityForAllThreads();
            }

            @Override
            public void updateCPI(double cpi) { }
        },
        EMBARRASSINGLY_IMBALANCED {
            @Override
            public void act() {
                if (NUMALog || logState) {
                    Log.println("Act as Embarrassingly-Imbalanced");
                }
                NUMAConfigurations.setLocalNodeAffinityForAllThreads();
            }

            @Override
            public void updateCPI(double cpi) { }
        },
        PARALLEL_ON_SINGLE_NODE {
            @Override
            public void act() {
                if (NUMALog || logState) {
                    Log.println("Act as Parallel-On-Single-Node");
                }
                NUMAConfigurations.setLocalNodeAffinityForAllThreads();
            }

            @Override
            public void updateCPI(double cpi) {
                singleNodeCPI = cpi;
            }
        },
        PARALLEL_ON_ALL_NODES {
            @Override
            public void act() {
                if (NUMALog || logState) {
                    Log.println("Act as Parallel-On-All-Nodes");
                }
                NUMAConfigurations.setFreeNodeAffinityForAllThreads();
            }

            @Override
            public void updateCPI(double cpi) {
                dualNodeCPI = cpi;
            }
        };

        public abstract void act();
        public abstract void updateCPI(double cpi);
    }

}
