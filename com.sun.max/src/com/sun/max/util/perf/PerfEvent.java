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
package com.sun.max.util.perf;

import com.sun.max.annotate.C_FUNCTION;
import com.sun.max.unsafe.Pointer;
import com.sun.max.vm.layout.Layout;
import com.sun.max.vm.reference.Reference;

import com.sun.max.util.perf.PerfUtil.*;


public class PerfEvent {

    MAXINE_PERF_EVENT_GROUP_ID groupId;
    MAXINE_PERF_EVENT_ID eventId;
    MAXINE_PERF_EVENT_ID groupLeaderId;
    long value;

    public PerfEvent(MAXINE_PERF_EVENT_GROUP_ID group, MAXINE_PERF_EVENT_ID eventId, PERF_TYPE_ID type, int config, MAXINE_PERF_EVENT_ID groupLeaderId) {
        this.groupId = group;
        this.eventId = eventId;
        this.groupLeaderId = groupLeaderId;
        perfEventCreate(eventId.value, type.value, config, groupLeaderId.value);
    }

    public void enable() {
        perfEventEnable(eventId.value);
    }

    public void disable() {
        perfEventDisable(eventId.value);
    }

    public void reset() {
        perfEventReset(eventId.value);
    }

    public void read(long[] values) {
        int dataOffset = Layout.longArrayLayout().getElementOffsetFromOrigin(0).toInt();
        perfEventRead(eventId.value, Reference.fromJava(values).toOrigin().plus(dataOffset));
    }

    @C_FUNCTION
    public static native void perfEventCreate(int id, int type, int config, int groupLeaderId);

    @C_FUNCTION
    public static native void perfEventEnable(int id);

    @C_FUNCTION
    public static native void perfEventDisable(int id);

    @C_FUNCTION
    public static native void perfEventReset(int id);

    @C_FUNCTION
    public static native void perfEventRead(int id, Pointer values);

}
