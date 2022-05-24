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
import com.sun.max.vm.thread.VmThreadMap;

import static com.sun.max.vm.MaxineVM.NUMALog;


public class NUMAState {

    final static int NUM_OF_TOTAL_SYSTEM_CORES = Runtime.getRuntime().availableProcessors();
    // TODO: Parametrize
    final static int NUM_OF_NUMA_NODES = 2;
    final static int NUM_OF_SINGLE_NODE_CORES = NUM_OF_TOTAL_SYSTEM_CORES / NUM_OF_NUMA_NODES;

    final static boolean logState = false;

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
            // other
            setCurrentState(STATE.OTHER);
        }
        //Log.println("Live thread count = " + VmThreadMap.getLiveTheadCount());
        //Log.println("State = " + fsmState + " in checkState()");
        //PerfUtil.perfGroupSetSpecificThreadSpecificCore(INSTRUCTIONS_SINGLE, thread.id, -1);
        //enableHWCounters();
        //Thread.sleep(0);
        //} catch (InterruptedException ex) {

        //}
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
        },
        TLP_BOUND {
            @Override
            public void act() {
                if (NUMALog || logState) {
                    Log.println("Act as TLP-Bound");
                }
                NUMAConfigurations.setLocalNodeAffinityForAllThreads();
            }
        },
        EMBARRASSINGLY_IMBALANCED {
            @Override
            public void act() {
                if (NUMALog || logState) {
                    Log.println("Act as Embarrassingly-Imbalanced");
                }
                NUMAConfigurations.setLocalNodeAffinityForAllThreads();
            }
        },
        OTHER {
            @Override
            public void act() {
                if (NUMALog || logState) {
                    Log.println("Act as Other");
                }
            }
        };

        public abstract void act();
    }

}
