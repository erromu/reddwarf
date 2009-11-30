/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.tests.workload;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Properties;

/**
 * The {@code WorkloadGeneratorService} is designed to generate a steady
 * load of work in the form of transactional tasks submitted to the
 * {@code TransactionScheduler}.  Upon startup, the service initiates a set
 * of self-replicating task generators that submit tasks to the
 * {@code TransactionScheduler} with an exponential distribution of interarrival
 * times.  The result is a standard "Poisson" distribution of events that
 * simulates a realistic workload of tasks.<p>
 *
 * The tasks generated by the workload generators are designed to be well
 * behaved with regard to standard practice for Project Darkstar server
 * implementations.  Essentially this means that they perform on the order of
 * a millisecond or two and should never timeout when there is no contention.<p>
 *
 * The {@code WorkloadGeneratorService} supports the following configuration
 * properties:
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #GENERATORS_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_GENERATORS}
 *
 * <dd style="padding-top: .5em">Specifies the number of task generators to
 * launch on service startup.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #BINDINGS_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_BINDINGS}
 *
 * <dd style="padding-top: .5em">Specifies the number of objects to assign to
 * name bindings for writing.  This value is important in establishing the
 * probability of write conflict between concurrent tasks.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #ACCESSES_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_ACCESSES}
 *
 * <dd style="padding-top: .5em">Specifies the number of object accesses each
 * task should make.  This value includes the number of write accesses described
 * below (in other words, the total number of read accesses per transaction
 * equals this value minus the total number of write accesses).<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #WRITES_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_WRITES}
 *
 * <dd style="padding-top: .5em">Specifies the number of write object accesses
 * each task should make.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #TASKS_PER_SECOND_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #DEFAULT_TASKS_PER_SECOND}
 *
 * <dd style="padding-top: .5em">Specifies the number of tasks per second
 * generated by each generator.<p>
 *
 * </dl> <p>
 *
 * The expected overall task throughput generated by this service is equal to:
 * generators x tasks_per_second<p>
 */
public class WorkloadGeneratorService implements Service {

    public static final String PACKAGE_NAME = "com.sun.sgs.tests.workload";

    public static final String GENERATORS_PROPERTY = PACKAGE_NAME + ".generators";
    public static final Integer DEFAULT_GENERATORS = 300;

    public static final String BINDINGS_PROPERTY = PACKAGE_NAME + ".bindings";
    public static final Integer DEFAULT_BINDINGS = 1000;

    public static final String ACCESSES_PROPERTY = PACKAGE_NAME + ".accesses";
    public static final Integer DEFAULT_ACCESSES = 10;

    public static final String WRITES_PROPERTY = PACKAGE_NAME + ".writes";
    public static final Integer DEFAULT_WRITES = 2;

    public static final String TASKS_PER_SECOND_PROPERTY = PACKAGE_NAME + ".tasks.per.second";
    public static final Integer DEFAULT_TASKS_PER_SECOND = 10;
    
    private final ComponentRegistry systemRegistry;
    private final TransactionProxy txnProxy;
    private final TaskScheduler taskScheduler;
    private final TransactionScheduler txnScheduler;
    private final Identity owner;

    private final DataService dataService;
    private final TaskService taskService;

    private final int generators;
    private final int bindings;
    private final int accesses;
    private final int writes;
    private final int reads;
    private final int tasksPerSecond;

    private final int taskThreads;
    private final int txnThreads;

    private final int size = 500;
    private int counter = 0;

    public WorkloadGeneratorService(Properties props,
                                    ComponentRegistry systemRegistry,
                                    TransactionProxy txnProxy) throws Exception {
        this.systemRegistry = systemRegistry;
        this.txnProxy = txnProxy;
        this.taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
        this.txnScheduler = systemRegistry.getComponent(TransactionScheduler.class);
        this.owner = txnProxy.getCurrentOwner();

        this.dataService = txnProxy.getService(DataService.class);
        this.taskService = txnProxy.getService(TaskService.class);

        PropertiesWrapper wProps = new PropertiesWrapper(props);
        this.generators = wProps.getIntProperty(GENERATORS_PROPERTY,
                                                DEFAULT_GENERATORS,
                                                0, Integer.MAX_VALUE);
        this.bindings = wProps.getIntProperty(BINDINGS_PROPERTY,
                                              DEFAULT_BINDINGS,
                                              0, Integer.MAX_VALUE);
        this.writes = wProps.getIntProperty(WRITES_PROPERTY,
                                            DEFAULT_WRITES,
                                            0, Integer.MAX_VALUE);
        this.accesses = wProps.getIntProperty(ACCESSES_PROPERTY,
                                              DEFAULT_ACCESSES,
                                              0, Integer.MAX_VALUE);
        this.reads = this.accesses - this.writes;
        this.tasksPerSecond = wProps.getIntProperty(TASKS_PER_SECOND_PROPERTY,
                                                    DEFAULT_TASKS_PER_SECOND,
                                                    0,
                                                    Integer.MAX_VALUE);

        Class<?> taskSchedulerClass = taskScheduler.getClass();
        Field taskThreadsPropertyField = taskSchedulerClass.getDeclaredField(
                "CONSUMER_THREADS_PROPERTY");
        Field defaultTaskThreadsField = taskSchedulerClass.getDeclaredField(
                "DEFAULT_CONSUMER_THREADS");
        taskThreadsPropertyField.setAccessible(true);
        defaultTaskThreadsField.setAccessible(true);
        String taskThreadsProperty = (String) taskThreadsPropertyField.get(taskScheduler);
        String defaultTaskThreads = (String) defaultTaskThreadsField.get(taskScheduler);
        this.taskThreads = Integer.parseInt(wProps.getProperty(taskThreadsProperty, defaultTaskThreads));

        Class<?> txnSchedulerClass = txnScheduler.getClass();
        Field txnThreadsPropertyField = txnSchedulerClass.getDeclaredField(
                "CONSUMER_THREADS_PROPERTY");
        Field defaultTxnThreadsField = txnSchedulerClass.getDeclaredField(
                "DEFAULT_CONSUMER_THREADS");
        txnThreadsPropertyField.setAccessible(true);
        defaultTxnThreadsField.setAccessible(true);
        String txnThreadsProperty = (String) txnThreadsPropertyField.get(txnScheduler);
        String defaultTxnThreads = (String) defaultTxnThreadsField.get(txnScheduler);
        this.txnThreads = Integer.parseInt(wProps.getProperty(txnThreadsProperty, defaultTxnThreads));
    }

