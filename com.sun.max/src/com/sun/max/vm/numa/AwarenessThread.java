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

import com.sun.max.vm.thread.VmThread;

public class AwarenessThread extends Thread {

    final int sleepPeriod = 100;
    final int HWCountersMeasurementPeriod = 100;

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
        while (true) {
            try {
                // start measuring
                HWCountersHandler.enableHWCounters();
                // measure for N msec
                Thread.sleep(HWCountersMeasurementPeriod);
                // stop and collect measurements
                HWCountersHandler.readNDisableHWCounters();
                // act accordingly
                NUMAState.fsmTick();
                // sleep for M msec until next measurement period
                Thread.sleep(sleepPeriod);
            } catch (InterruptedException ex) {
            }
        }
    }
}
