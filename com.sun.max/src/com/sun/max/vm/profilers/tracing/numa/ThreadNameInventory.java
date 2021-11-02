/*
 * Copyright (c) 2021, APT Group, Department of Computer Science,
 * The University of Manchester. All rights reserved.
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
package com.sun.max.vm.profilers.tracing.numa;

import com.sun.max.unsafe.Address;
import com.sun.max.unsafe.Pointer;
import com.sun.max.vm.runtime.FatalError;
import com.sun.max.vm.thread.VmThread;
import com.sun.max.vm.thread.VmThreadLocal;

/**
 * {@link com.sun.max.vm.MaxineVM} assigns its Java {@link com.sun.max.vm.thread.VmThread}s to native pthreads.
 * When a Java thread starts/stops more than once, it might be re-assigned to different pthreads.
 * Consequently, there is no way to uniquely identify a Java thread by its {@link VmThread#id()}
 * because this value refers to the native pthread id.
 *
 * {@link ThreadNameInventory} helps in uniquely identifying a Java Application Thread to support
 * object access profiling per thread in the context of {@link NUMAProfiler}.
 *
 * {@link ThreadNameInventory} stores all unique Java thread names in a {@link String} array.
 * The {@link ThreadNameInventory#index} as the String array's index is the id needed to uniquely identify each thread.
 *
 * Accesses in objects that had been allocated before profiling being enabled (and consequently NUMAProfiler is not aware of the allocator thread)
 * are highlighted as "Unknown" (see {@link ThreadNameInventory#inventory}[0]).
 *
 * See also:    {@link com.sun.max.vm.monitor.modal.modehandlers.lightweight.LightweightLockword}
 *              {@link VmThreadLocal#THREAD_NAME_KEY}
 */
public class ThreadNameInventory {

    private static int index;
    private static String[] inventory;

    public ThreadNameInventory() {
        inventory = new String[256];
        inventory[0] = "Unknown";
        index = 1;
    }

    public int inventoryContainsValue(String value) {
        for (int i = 0; i < index; i++) {
            if (inventory[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    public static int getIndex() {
        return index;
    }

    public int addThreadName(Pointer etla, String threadName) {
        if (index < 256) {
            int key = inventoryContainsValue(threadName);
            if (key == 0) {
                // does not exist, write THREAD_NAME_KEY where current index points, add name to inventory, return index to update threadNameKey in ProfilingArtifact
                VmThreadLocal.THREAD_NAME_KEY.store(etla, Address.fromInt(index));
                inventory[index] = threadName;
                index++;
            } else {
                // already exists, write THREAD_NAME_KEY and return key to update threadNameKey in ProfilingArtifact
                VmThreadLocal.THREAD_NAME_KEY.store(etla, Address.fromInt(key));
                return key;
            }
        } else {
            // Profiling supports up to 256 threads because allocator thread id need to be stored in object's misc word.
            // The available bits for allocator thread id are only 8.
            FatalError.unexpected("Profiling threads exceed the 1 << 8 limit.");
        }
        return index - 1;
    }

    public static String getByIndex(int index) {
        return inventory[index];
    }
}
