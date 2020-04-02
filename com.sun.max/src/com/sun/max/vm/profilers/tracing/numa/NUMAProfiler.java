/*
 * Copyright (c) 2020, APT Group, Department of Computer Science,
 * School of Engineering, The University of Manchester. All rights reserved.
 * Copyright (c) 2018-2019, APT Group, School of Computer Science,
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

import static com.sun.max.vm.MaxineVM.*;
import static com.sun.max.vm.intrinsics.MaxineIntrinsicIDs.*;
import static com.sun.max.vm.thread.VmThreadLocal.*;

import com.sun.max.annotate.*;
import com.sun.max.memory.*;
import com.sun.max.program.*;
import com.sun.max.unsafe.*;
import com.sun.max.util.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.heap.*;
import com.sun.max.vm.heap.sequential.semiSpace.*;
import com.sun.max.vm.intrinsics.*;
import com.sun.max.vm.jdk.*;
import com.sun.max.vm.reference.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.thread.*;

import java.util.Arrays;

public class NUMAProfiler {

    /**
     * Values that {@link VmThreadLocal#PROFILER_STATE} can take.
     */
    public enum PROFILING_STATE {
        /**
         * Indicates that profiling is disabled on this thread.
         */
        DISABLED(0),
        /**
         * Indicates that profiling is enabled on this thread.
         */
        ENABLED(1),
        /**
         * Indicates that profiling is enabled on this thread, and the thread is currently profiling a memory access.
         * This state is used to avoid nested profiling.
         */
        ONGOING(2);

        public int getValue() {
            return value;
        }

        private final int value;

        PROFILING_STATE(int i) {
            value = i;
        }
    }

    public static int flareObjectCounter = 0;

    public static int start_counter = 0;
    public static int end_counter = 0;

    @C_FUNCTION
    static native void numaProfiler_lock();

    @C_FUNCTION
    static native void numaProfiler_unlock();

    /**
     * The Buffer who keeps track of the physical NUMA node of any virtual memory page allocated for the JVM Heap.
     */
    private static VirtualPagesBuffer heapPages;

    @SuppressWarnings("unused")
    private static boolean NUMAProfilerVerbose;
    @SuppressWarnings("unused")
    private static int     NUMAProfilerBufferSize;
    @SuppressWarnings("unused")
    private static boolean NUMAProfilerDebug;
    @SuppressWarnings("unused")
    private static boolean NUMAProfilerIncludeFinalization;
    @SuppressWarnings("unused")
    public static boolean NUMAProfilerIsolateDominantThread;

    /**
     * PROFILING POLICY 1: Explicit GC Driven
     * Trigger Event: An Application's System.gc() call.
     * The following two variables are used to help us ignore the application's
     * warmup iterations in order to profile only the effective part. The {@code iteration}
     * is calculated by the number of System.gc() calls. The MaxineVM.profileThatObject()
     * method returns false as long as the iteration counter is below the NUMAProfilerExplicitGCThreshold, which
     * is given by the user, ignoring any object allocation up to that point. Its default value
     * has been chosen as -1 because by 0 it means that we want to profile everything.
     */
    @SuppressWarnings("unused")
    public static int NUMAProfilerExplicitGCThreshold = -1;

    /**
     * The count of System.gc() calls.
     */
    public static  int iteration = 0;

    /**
     * The count of profiling cycles.
     */
    public static int profilingCycle;

    /**
     * This field stores the GC type information (implicit or explicit).
     * By default is false. It is set to true, when an explicit GC is triggered
     * at JDK_java_lang_Runtime class. It is then accessed by the NUMAProfiler at the post-gc phase.
     * If an explicit gc is found, the explicit gc counter is incremented.
     * This way, the Explicit-GC Policy is, timing-wise, more accurate since the NUMAProfiler
     * is switched on exactly at the point when the last warm-up iteration has been finished
     * and therefore, profiles only what it should.
     */
    public static boolean isExplicitGC = false;

    /**
     * PROFILING POLICY 2: Flare-Object Driven
     * Trigger Event: A Flare-Object Allocation by the Application.
     * The following variable is used to help us ignore the application's
     * warmup iterations in order to profile only the effective part. The MaxineVM.profileThatObject()
     * method returns false as long as the NUMAProfilerFlareObject counter is below the NUMAProfilerFlareAllocationThreshold,
     * which is given by the user, ignoring any object allocation up to that point.
     * The NUMAProfilerFlareProfileWindow (default 1) indicates how many Flare Objects we need
     * to allocate before we stop the profiling.
     */
    public static  String NUMAProfilerFlareAllocationThresholds = "0";
    public static int[]  flareAllocationThresholds;
    @SuppressWarnings("FieldCanBeLocal")
    private static String NUMAProfilerFlareObjectStart          = "NUMAProfilerFlareObjectStart";
    @SuppressWarnings("FieldCanBeLocal")
    private static String NUMAProfilerFlareObjectEnd            = "NUMAProfilerFlareObjectEnd";

    /**
     * Buffers that keep the threadId of the threads that started profiling due to reaching the flare object
     * allocation threshold.
     */
    public static int[] flareObjectThreadIdBuffer;

    /**
     * A boolean variable, to show when the profiler is ON for the Flare Object Policy.
     */
    public static boolean enableFlareObjectProfiler = false;

    private static final int MINIMUMBUFFERSIZE = 500000;

    public static int survivorBufferSize = MINIMUMBUFFERSIZE;
    public static int allocatorBufferSize = MINIMUMBUFFERSIZE;

    /**
     * The underlying hardware configuration.
     */
    static NUMALib numaConfig;

    /**
     * This String array holds the counters' names. Those names are passed to each VmThreadLocal instance initialization.
     */
    public static String[] objectAccessCounterNames;

    @CONSTANT_WHEN_NOT_ZERO
    private static Address heapStart;

    public final int memoryPageSize;

    /**
     * An enum that maps each Object Access Counter name with a {@link VmThreadLocal#profilingCounters} index.
     */
    public enum ACCESS_COUNTER {
        LOCAL_TUPLE_WRITE(0), INTERNODE_TUPLE_WRITE(1), INTERBLADE_TUPLE_WRITE(2),
        LOCAL_ARRAY_WRITE(3), INTERNODE_ARRAY_WRITE(4), INTERBLADE_ARRAY_WRITE(5),
        LOCAL_TUPLE_READ(6), INTERNODE_TUPLE_READ(7), INTERBLADE_TUPLE_READ(8),
        LOCAL_ARRAY_READ(9), INTERNODE_ARRAY_READ(10), INTERBLADE_ARRAY_READ(11);

        private final int value;

        ACCESS_COUNTER(int i) {
            value = i;
        }
    }

    /**
     * An enum that maps the different {@link RecordBuffer} buffers.
     */
    public enum RECORD_BUFFER {
        ALLOCATIONS_BUFFER(0),
        SURVIVORS_1_BUFFER(1),
        SURVIVORS_2_BUFFER(2);

        public final int value;

        RECORD_BUFFER(int i) {
            value = i;
        }
    }

    // The options a user can pass to the NUMA Profiler.
    static {
        VMOptions.addFieldOption("-XX:", "NUMAProfilerVerbose", NUMAProfiler.class, "Verbose numa profiler output. (default: false)", MaxineVM.Phase.PRISTINE);
        VMOptions.addFieldOption("-XX:", "NUMAProfilerBufferSize", NUMAProfiler.class, "NUMAProfiler's Buffer Size.");
        VMOptions.addFieldOption("-XX:", "NUMAProfilerExplicitGCThreshold", NUMAProfiler.class,
                "The number of the Explicit GCs to be performed before the NUMAProfiler starts recording. " +
                "It cannot be used in combination with \"NUMAProfilerFlareAllocationThresholds\". (default: -1)");
        VMOptions.addFieldOption("-XX:", "NUMAProfilerFlareObjectStart", NUMAProfiler.class, "The Class of the Object to be sought after by the NUMAProfiler to start the profiling process. (default: 'AllocationProfilerFlareObject')");
        VMOptions.addFieldOption("-XX:", "NUMAProfilerFlareObjectEnd", NUMAProfiler.class, "The Class of the Object to be sought after by the NUMAProfiler to stop the profiling process. (default: 'AllocationProfilerFlareObject')");
        VMOptions.addFieldOption("-XX:", "NUMAProfilerFlareAllocationThresholds", NUMAProfiler.class,
                "The number of the Flare start objects to be allocated before the NUMAProfiler starts recording. " +
                "Multiple \"windows\" may be profiled by providing a comma separated list, " +
                "e.g. \"100,200,500\" will start profiling after the 100th, the 200th, and the 500th Flare object " +
                "allocation till the thread that started the profiling allocates a Flare end object. It cannot be used in combination with \"NUMAProfilerExplicitGCThreshold\". (default: \"0\")");
        VMOptions.addFieldOption("-XX:", "NUMAProfilerDebug", NUMAProfiler.class, "Print information to help in NUMAProfiler's Validation. (default: false)", MaxineVM.Phase.PRISTINE);
        VMOptions.addFieldOption("-XX:", "NUMAProfilerIncludeFinalization", NUMAProfiler.class, "Include memory accesses performed due to Finalization. (default: false)", MaxineVM.Phase.PRISTINE);
        VMOptions.addFieldOption("-XX:", "NUMAProfilerIsolateDominantThread", NUMAProfiler.class, "Isolate the dominant thread object allocations (default: false)", MaxineVM.Phase.PRISTINE);

        objectAccessCounterNames = new String[]{
            "LOCAL_TUPLE_WRITES", "INTERNODE_TUPLE_WRITES", "INTERBLADE_TUPLE_WRITES",
            "LOCAL_ARRAY_WRITES", "INTERNODE_ARRAY_WRITES", "INTERBLADE_ARRAY_WRITES",
            "LOCAL_TUPLE_READS", "INTERNODE_TUPLE_READS", "INTERBLADE_TUPLE_READS",
            "LOCAL_ARRAY_READS", "INTERNODE_ARRAY_READS", "INTERBLADE_ARRAY_READS"
        };
    }

    public NUMAProfiler() {
        assert NUMALib.numalib_available() != -1 : "NUMAProfiler cannot be run without NUMA support";

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): NUMAProfiler Initialization.");
        }

        splitStringtoSortedIntegers();

        if (NUMAProfilerBufferSize != 0) {
            if (NUMAProfilerBufferSize < MINIMUMBUFFERSIZE) {
                Log.print("WARNING: Small Buffer Size. Minimum Buffer Size applied! (=");
                Log.print(MINIMUMBUFFERSIZE);
                Log.println(")");
                allocatorBufferSize = MINIMUMBUFFERSIZE;
                survivorBufferSize = MINIMUMBUFFERSIZE;
            } else {
                allocatorBufferSize = NUMAProfilerBufferSize;
                survivorBufferSize = NUMAProfilerBufferSize;
            }
        }

        initAllThreadLocalRecordBuffers();

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Initialize the Survivor Objects NUMAProfiler Buffers.");
        }
        initSurvivorBuffersForAllThreads();

        memoryPageSize = NUMALib.numaPageSize();

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Initialize the Heap Boundaries Buffer.");
        }
        initializeHeapBoundariesBuffer();

        numaConfig = new NUMALib();

        heapStart = vm().config.heapScheme().getHeapStartAddress();

        profilingCycle = 1;
        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Initialization Complete.");

            Log.print("(NUMA Profiler): Start Profiling. [Cycle ");
            Log.print(profilingCycle);
            Log.println("]");
        }

        //initialize thread local counters
        initProfilingCounters();

        if (NUMAProfilerExplicitGCThreshold == 0) {
            enableProfiling();
        }
    }

    public static void onVmThreadStart(int threadId, String threadName, Pointer etla) {
        if ((NUMAProfilerExplicitGCThreshold >= 0 && iteration >= NUMAProfiler.NUMAProfilerExplicitGCThreshold) ||
            (!NUMAProfilerFlareAllocationThresholds.equals("0") && enableFlareObjectProfiler)) {
            Log.println("(profilingThread);" + profilingCycle + ";" + threadId + ";" + threadName);
            PROFILER_STATE.store(etla, Address.fromInt(PROFILING_STATE.ENABLED.getValue()));
        }
        // Initialize new Thread's Record Buffer
        initThreadLocalRecordBuffer.run(etla);
        initThreadLocalSurvivorBuffers.run(etla);
    }

    /**
     * Check if the given hub is a hub of a Flare object and increase the
     * {@link #flareObjectCounter} if so.
     *
     * @param hub
     */
    public static void checkForFlareObject(Hub hub) {
        final boolean lockDisabledSafepoints = lock();
        if (MaxineVM.useNUMAProfiler && !NUMAProfilerFlareAllocationThresholds.equals("0")) {
            String type = hub.classActor.name();
            final int currentThreadID = VmThread.current().id();
            if (type.contains(NUMAProfilerFlareObjectStart)) {
                flareObjectCounter++;
                if (NUMAProfilerVerbose) {
                    Log.print("(NUMA Profiler): Start Flare-Object Counter: ");
                    Log.println(flareObjectCounter);
                }
                if (flareObjectCounter == flareAllocationThresholds[start_counter]) {
                    if (enableFlareObjectProfiler) {
                        throw FatalError.unexpected("The NUMA Profiler supports only a single profiling instance a time. " +
                            "It seams that there is already an ongoing Flare-Object profiling");
                    }
                    flareObjectThreadIdBuffer[start_counter] = currentThreadID;
                    if (NUMAProfilerVerbose) {
                        Log.print("(NUMA Profiler): Enable profiling due to flare object allocation for id ");
                        Log.println(currentThreadID);
                    }
                    if (start_counter < flareAllocationThresholds.length - 1) {
                        start_counter++;
                    }
                    if (NUMAProfiler.NUMAProfilerIsolateDominantThread) {
                        setProfilingTLA.run(VmThread.currentTLA());
                    } else {
                        enableProfiling();
                    }
                    enableFlareObjectProfiler = true;
                }
            } else if (enableFlareObjectProfiler == true && flareObjectThreadIdBuffer[end_counter] == currentThreadID && type.contains(NUMAProfilerFlareObjectEnd)) {
                if (NUMAProfilerVerbose) {
                    Log.print("(NUMA Profiler): Disable profiling due to flare end object allocation for id ");
                    Log.println(currentThreadID);
                }
                end_counter++;
                if (NUMAProfiler.NUMAProfilerIsolateDominantThread) {
                    resetProfilingTLA.run(VmThread.currentTLA());
                } else {
                    disableProfiling();
                }
                enableFlareObjectProfiler = false;
            }
        }
        unlock(lockDisabledSafepoints);
    }

    public static boolean shouldProfile() {
        if (MaxineVM.useNUMAProfiler) {
            int profilerTLA = PROFILER_STATE.load(VmThread.currentTLA()).toInt();
            return profilerTLA == PROFILING_STATE.ENABLED.getValue();
        }
        return false;
    }

    private void initializeHeapBoundariesBuffer() {
        int bufSize = Heap.maxSize().dividedBy(memoryPageSize).toInt();
        heapPages = new VirtualPagesBuffer(bufSize);
        heapPages.writeNumaNode(0, NUMALib.numaNodeOfAddress(heapStart.toLong()));
    }

    /**
     * This method is called when a profiled object is allocated.
     */
    @NO_SAFEPOINT_POLLS("numa profiler call chain must be atomic")
    @NEVER_INLINE
    public static void profileNew(int size, String type, long address) {
        final boolean wasDisabled = SafepointPoll.disable();
        RecordBuffer.getForCurrentThread(ETLA.load(VmThread.current().tla()), RECORD_BUFFER.ALLOCATIONS_BUFFER.value).profile(size, type, address);
        if (!wasDisabled) {
            SafepointPoll.enable();
        }
    }

    /**
     * This method assesses the locality of a memory access and returns the {@link ACCESS_COUNTER} value to be incremented.
     * A memory access can be either local (a thread running on N numa node accesses an object on N numa node),
     * inter-node (a thread running on N numa node accesses an object on M numa node with both N and M being on the same blade),
     * or inter-blade (a thread running on N numa node accesses an object on Z numa node which is part of another blade).
     * @param address
     * @return {@code accessCounterValue} + 0 for LOCAL access, {@code accessCounterValue} + 1 for INTER-NODE access, {@code accessCounterValue} + 2 for INTER-BLADE access (see {@link ACCESS_COUNTER} values)
     *
     */
    private static int assessAccessLocality(long address, int accessCounterValue) {
        // get the Numa Node where the thread which is performing the write is running
        final int threadNumaNode = Intrinsics.getCpuID() >> MaxineIntrinsicIDs.NUMA_NODE_SHIFT;
        // get the Numa Node where the written object is placed
        final int objectNumaNode = getNumaNodeForAddress(address);

        if (threadNumaNode != objectNumaNode) {
            // get the Blade where the thread Numa Node is located
            final int threadBlade = threadNumaNode / 6;
            // get the Blade where the object Numa Node is located
            final int objectBlade = objectNumaNode / 6;
            if (threadBlade != objectBlade) {
                return accessCounterValue + 2;
            } else {
                return accessCounterValue + 1;
            }
        } else {
            return accessCounterValue;
        }
    }

    private static void increaseAccessCounter(int counter) {
        Pointer tla = VmThread.currentTLA();
        assert ETLA.load(tla) == tla;
        long value = profilingCounters[counter].load(tla).toLong() + 1;
        profilingCounters[counter].store(tla, Address.fromLong(value));
    }

    @NO_SAFEPOINT_POLLS("numa profiler call chain must be atomic")
    @NEVER_INLINE
    public static void profileAccess(ACCESS_COUNTER counter, long address) {

        // if the written object is not part of the data heap
        // TODO: implement some action, currently ignore
        if (!vm().config.heapScheme().contains(Address.fromLong(address))) {
            return;
        }

        final int accessCounter = assessAccessLocality(address, counter.value);

        // increment local or remote writes
        increaseAccessCounter(accessCounter);
    }

    /**
     * Dump NUMAProfiler Buffer to Maxine's Log output.
     */
    private void dumpBuffer() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, printAllocationBuffer);
    }

    private void dumpSurvivors() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, printSurvivorsBuffers);
    }

    private void dumpHeapBoundaries() {
        final boolean lockDisabledSafepoints = lock();
        heapPages.printStats(profilingCycle);
        unlock(lockDisabledSafepoints);
    }

    private void resetHeapBoundaries() {
        final boolean lockDisabledSafepoints = lock();
        heapPages.resetBuffer();
        heapPages.writeNumaNode(0, NUMALib.numaNodeOfAddress(heapStart.toLong()));
        unlock(lockDisabledSafepoints);
    }

    /**
     * Finds the index of the memory page of an address in the heapPages Buffer.
     * It is based on the calculation:
     * pageIndex = (address - firstPageAddress) / pageSize
     * @param address an address
     * @return the memory page index of the address
     */
    private static int getHeapPagesIndexOfAddress(Address address) {
        return address.minus(heapStart).dividedBy(numaProfiler.memoryPageSize).toInt();
    }

    @INTRINSIC(UNSAFE_CAST)
    private static native MemoryRegion asMemoryRegion(Object object);

    private final Pointer.Procedure findNumaNodeForSpace = new Pointer.Procedure() {
        public void run(Pointer pointer) {
            Reference    reference = Reference.fromOrigin(pointer);
            MemoryRegion space     = asMemoryRegion(reference);
            findNumaNodeForAllSpaceMemoryPages(space);
        }
    };

    /**
     * Find the NUMA Node for each virtual memory page of the JVM Heap.
     * Currently implemented only for the {@link SemiSpaceHeapScheme}.
     */
    private void findNumaNodeForAllHeapMemoryPages() {
        vm().config.heapScheme().forAllSpaces(findNumaNodeForSpace);
    }

    /**
     * Find the NUMA node for each memory page in the premises of a specific Memory Space.
     *
     * @param space
     */
    private void findNumaNodeForAllSpaceMemoryPages(MemoryRegion space) {
        int pageIndex;
        int node;

        Address currentAddress = space.start();

        while (currentAddress.lessThan(space.end())) {
            // Get NUMA node of address using NUMALib
            node = NUMALib.numaNodeOfAddress(currentAddress.toLong());
            // Get the index of the memory page in the heapPages Buffer
            pageIndex = getHeapPagesIndexOfAddress(currentAddress);
            // Write the NUMA node of the page in the heapPages Buffer
            heapPages.writeNumaNode(pageIndex, node);

            // Get the next memory page address
            currentAddress = currentAddress.plus(memoryPageSize);

            // if no NUMA node is found the page is still unallocated
            if (node == NUMALib.EFAULT) {
                node = VirtualPagesBuffer.maxNumaNodes;
            }

            // update stats
            int count = heapPages.readStats(node);
            heapPages.writeStats(node, count + 1);
        }

    }

    /**
     * Get the physical NUMA node id for a virtual address.
     *
     * We use {@code heapPages} (a {@link VirtualPagesBuffer} instance) as a "cache" that stores a mapping
     * to a physical NUMA node for each virtual memory page. We calculate the index of the memory page into
     * the cache (to avoid the linear search) and we get the corresponding NUMA node.
     * It might return EFAULT (=-14) in case it is the first hit of the memory page in the current cycle.
     * In that case the system call from NUMALib is called directly and the values are updated.
     *
     * @param address
     * @return physical NUMA node id
     */
    public static int getNumaNodeForAddress(long address) {
        int pageIndex = getHeapPagesIndexOfAddress(Address.fromLong(address));

        int objNumaNode = heapPages.readNumaNode(pageIndex);
        // if outdated, use the sys call to get the numa node and update heapPages buffer
        if (objNumaNode == NUMALib.EFAULT) {
            Address pageAddr = heapStart.plus(Address.fromInt(numaProfiler.memoryPageSize).times(pageIndex));
            int node = NUMALib.numaNodeOfAddress(pageAddr.toLong());
            heapPages.writeNumaNode(pageIndex, node);
            objNumaNode = node;
        }
        return objNumaNode;
    }

    /**
     * Search {@code from} buffer for survivor objects and store them into {@code to} buffer.
     * If an object is alive, update both its Virtual Address and
     * NUMA Node before coping it to the survivors buffer
     * @param from the source buffer in which we search for survivor objects.
     * @param to the destination buffer in which we store the survivor objects.
     */
    private static void storeSurvivors(RecordBuffer from, RecordBuffer to) {
        if (NUMAProfilerVerbose) {
            Log.print("(NUMA Profiler): Copy Survived Objects from ");
            Log.print(from.buffersName);
            Log.print(" to ");
            Log.println(to.buffersName);
        }
        for (int i = 0; i < from.currentIndex; i++) {
            long address = from.readAddr(i);

            if (Heap.isSurvivor(address)) {
                // update Virtual Address
                long newAddr = Heap.getForwardedAddress(address);
                // update NUMA Node
                int node = NUMALib.numaNodeOfAddress(newAddr);
                //guard survivors RecordBuffer from overflow
                assert to.currentIndex < to.bufferSize : "Survivor Buffer out of bounds! Increase the Buffer Size.";
                // write it to Buffer
                to.record(from.readThreadId(i), from.readType(i), from.readSize(i), newAddr, node);
            }
        }
    }

    /**
     * This method is called from postGC actions to profile the survivor objects.
     * It calls the {@linkplain #profileSurvivorsProcedure}.
     */
    private void profileSurvivors() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, profileSurvivorsProcedure);
    }

    /**
     * A method to check and update the profiling state.
     * NOTE: This only applies for ExplicitGC Driven profiling.
     */
    private void checkAndUpdateProfilingState() {
        // Check if the current GC is explicit. If yes, increase the iteration counter.
        if (isExplicitGC) {
            iteration++;
            isExplicitGC = false;
            if (iteration == NUMAProfiler.NUMAProfilerExplicitGCThreshold) {
                if (NUMAProfilerVerbose) {
                    Log.println("(NUMA Profiler): Enabling profiling. [post-GC phase]");
                }
                enableProfiling();
            }
        }

        profilingCycle++;
    }

    /**
     * This method is called by ProfilerGCCallbacks in every pre-gc callback phase.
     */
    void preGCActions() {

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Entering Pre-GC Phase.");
            Log.print("(NUMA Profiler): Cycle ");
            Log.print(profilingCycle);
            Log.println(" Profiling Is Now Complete. [pre-GC phase]");
        }

        // guard libnuma sys call usages during implicit GCs
        // find numa nodes for all pages in the GC exactly before the first profiling cycle
        if (isExplicitGC && iteration >= NUMAProfiler.NUMAProfilerExplicitGCThreshold - 1) {
            findNumaNodeForAllHeapMemoryPages();
        }

        dumpHeapBoundaries();

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Leaving Pre-GC Phase.");
        }
    }

    /**
     *  This method is called every time a GC has been completed.
     */
    void postGCActions() {

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Entering Post-GC Phase.");
        }

        profileSurvivors();

        if ((profilingCycle % 2) == 0) {
            if (NUMAProfilerVerbose) {
                Log.println("(NUMA Profiler): Clean-up Survivor1 Buffer. [post-gc phase]");
            }
            resetSurvivors1Buffers();
        } else {
            if (NUMAProfilerVerbose) {
                Log.println("(NUMA Profiler): Clean-up Survivor2 Buffer. [post-gc phase]");
            }
            resetSurvivors2Buffers();
        }

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Print Allocations Thread Local Buffers. [post-GC phase]");
        }
        dumpBuffer();

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Reset Allocation Thread Local Buffers. [post-gc phase]");
        }
        resetAllocationBuffers();

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Dump Survivors Buffer. [post-GC phase]");
        }
        dumpSurvivors();

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Print Access Profiling Thread Local Counters. [post-GC phase]");
        }
        printProfilingCounters();

        checkAndUpdateProfilingState();

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Leaving Post-GC Phase.");
            Log.print("(NUMA Profiler): Start Profiling. [Cycle ");
            Log.print(profilingCycle);
            Log.println("]");
        }

    }

    /**
     *  A method to transform a string of that form "int0,int1,int2" into an integer array [int0, int1, int2].
     */
    private void splitStringtoSortedIntegers() {

        String[] thresholds = NUMAProfilerFlareAllocationThresholds.split(",");
        flareObjectThreadIdBuffer = new int[thresholds.length];
        flareAllocationThresholds = new int[thresholds.length];
        for (int i = 0; i < thresholds.length; i++) {
            flareAllocationThresholds[i] = Integer.parseInt(thresholds[i]);
        }
        Arrays.sort(flareAllocationThresholds);

    }


    /**
     * This {@link Pointer.Predicate} confirms if an action requested from a VmThread
     * should be allowed or not. We have chosen to disable profiling for:
     * a) VmOperationThread [thread 2]
     * b) Signal Dispacher [thread 3]
     * c) Reference Handler [thread 4]
     * d) Finalizer [thread 5]
     * All the above VmThreads not only are not actively participating in application's execution
     * but also, due to their nature they occasionally confuse the profiler. Therefore they are ignored.
     */
    public static final Pointer.Predicate profilingPredicate = new Pointer.Predicate() {
        @Override
        public boolean evaluate(Pointer tla) {
            VmThread vmThread = VmThread.fromTLA(tla);
            return vmThread.javaThread() != null &&
                    !vmThread.isVmOperationThread() &&
                    !vmThread.getName().equals("Signal Dispatcher") &&
                    !vmThread.getName().equals("Reference Handler") &&
                    (NUMAProfilerIncludeFinalization || !vmThread.getName().equals("Finalizer"));
        }
    };

    /**
     * A predicate to determine if profiling is enabled on the current thread.
     */
    public static final Pointer.Predicate isProfilingEnabledPredicate = new Pointer.Predicate() {
        @Override
        public boolean evaluate(Pointer tla) {
            return PROFILER_STATE.load(tla).toInt() == 1;
        }
    };

    private static final Pointer.Procedure setProfilingTLA = new Pointer.Procedure() {
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            PROFILER_STATE.store(etla, Address.fromInt(PROFILING_STATE.ENABLED.value));
        }
    };

    private static void enableProfiling() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, setProfilingTLA);
    }

    private static final Pointer.Procedure resetProfilingTLA = new Pointer.Procedure() {
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            PROFILER_STATE.store(etla, Address.fromInt(PROFILING_STATE.DISABLED.value));
        }
    };

    private static void disableProfiling() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, resetProfilingTLA);
    }

    /**
     * A {@link Pointer.Procedure} that prints a thread's all Object Access Profiling Counters}.
     */
    private static final Pointer.Procedure printThreadLocalProfilingCounters = new Pointer.Procedure() {
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            for (int i = 0; i < profilingCounters.length; i++) {
                VmThreadLocal profilingCounter = profilingCounters[i];
                final long count = profilingCounter.load(etla).toLong();
                if (count != 0) {
                    Log.print("(accessCounter);");
                    Log.print(profilingCycle);
                    Log.print(";");
                    Log.print(VmThread.fromTLA(etla).id());
                    Log.print(";");
                    Log.print(profilingCounter.name);
                    Log.print(";");
                    Log.println(count);
                }
                //reset counter
                profilingCounter.store(etla, Address.fromInt(0));
            }
        }
    };

    /**
     * Call {@link #initThreadLocalProfilingCounters} for all ACTIVE threads.
     */
    private static void printProfilingCounters() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, printThreadLocalProfilingCounters);
    }

    /**
     * A method to print the Access Profiling Counters of one specific thread.
     * @param tla
     */
    public static void printProfilingCountersOfThread(Pointer tla) {
        printThreadLocalProfilingCounters.run(tla);
    }

    /**
     * A {@link Pointer.Procedure} that prints a thread's Allocations Buffer}.
     */
    private static final Pointer.Procedure printAllocationBuffer = new Pointer.Procedure() {
        @Override
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            RecordBuffer.getForCurrentThread(etla, RECORD_BUFFER.ALLOCATIONS_BUFFER.value).print(profilingCycle, 1);
        }
    };

    /**
     * A method to print the Allocations Buffer of one specific thread.
     * @param tla
     */
    public static void printAllocationBufferOfThread(Pointer tla) {
        printAllocationBuffer.run(tla);
    }

    private static final Pointer.Procedure printSurvivorsBuffers = new Pointer.Procedure() {
        @Override
        public void run(Pointer tla) {
            if (NUMAProfilerVerbose) {
                Log.print("==== Survivors Cycle ");
                Log.print(profilingCycle);
                Log.println(" ====");
            }
            if ((profilingCycle % 2) == 0) {
                RecordBuffer.getForCurrentThread(tla, RECORD_BUFFER.SURVIVORS_2_BUFFER.value).print(profilingCycle, 0);
            } else {
                RecordBuffer.getForCurrentThread(tla, RECORD_BUFFER.SURVIVORS_1_BUFFER.value).print(profilingCycle, 0);
            }
        }
    };

    private static final Pointer.Procedure resetAllocationBuffer = new Pointer.Procedure() {
        @Override
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            RecordBuffer.getForCurrentThread(etla, RECORD_BUFFER.ALLOCATIONS_BUFFER.value).resetBuffer();
        }
    };

    private static final Pointer.Procedure resetSurvivors1Buffer = new Pointer.Procedure() {
        @Override
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            RecordBuffer.getForCurrentThread(etla, RECORD_BUFFER.SURVIVORS_2_BUFFER.value).resetBuffer();
        }
    };

    private static final Pointer.Procedure resetSurvivors2Buffer = new Pointer.Procedure() {
        @Override
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            RecordBuffer.getForCurrentThread(etla, RECORD_BUFFER.SURVIVORS_2_BUFFER.value).resetBuffer();
        }
    };

    /**
     * Only for threads terminated before GC.
     * @param tla
     */
    public static void resetAllocationBufferOfThread(Pointer tla) {
        resetAllocationBuffer.run(tla);
    }

    /**
     * Only for frozen threads during GC.
     */
    public static void resetAllocationBuffers() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, resetAllocationBuffer);
    }

    public static void resetSurvivors1Buffers() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, resetSurvivors1Buffer);
    }

    public static void resetSurvivors2Buffers() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, resetSurvivors2Buffer);
    }

    /**
     * A {@link Pointer.Procedure} that initializes all Access Profiling Counters of a thread.
     */
    private static final Pointer.Procedure initThreadLocalProfilingCounters = new Pointer.Procedure() {
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            for (int i = 0; i < profilingCounters.length; i++) {
                VmThreadLocal profilingCounter = profilingCounters[i];
                profilingCounter.store(etla, Address.fromInt(0));
            }
        }
    };

    /**
     * Call {@link #initThreadLocalProfilingCounters} for all {@linkplain VmThreadMap#ACTIVE} threads.
     */
    private static void initProfilingCounters() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, initThreadLocalProfilingCounters);
    }

    /**
     * A procedure to profile the survivor objects.
     * We use two {@linkplain RecordBuffer}s for the survivor objects, the {@code SURVIVORS_1_BUFFER} and {@code SURVIVORS_2_BUFFER}.
     * One buffer contains the still surviving objects (old) from previous GC(s) -of the current cycle-
     * and the other is empty and ready to store the old followed by the survivor objects from the last GC (new).
     *
     * The buffers swap their roles in an even/odd profiling cycle fashion.
     *
     */
    public static final Pointer.Procedure profileSurvivorsProcedure = new Pointer.Procedure() {
        public void run(Pointer tla) {
            if ((profilingCycle % 2) == 0) {
                //even cycles
                storeSurvivors(RecordBuffer.getForCurrentThread(tla, RECORD_BUFFER.SURVIVORS_1_BUFFER.value), RecordBuffer.getForCurrentThread(tla, RECORD_BUFFER.SURVIVORS_2_BUFFER.value));
                storeSurvivors(RecordBuffer.getForCurrentThread(tla, RECORD_BUFFER.ALLOCATIONS_BUFFER.value), RecordBuffer.getForCurrentThread(tla, RECORD_BUFFER.SURVIVORS_2_BUFFER.value));
            } else {
                //odd cycles
                storeSurvivors(RecordBuffer.getForCurrentThread(tla, RECORD_BUFFER.SURVIVORS_2_BUFFER.value), RecordBuffer.getForCurrentThread(tla, RECORD_BUFFER.SURVIVORS_1_BUFFER.value));
                storeSurvivors(RecordBuffer.getForCurrentThread(tla, RECORD_BUFFER.ALLOCATIONS_BUFFER.value), RecordBuffer.getForCurrentThread(tla, RECORD_BUFFER.SURVIVORS_1_BUFFER.value));
            }
        }
    };

    /**
     * A {@link Pointer.Procedure} that initializes the two Survivor Buffers of a thread.
     * It is also used independently when a new thread is spawned.
     */
    public static final Pointer.Procedure initThreadLocalSurvivorBuffers = new Pointer.Procedure() {
        public void run(Pointer tla) {
            final RecordBuffer survivors1 = new RecordBuffer(survivorBufferSize, "Survivors Buffer No1");
            RecordBuffer.setForCurrentThread(tla, survivors1, RECORD_BUFFER.SURVIVORS_1_BUFFER.value);
            final RecordBuffer survivors2 = new RecordBuffer(survivorBufferSize, "Survivors Buffer No2");
            RecordBuffer.setForCurrentThread(tla, survivors2, RECORD_BUFFER.SURVIVORS_2_BUFFER.value);
        }
    };

    private void initSurvivorBuffersForAllThreads() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, initThreadLocalSurvivorBuffers);
    }

    /**
     * A {@link Pointer.Procedure} that initializes the Allocation Buffer of a thread.
     * It is also used independently when a new thread is spawned.
     */
    public static final Pointer.Procedure initThreadLocalRecordBuffer = new Pointer.Procedure() {
        public void run(Pointer tla) {
            final RecordBuffer allocationsBuffer = new RecordBuffer(allocatorBufferSize, "allocations Buffer ");
            assert tla.equals(ETLA.load(tla));
            RecordBuffer.setForCurrentThread(tla, allocationsBuffer, RECORD_BUFFER.ALLOCATIONS_BUFFER.value);
        }
    };

    /**
     * Calls {@code initThreadLocalRecordBuffer} {@link Pointer.Procedure} for all ACTIVE threads.
     * It is used in NUMAProfiler's initialization for VM Threads.
     */
    private void initAllThreadLocalRecordBuffers() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, initThreadLocalRecordBuffer);
    }

    public static final Pointer.Procedure deallocateAllocationsBuffer = new Pointer.Procedure() {
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            RecordBuffer.getForCurrentThread(etla, RECORD_BUFFER.ALLOCATIONS_BUFFER.value).deallocateAll();
        }
    };

    public static final Pointer.Procedure deallocateSurvivors1Buffer = new Pointer.Procedure() {
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            RecordBuffer.getForCurrentThread(etla, RECORD_BUFFER.SURVIVORS_1_BUFFER.value).deallocateAll();
        }
    };

    public static final Pointer.Procedure deallocateSurvivors2Buffer = new Pointer.Procedure() {
        public void run(Pointer tla) {
            Pointer etla = ETLA.load(tla);
            RecordBuffer.getForCurrentThread(etla, RECORD_BUFFER.SURVIVORS_2_BUFFER.value).deallocateAll();
        }
    };

    private void releaseReservedMemory() {
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, deallocateAllocationsBuffer);
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, deallocateSurvivors1Buffer);
        VmThreadMap.ACTIVE.forAllThreadLocals(profilingPredicate, deallocateSurvivors2Buffer);
        heapPages.deallocateAll();
    }

    public static void onVmThreadExit(Pointer tla) {
        final boolean isThreadBeingProfiled = NUMAProfiler.profilingPredicate.evaluate(tla) && NUMAProfiler.isProfilingEnabledPredicate.evaluate(tla);
        if (isThreadBeingProfiled) {
            final boolean lockDisabledSafepoints = lock();
            NUMAProfiler.printAllocationBufferOfThread(tla);
            NUMAProfiler.resetAllocationBufferOfThread(tla);
            NUMAProfiler.printProfilingCountersOfThread(tla);
            NUMAProfiler.deallocateAllocationsBuffer.run(tla);
            NUMAProfiler.deallocateSurvivors1Buffer.run(tla);
            NUMAProfiler.deallocateSurvivors2Buffer.run(tla);
            unlock(lockDisabledSafepoints);
        }
    }

    /**
     * This method can be used for actions need to take place right before
     * NUMA Profiler's termination. It is triggered when {@linkplain com.sun.max.vm.run.java.JavaRunScheme}
     * is being terminated.
     */
    public void terminate() {

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Disable profiling for termination");
        }

        // Disable profiling for shutdown
        disableProfiling();

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Termination");
        }

        dumpHeapBoundaries();

        dumpBuffer();

        printProfilingCounters();

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Release Reserved Memory.");
        }

        if (!NUMAProfilerIncludeFinalization) {
            releaseReservedMemory();
        }

        if (NUMAProfilerVerbose) {
            Log.println("(NUMA Profiler): Terminating... Bye!");
        }
    }

    private static int lockOwner;
    private static int lockDepth;

    /**
     * lock() and unlock() methods have been implemented according to the Log.lock() and Log.unlock() ones.
     *
     */
    @NO_SAFEPOINT_POLLS("numa profiler call chain must be atomic")
    @NEVER_INLINE
    public static boolean lock() {
        if (isHosted()) {
            return true;
        }

        boolean wasDisabled = SafepointPoll.disable();
        NUMAProfiler.numaProfiler_lock();
        if (lockDepth == 0) {
            FatalError.check(lockOwner == 0, "numa profiler lock should have no owner with depth 0");
            lockOwner = VmThread.current().id();
        }
        lockDepth++;
        return !wasDisabled;
    }

    /**
     * lock() and unlock() methods have been implemented according to the Log.lock() and Log.unlock() ones.
     *
     */
    @NO_SAFEPOINT_POLLS("numa profiler call chain must be atomic")
    @NEVER_INLINE
    public static void unlock(boolean lockDisabledSafepoints) {
        if (isHosted()) {
            return;
        }

        --lockDepth;
        FatalError.check(lockDepth >= 0, "mismatched lock/unlock");
        FatalError.check(lockOwner == VmThread.current().id(), "numa profiler lock should be owned by current thread");
        if (lockDepth == 0) {
            lockOwner = 0;
        }
        NUMAProfiler.numaProfiler_unlock();
        ProgramError.check(SafepointPoll.isDisabled(), "Safepoints must not be re-enabled in code surrounded by NUMAProfiler.lock() and NUMAProfiler.unlock()");
        if (lockDisabledSafepoints) {
            SafepointPoll.enable();
        }
    }
}
