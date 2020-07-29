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
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package uk.ac.manchester.tests.jdk8.java.lang;

public class DefaultMethod {

    public interface If0 {
        public void sayHello();

        default void sayOne() {
            System.out.println("dos");
        }
    }

    public interface If1 extends If0 {
        default void sayHello() {
            System.out.println("Hola!");
        }

        default void sayOne() {
            System.out.println("uno");
        }
    }

    public class TestMe implements If0,If1 {
    }

    public class TestMeMore implements If1,If0 {
    }
  
    public static void main(String [] args) {
        TestMe t = new DefaultMethod().new TestMe();
        TestMeMore t2 = new DefaultMethod().new TestMeMore();
        t.sayHello();
        t.sayOne();
        t2.sayHello();
        t2.sayOne();
    }
}
