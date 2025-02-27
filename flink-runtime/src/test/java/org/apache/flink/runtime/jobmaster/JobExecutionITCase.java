/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.jobmaster;

import org.apache.flink.runtime.io.network.partition.ResultPartitionType;
import org.apache.flink.runtime.jobgraph.DistributionPattern;
import org.apache.flink.runtime.jobgraph.JobGraph;
import org.apache.flink.runtime.jobgraph.JobGraphTestUtils;
import org.apache.flink.runtime.jobgraph.JobVertex;
import org.apache.flink.runtime.jobmanager.scheduler.SlotSharingGroup;
import org.apache.flink.runtime.minicluster.TestingMiniCluster;
import org.apache.flink.runtime.minicluster.TestingMiniClusterConfiguration;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.apache.flink.runtime.util.JobVertexConnectionUtils.connectNewDataSetAsInput;
import static org.assertj.core.api.Assertions.assertThat;

/** Integration tests for job scheduling. */
class JobExecutionITCase {

    /**
     * Tests that tasks with a co-location constraint are scheduled in the same slots. In fact it
     * also tests that consumers are scheduled wrt their input location if the co-location
     * constraint is deactivated.
     */
    @Test
    void testCoLocationConstraintJobExecution() throws Exception {
        final int numSlotsPerTaskExecutor = 1;
        final int numTaskExecutors = 3;
        final int parallelism = numTaskExecutors * numSlotsPerTaskExecutor;
        final JobGraph jobGraph = createJobGraph(parallelism);

        final TestingMiniClusterConfiguration miniClusterConfiguration =
                TestingMiniClusterConfiguration.newBuilder()
                        .setNumSlotsPerTaskManager(numSlotsPerTaskExecutor)
                        .setNumTaskManagers(numTaskExecutors)
                        .setLocalCommunication(true)
                        .build();

        try (TestingMiniCluster miniCluster =
                TestingMiniCluster.newBuilder(miniClusterConfiguration).build()) {
            miniCluster.start();

            miniCluster.submitJob(jobGraph).get();

            final CompletableFuture<JobResult> jobResultFuture =
                    miniCluster.requestJobResult(jobGraph.getJobID());

            assertThat(jobResultFuture.get().isSuccess()).isTrue();
        }
    }

    private JobGraph createJobGraph(int parallelism) {
        final JobVertex sender = new JobVertex("Sender");
        sender.setParallelism(parallelism);
        sender.setInvokableClass(TestingAbstractInvokables.Sender.class);

        final JobVertex receiver = new JobVertex("Receiver");
        receiver.setParallelism(parallelism);
        receiver.setInvokableClass(TestingAbstractInvokables.Receiver.class);

        // In order to make testCoLocationConstraintJobExecution fail, one needs to
        // remove the co-location constraint and the slot sharing groups, because then
        // the receivers will have to wait for the senders to finish and the slot
        // assignment order to the receivers is non-deterministic (depending on the
        // order in which the senders finish).
        final SlotSharingGroup slotSharingGroup = new SlotSharingGroup();
        receiver.setSlotSharingGroup(slotSharingGroup);
        sender.setSlotSharingGroup(slotSharingGroup);
        receiver.setStrictlyCoLocatedWith(sender);

        connectNewDataSetAsInput(
                receiver, sender, DistributionPattern.POINTWISE, ResultPartitionType.PIPELINED);

        return JobGraphTestUtils.streamingJobGraph(sender, receiver);
    }
}