    public String getName() {
        return this.getClass().getName();
    }

    public void ready() throws Exception {
        setBindings(100, "read", size);
        setBindings(bindings, "write", size);

        System.out.println("Calculating uncontended task time");
        long totalTasks = 10000;
        long startNanos = System.nanoTime();
        for (int i = 0; i < totalTasks; i++) {
            txnScheduler.runTask(new TestTask(), owner);
        }
        long endNanos = System.nanoTime();
        double taskTime = (double) (endNanos - startNanos) / 1000000.0 / totalTasks;
        System.out.printf("  Task Time: %2.2f%n", taskTime);

        // kick off a number of workload generators that should
        // generate tasks with a poisson distribution of interarrival times
        taskScheduler.scheduleTask(new KernelRunnable() {
            public String getBaseTaskType() {
                return "LaunchGenerators";
            }
            public void run() throws Exception {
                for (int i = 0; i < generators; i++) {
                    taskScheduler.scheduleTask(new TaskGenerator(), owner, System.currentTimeMillis());
                    Thread.sleep(nextExponential(50));
                }
            }
        }, owner);
    }

    public void shutdown() {

    }

    /**
     * Generate random numbers with an exponential distribution.  The expected
     * mean of the generated set of numbers from this method should be equal
     * to the given {@code mean}.
     */
    private long nextExponential(long mean) {
        return (long) (((double) mean) * -1.0 * Math.log(Math.random()));
    }

    /**
     * Set a set of {@code ManagedInteger} objects bound to a sequence of
     * name bindings in the data store.
     *
     * @param numBindings number of bindings to set
     * @param prefix the prefix of the name bindings
     * @param objectSize the size in bytes of each objects internal byte array
     */
    private void setBindings(final long numBindings, final String prefix, final int objectSize) {
        counter = 0;
        for (; counter < numBindings; counter++) {
            try {
                txnScheduler.runTask(new KernelRunnable() {
                    public String getBaseTaskType() {
                        return "setBinding";
                    }
                    public void run() {
                        dataService.setBinding(prefix + counter, new ManagedInteger(objectSize));
                    }
                }, owner);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
    }

    /**
     * A dummy managed object to use in the tests.
     */
    private static class ManagedInteger implements ManagedObject, Serializable {
        private static int nextValue = 0;
        public int i;
        public byte[] buffer;
        public ManagedInteger(int size) {
            i = nextValue++;
            buffer = new byte[size];
        }
        public void update() { i++; }
    }

    /**
     * A simple task that generates {@code TestTask}s and submits them to the
     * {@code TransactionScheduler} with exponential interarrival times.
     */
    private class TaskGenerator implements KernelRunnable {
        public String getBaseTaskType() {
            return this.getClass().getName();
        }

        public void run() {
            txnScheduler.scheduleTask(new TestTask(), owner);
            taskScheduler.scheduleTask(this, owner, System.currentTimeMillis() + nextExponential(1000 / tasksPerSecond));
        }
    }

    /**
     * A simple transactional task that will simulate a set of object reads and
     * object writes based on this service's configuration parameters.
     */
    private class TestTask implements KernelRunnable {
        
        int[] objectIndexes = new int[writes];

        public TestTask() {
            for (int i = 0; i < writes; i++) {
                objectIndexes[i] = (int) (Math.random() * (bindings - 1));
            }
        }

        public String getBaseTaskType() {
            return this.getClass().getName();
        }

        public void run() throws Exception {
            // read access a few dummy objects to simulate task overhead
            for (int i = 0; i < reads; i++) {
                dataService.getBinding("read" + i);
            }

            // write access specified number of objects
            for (int i = 0; i < writes; i++) {
                ManagedInteger obj = (ManagedInteger) dataService.getBinding("write" + objectIndexes[i]);
                obj.update();
            }
        }

    }

}
