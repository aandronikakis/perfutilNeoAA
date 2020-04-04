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

package com.sun.max.util;

import com.sun.max.annotate.C_FUNCTION;
import com.sun.max.unsafe.CString;
import com.sun.max.unsafe.Pointer;
import com.sun.max.vm.Log;
import com.sun.max.vm.layout.Layout;
import com.sun.max.vm.reference.Reference;


public class PerfEvent {

    int id;
    String name;
    long[] valuesBuffer;
    long value;
    static final int valuesBufferSize = 100;

    public PerfEvent(int id, String name, int type, int config) {
        this.id = id;
        this.name = name;
        this.valuesBuffer = new long[valuesBufferSize];
        perfEventCreate(id, CString.utf8FromJava(name), type, config);

        valuesBuffer[0] = 0;
        valuesBuffer[1] = 1;
        valuesBuffer[2] = 2;
        valuesBuffer[3] = 3;
        valuesBuffer[4] = 4;
        valuesBuffer[5] = 5;
    }

    public void enable() {
        perfEventEnable(id);
    }

    public void disable() {
        perfEventDisable(id);
    }

    public void reset() {
        perfEventReset(id);
    }

    public void read() {
        //int dataOffset = Layout.longArrayLayout().getElementOffsetFromOrigin(0).toInt();
        //perfEventRead(id, Reference.fromJava(valuesBuffer).toOrigin().plus(dataOffset));
        value = perfEventRead(id);
        Log.println(value);
        /*for(int i = 0; i < valuesBufferSize; i++) {
            if (valuesBuffer[i] != 0) {
                Log.print("Values [");
                Log.print(i);
                Log.print("] = ");
                Log.println(valuesBuffer[i]);
            }
        }*/
    }

    @C_FUNCTION
    public static native void perfEventCreate(int id, Pointer eventName, int type, int config);

    @C_FUNCTION
    public static native void perfEventEnable(int id);

    @C_FUNCTION
    public static native void perfEventDisable(int id);

    @C_FUNCTION
    public static native void perfEventReset(int id);

    @C_FUNCTION
    public static native long perfEventRead(int id);
    //public static native void perfEventRead(int id, long values);
    //public static native void perfEventRead(int id, Pointer values);
    // long[] values

}
