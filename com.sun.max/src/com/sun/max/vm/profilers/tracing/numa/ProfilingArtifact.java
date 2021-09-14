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

import com.sun.max.annotate.INTRINSIC;

import static com.sun.max.vm.intrinsics.MaxineIntrinsicIDs.UNSAFE_CAST;

/**
 * This class represents the object allocation profiling data structure.
 * It can be either a {@link RecordBuffer} or a {@link AllocationsCounter}.
 */
public abstract class ProfilingArtifact {

    int threadId;
    String simpleName;

    @INTRINSIC(UNSAFE_CAST)
    public static native ProfilingArtifact asArtifact(Object object);

    abstract int getThreadId();
    abstract String getSimpleName();
    abstract void print(int cycle, int isAllocations);
    abstract void deallocateArtifact();
}
