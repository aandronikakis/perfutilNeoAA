/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.vm.code;

import static com.sun.max.vm.code.CodeManager.ReferenceListNode.*;

import com.sun.max.annotate.*;
import com.sun.max.collect.*;
import com.sun.max.lang.*;
import com.sun.max.memory.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.actor.member.MethodKey.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.compiler.target.TargetBundleLayout.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.thread.*;

/**
 * Target machine code cache management.
 *
 * All generated code is position independent as a whole, but target methods may contain direct call references between
 * each other and these must be within 32-bit offsets! Therefore all code regions must be within 32-bit offsets from
 * each other. A concrete implementation of this class must enforce this invariant.
 *
 * @author Bernd Mathiske
 */
public abstract class CodeManager extends RuntimeMemoryRegion {

    /**
     * The default size of a runtime code region.
     */
    protected static final int RUNTIME_CODE_REGION_SIZE = Ints.M;

    /**
     * The default number of runtime code regions.
     */
    protected static final int NUMBER_OF_RUNTIME_CODE_REGIONS = 64;

    /**
     * The maximum size of the code cache.
     */
    public static final int CODE_CACHE_SIZE = NUMBER_OF_RUNTIME_CODE_REGIONS * RUNTIME_CODE_REGION_SIZE;

    /**
     * An array of the code regions.
     */
    @INSPECTED
    protected final CodeRegion[] runtimeCodeRegions;

    /**
     * Linked list for tracking all the references in memory managed by this code manager.
     * This data structure will only be used if {@link Heap#codeReferencesAreGCRoots()} returns true.
     *
     * TODO: Nodes will have to be removed from this list if code eviction is ever implemented.
     *
     * @see ReferenceListNode
     */
    private Pointer referenceList = Pointer.zero();

    /**
     * Get the code region at the specified index.
     *
     * @param index the index into the code regions array
     * @return the code region at the specified index
     */
    protected CodeRegion getRuntimeCodeRegion(int index) {
        return runtimeCodeRegions[index];
    }

    /**
     * Creates a code manager that can manage a given number of number of code regions. Populate the array with empty
     * CodeRegion objects, whose description is "Code-N", where N is the index in the code region array.
     *
     * @param numberOfRuntimeCodeRegions the maximum number of code regions that this code manager should manage
     */
    CodeManager(int numberOfRuntimeCodeRegions) {
        runtimeCodeRegions = new CodeRegion[numberOfRuntimeCodeRegions];
        for (int i = 0; i < numberOfRuntimeCodeRegions; i++) {
            runtimeCodeRegions[i] = new CodeRegion("Code-" + i);
        }
    }

    /**
     * Initialize this code manager.
     */
    void initialize() {
    }

    /**
     * The current code region that is the default for allocating new target methods.
     */
    @INSPECTED
    private CodeRegion currentCodeRegion = Code.bootCodeRegion;

    /**
     * Allocates a new code region with free space, if necessary.
     *
     * @return a new code region with free space
     */
    protected abstract CodeRegion makeFreeCodeRegion();

    /**
     * Describes the layout of a node in {@link CodeManager#referenceList}.
     *
     * @author Doug Simon
     */
    enum ReferenceListNode {
        /**
         * The start address of a sequence of references.
         */
        references,

        /**
         * The number of references in the sequence.
         */
        count,

        /**
         * The address of the next ReferenceListNode.
         */
        next;

        /**
         * The size of a ReferenceListNode.
         */
        static final int SIZE = Word.size() * values().length;

        /**
         * Sets a value for this field in a ReferenceListNode.
         *
         * @param referenceListNode pointer to a node
         * @param value the value to set for this field in {@code referenceListNode}
         */
        void set(Pointer referenceListNode, Word value) {
            referenceListNode.setWord(ordinal(), value);
        }

        /**
         * Gets the value of this field in a ReferenceListNode.
         *
         * @param referenceListNode pointer to a node
         * @return the of this field in {@code referenceListNode}
         */
        Word get(Pointer referenceListNode) {
            return referenceListNode.getWord(ordinal()).asPointer();
        }
    }

