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

import com.sun.max.vm.VMOptions;
import com.sun.max.vm.thread.VmThread;

public class AwarenessThread extends Thread {

    private static int NUMAOptInterval = 200;
    final static int NUMAOptIncrementStep = 200;

    private static boolean NUMAOptStabilize = false;

    static {
        VMOptions.addFieldOption("-XX:", "NUMAOptInterval", AwarenessThread.class, "Enable NUMA Awareness every n msec.");
        VMOptions.addFieldOption("-XX:", "NUMAOptStabilize", AwarenessThread.class, "Stabilize.");
    }

    /**
     * The NUMA awareness thread itself.
     */
    protected VmThread awarenessThread;


    /**
     * Constructor.
     *
     */
    public AwarenessThread() {
        super(VmThread.systemThreadGroup, "NUMA-Awareness-Thread");
        setDaemon(true);

        this.start();
    }

    @Override
    public void run() {
        // enable HW counters for main thread
        HWCountersHandler.enableHWCountersMainThread();
        while (true) {
            try {
                // sleep for M msec until next measurement period
                Thread.sleep(NUMAOptInterval);
                // wake up and collect measurements
                HWCountersHandler.readHWCounters();
                // process collected data
                ProfilingData.process();
                // act accordingly
                NUMAState.fsmTick();
            } catch (InterruptedException ex) {
            }
        }
    }

    public static void sleepMore() {
        if (NUMAOptStabilize) {
            NUMAOptInterval += NUMAOptIncrementStep;
        }
    }
}
