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

public class AwarenessThread extends Thread {

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
                final int thisPeriod = 100;
                // sleep in msec
                Thread.sleep(thisPeriod);
                final long now = System.currentTimeMillis();
                Log.println("Hello from NUMA awareness thread - " + now + " ms");
            } catch (InterruptedException ex) {
            }
        }
    }
}