    /**
     * Allocates memory in a code region for the code-related arrays of a given target method
     * and {@linkplain TargetMethod#setCodeArrays(byte[], byte[], Object[]) initializes} them.
     *
     * @param targetBundleLayout describes the layout of the arrays in the allocate space
     * @param targetMethod the target method for which the code-related arrays are allocated
     */
    synchronized void allocate(TargetBundleLayout targetBundleLayout, TargetMethod targetMethod) {
        final Size bundleSize = targetBundleLayout.bundleSize();
        int codeLength = targetBundleLayout.length(ArrayField.code);
        int scalarLiteralsLength = targetBundleLayout.length(ArrayField.scalarLiterals);
        int referenceLiteralsLength = targetBundleLayout.length(ArrayField.referenceLiterals);
        final Size allocationSize;

        if (!MaxineVM.isPrototyping() && Heap.codeReferencesAreGCRoots() && referenceLiteralsLength != 0) {
            // Allocate extra space for a ReferenceListNode
            allocationSize = bundleSize.plus(ReferenceListNode.SIZE);
        } else {
            allocationSize = bundleSize;
        }

        Pointer start = currentCodeRegion.allocateTargetMethod(targetMethod, allocationSize);
        if (start.isZero()) {
            currentCodeRegion = makeFreeCodeRegion();
            Code.registerMemoryRegion(currentCodeRegion);
            start = currentCodeRegion.allocateTargetMethod(targetMethod, allocationSize);
            if (start.isZero()) {
                ProgramError.unexpected("could not allocate code");
            }
        }

        byte[] code;
        byte[] scalarLiterals = null;
        Object[] referenceLiterals = null;
        if (MaxineVM.isPrototyping()) {
            code = new byte[codeLength];
            scalarLiterals = scalarLiteralsLength == 0 ? null : new byte[scalarLiteralsLength];
            referenceLiterals = referenceLiteralsLength == 0 ? null : new Object[referenceLiteralsLength];
        } else {
            final Pointer codeCell = targetBundleLayout.cell(start, ArrayField.code);
            code = (byte[]) Cell.plantArray(codeCell, PrimitiveClassActor.BYTE_ARRAY_CLASS_ACTOR.dynamicHub(), codeLength);
            if (scalarLiteralsLength != 0) {
                final Pointer scalarLiteralsCell = targetBundleLayout.cell(start, ArrayField.scalarLiterals);
                scalarLiterals = (byte[]) Cell.plantArray(scalarLiteralsCell, PrimitiveClassActor.BYTE_ARRAY_CLASS_ACTOR.dynamicHub(), scalarLiteralsLength);
            }
            if (referenceLiteralsLength != 0) {
                final Pointer referenceLiteralsCell = targetBundleLayout.cell(start, ArrayField.referenceLiterals);
                referenceLiterals = (Object[]) Cell.plantArray(referenceLiteralsCell, ClassActor.fromJava(Object[].class).dynamicHub(), referenceLiteralsLength);
                if (Heap.codeReferencesAreGCRoots()) {
                    // The ReferenceListNode is immediately after the rest of the bundle
                    final Pointer referenceListNode = start.plus(bundleSize);
                    final Pointer firstReference = targetBundleLayout.firstElementPointer(start, ArrayField.referenceLiterals);
                    ReferenceListNode.references.set(referenceListNode, firstReference);
                    ReferenceListNode.count.set(referenceListNode, Address.fromInt(referenceLiteralsLength));
                    ReferenceListNode.next.set(referenceListNode, referenceList);

                    // Only after the following assignment will the GC see the newly allocated reference literals:
                    referenceList = referenceListNode;
                }
            }
            if (Code.traceAllocation.getValue()) {
                traceAllocation(targetBundleLayout, bundleSize, scalarLiteralsLength, referenceLiteralsLength, start, codeCell);
            }
        }

        final Pointer codeStart = targetBundleLayout.firstElementPointer(start, ArrayField.code);
        targetMethod.setCodeArrays(code, codeStart, scalarLiterals, referenceLiterals);

        if (targetMethod.classMethodActor() != null) {
            methodKeyToTargetMethods.add(new MethodActorKey(targetMethod.classMethodActor()), targetMethod);
        }
    }

    private void traceAllocation(TargetBundleLayout targetBundleLayout, Size bundleSize, int scalarLiteralsLength, int referenceLiteralsLength, Pointer start, Pointer codeCell) {
        final boolean lockDisabledSafepoints = Log.lock();
        Log.printVmThread(VmThread.current(), false);
        Log.print(": Code arrays: code=[");
        Log.print(codeCell);
        Log.print(" - ");
        Log.print(targetBundleLayout.cellEnd(start, ArrayField.code));
        Log.print("], scalarLiterals=");
        if (scalarLiteralsLength > 0) {
            Log.print(targetBundleLayout.cell(start, ArrayField.scalarLiterals));
            Log.print(" - ");
            Log.print(targetBundleLayout.cellEnd(start, ArrayField.scalarLiterals));
            Log.print("], referenceLiterals=");
        } else {
            Log.print("0, referenceLiterals=");
        }
        if (referenceLiteralsLength > 0) {
            Log.print(targetBundleLayout.cell(start, ArrayField.referenceLiterals));
            Log.print(" - ");
            Log.print(targetBundleLayout.cellEnd(start, ArrayField.referenceLiterals));
            if (Heap.codeReferencesAreGCRoots()) {
                Log.print(", referenceListNode=");
                Log.print(start.plus(bundleSize));
                Log.print(" - ");
                Log.print(start.plus(bundleSize).plus(ReferenceListNode.SIZE));
            }
            Log.println("]");
        } else {
            Log.println(0);
        }
        Log.unlock(lockDisabledSafepoints);
    }

