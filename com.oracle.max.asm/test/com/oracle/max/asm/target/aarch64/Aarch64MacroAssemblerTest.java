/*
 * Copyright (c) 2020, APT Group, School of Computer Science,
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
package com.oracle.max.asm.target.aarch64;

import static org.junit.Assert.assertEquals;
import static com.oracle.max.asm.target.aarch64.Aarch64AssemblerTest.assemble;

import org.junit.Before;
import org.junit.Test;

import com.oracle.max.cri.intrinsics.MemoryBarriers;
import com.sun.cri.ci.CiTarget;

public class Aarch64MacroAssemblerTest {

    private final Aarch64MacroAssembler asm;

    public Aarch64MacroAssemblerTest() {
        final CiTarget aarch64 = new CiTarget(new Aarch64(), true, 8, 16, 4096, 0, false, false, false, true);
        asm = new Aarch64MacroAssembler(aarch64, null);
    }

    @Before
    public void initialization() {
        asm.codeBuffer.reset();
    }

    @Test
    public void membarPreVolatileRead() {
        asm.codeBuffer.reset();
        asm.membar(MemoryBarriers.JMM_PRE_VOLATILE_READ);
        assertEquals(0, asm.codeBuffer.position());
    }

    @Test
    public void membarPostVolatileRead() {
        asm.codeBuffer.reset();
        asm.membar(MemoryBarriers.JMM_POST_VOLATILE_READ);
        assertEquals(assemble("dmb ishld"), asm.codeBuffer.getInt(0));
    }

    @Test
    public void membarPreVolatileWrite() {
        asm.codeBuffer.reset();
        asm.membar(MemoryBarriers.JMM_PRE_VOLATILE_WRITE);
        assertEquals(assemble("dmb ish"), asm.codeBuffer.getInt(0));
    }

    @Test
    public void membarPostVolatileWrite() {
        asm.codeBuffer.reset();
        asm.membar(MemoryBarriers.JMM_POST_VOLATILE_WRITE);
        assertEquals(assemble("dmb ish"), asm.codeBuffer.getInt(0));
    }

    @Test
    public void membarFullFence() {
        asm.codeBuffer.reset();
        asm.membar(MemoryBarriers.FULL_FENCE);
        assertEquals(assemble("dmb ish"), asm.codeBuffer.getInt(0));
    }

    @Test
    public void membarLoadFence() {
        asm.codeBuffer.reset();
        asm.membar(MemoryBarriers.LOAD_FENCE);
        assertEquals(assemble("dmb ishld"), asm.codeBuffer.getInt(0));
    }

    @Test
    public void membarStoreFence() {
        asm.codeBuffer.reset();
        asm.membar(MemoryBarriers.STORE_FENCE);
        assertEquals(assemble("dmb ish"), asm.codeBuffer.getInt(0));
    }

    @Test
    public void membarStoreLoad() {
        asm.codeBuffer.reset();
        asm.membar(MemoryBarriers.STORE_LOAD);
        assertEquals(assemble("dmb ish"), asm.codeBuffer.getInt(0));
    }

    @Test
    public void membarStoreStore() {
        asm.codeBuffer.reset();
        asm.membar(MemoryBarriers.STORE_STORE);
        assertEquals(assemble("dmb ishst"), asm.codeBuffer.getInt(0));
    }

    @Test
    public void membarLoadStore() {
        asm.codeBuffer.reset();
        asm.membar(MemoryBarriers.LOAD_STORE);
        assertEquals(assemble("dmb ishld"), asm.codeBuffer.getInt(0));
    }

    @Test
    public void membarLoadLoad() {
        asm.codeBuffer.reset();
        asm.membar(MemoryBarriers.LOAD_LOAD);
        assertEquals(assemble("dmb ishld"), asm.codeBuffer.getInt(0));
    }
}
