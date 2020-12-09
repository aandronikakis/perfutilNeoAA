/*
 * Copyright (c) 2018, APT Group, School of Computer Science,
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
package uk.ac.manchester.tests.jdk8.java.lang;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DefaultNestedMethodTest2 {

    private interface I0 {
        int get3();
    }

    private interface I1 extends I0{
        int get1();

        default int get3() {
            return 2;
        }
    }

    private interface I2 extends I1 {
        @Override
        default int get3() {
            return 3;
        }

        @Override
        default int get1() {
            return 1;
        }
    }

    /**
     * Tests:.
     *  Miranda -> default
     *  default super -> default sub
     */
    private class C implements I0, I1, I2 {
    }

    /**
     *  Note the interface order, tests as above but also ensures
     *  that the sub interface has priority over its super.
     */
    private class D implements I0, I2, I1 {
    }


    @Test
    public void defaultMethodInvocation() {
        C temp = new C();
        D d = new D();
        assertEquals(temp.get1(), 1);
        assertEquals(temp.get3(), 3);
        assertEquals(d.get1(), 1);
        assertEquals(d.get3(), 3);
    }
}
