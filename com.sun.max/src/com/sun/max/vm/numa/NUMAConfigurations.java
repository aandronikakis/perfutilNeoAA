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

import com.sun.max.unsafe.Pointer;
import com.sun.max.util.NUMALib;
import com.sun.max.vm.thread.VmThread;
import com.sun.max.vm.thread.VmThreadMap;

import static com.sun.max.vm.thread.VmThreadLocal.ETLA;
import static com.sun.max.vm.thread.VmThreadMap.THREAD_LOCK;

public class NUMAConfigurations {

    /**
     * Single-Node configuration: unconditionally bind all threads to one NUMA Node.
     */
    public static void bindToLocalNode() {
        synchronized (THREAD_LOCK) {
            VmThreadMap.ACTIVE.forAllThreadLocals(null, bindThreadToLocalNode);
        }
    }

    private static final Pointer.Procedure bindThreadToLocalNode = new Pointer.Procedure() {
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            final VmThread thread = VmThread.fromTLA(etla);
            thread.bindToNode(NUMALib.localNode);
        }
    };

    /**
     * X Configuration.
     */
}