    /**
     * Allocates space in this code region for a runtime stub.
     * Note that this method will allocate a byte array of the specified size and inline it in the code
     * region to preserve the invariant that the code region can be scanned linearly as a collection of objects.
     *
     * @param stub an object describing the size of the runtime stub (i.e. the size in bytes to allocate). If
     *            allocation is successful, the address of the memory chunk allocated (i.e. the address of the first
     *            element of the internally allocated byte array) will be accessible through the
     *            {@link MemoryRegion#start()} method of this object.
     * @return true if space was successfully allocated for the runtime stub
     */
    synchronized boolean allocateRuntimeStub(RuntimeStub stub) {
        if (!currentCodeRegion.allocateRuntimeStub(stub)) {
            currentCodeRegion = makeFreeCodeRegion();
            Code.registerMemoryRegion(currentCodeRegion);
            if (!currentCodeRegion.allocateRuntimeStub(stub)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Looks up the runtime code region in which the specified code pointer lies. This lookup
     * does not include the boot code region.
     *
     * @param codePointer the code pointer
     * @return a reference to the code region that contains the specified code pointer, if one exists; {@code null} if
     *         the code pointer lies outside of all runtime code regions
     */
    protected abstract CodeRegion codePointerToRuntimeCodeRegion(Address codePointer);

    /**
     * Looks up the code region in which the specified code pointer lies. This lookup includes
     * the boot code region.
     *
     * @param codePointer the code pointer
     * @return a reference to the code region that contains the specified code pointer, if one exists; {@code null} if
     *         the code pointer lies outside of all code regions
     */
    CodeRegion codePointerToCodeRegion(Address codePointer) {
        if (Code.bootCodeRegion.contains(codePointer)) {
            return Code.bootCodeRegion;
        }
        if (!contains(codePointer)) {
            return null;
        }
        return codePointerToRuntimeCodeRegion(codePointer);
    }

    /**
     * Looks up the target method that contains the specified code pointer.
     *
     * @param codePointer the code pointer to lookup
     * @return the target method that contains the specified code pointer, if it exists; {@code null}
     * if no target method contains the specified code pointer
     */
    TargetMethod codePointerToTargetMethod(Address codePointer) {
        final CodeRegion codeRegion = codePointerToCodeRegion(codePointer);
        if (codeRegion != null) {
            return codeRegion.findTargetMethod(codePointer);
        }
        return null;
    }

    /**
     * Looks up the runtime stub that contains the specified code pointer.
     *
     * @param codePointer the code pointer to lookup
     * @return the runtime stub that contains the specified code pointer, if it exists;
     *         {@code null} otherwise
     */
    RuntimeStub codePointerToRuntimeStub(Address codePointer) {
        final CodeRegion codeRegion = codePointerToCodeRegion(codePointer);
        if (codeRegion != null) {
            return codeRegion.findRuntimeStub(codePointer);
        }
        return null;
    }

    /**
     * A mapping from method keys to target methods.
     */
    private final ArrayBag<MethodKey, TargetMethod> methodKeyToTargetMethods = new ArrayBag<MethodKey, TargetMethod>(TargetMethod.class, ArrayBag.MapType.HASHED);

    /**
     * Finds any target methods that match the specified method key.
     *
     * @param methodKey the method key to lookup
     * @return an array of target methods that match the specified method key
     */
    synchronized TargetMethod[] methodKeyToTargetMethods(MethodKey methodKey) {
        return methodKeyToTargetMethods.get(methodKey);
    }

    /**
     * Visits all the references in memory managed by this code manager except for the boot code region.
     *
     * @param pointerIndexVisitor the visitor that is notified of each reference in the code cache
     */
    public void visitReferences(PointerIndexVisitor pointerIndexVisitor) {
        if (!Heap.codeReferencesAreGCRoots()) {
            FatalError.unexpected("References in code are only tracked if Heap.codeReferencesAreGCRoots() returns true");
        }

        if (Heap.traceRootScanning()) {
            Log.println("Scanning references in code:");
        }

        Pointer referenceListNode = referenceList;
        while (!referenceListNode.isZero()) {
            final Pointer firstReference = references.get(referenceListNode).asPointer();
            final int c = count.get(referenceListNode).asAddress().toInt();
            for (int i = 0; i < c; i++) {
                if (Heap.traceRootScanning()) {
                    final Pointer address = firstReference.plus(i * Word.size());
                    final Address value = address.readWord(0).asAddress();
                    if (!value.isZero()) {
                        Log.print("    Slot: address=");
                        Log.print(address);
                        Log.print(", value=");
                        Log.println(address.readWord(0));
                    }
                }
                pointerIndexVisitor.visit(firstReference, i);
            }
            referenceListNode = next.get(referenceListNode).asPointer();
        }
    }

    public Size getSize() {
        Size size = Size.zero();
        for (int i = 0; i < runtimeCodeRegions.length; i++) {
            size = size.plus(runtimeCodeRegions[i].size());
        }
        return size;
    }
}
