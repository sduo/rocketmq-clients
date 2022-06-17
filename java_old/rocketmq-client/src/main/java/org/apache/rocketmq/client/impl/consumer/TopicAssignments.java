/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.client.impl.consumer;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.Immutable;
import org.apache.rocketmq.client.message.MessageQueue;
import org.apache.rocketmq.client.misc.MixAll;
import org.apache.rocketmq.client.route.Partition;
import org.apache.rocketmq.client.route.TopicRouteData;

@Immutable
public class TopicAssignments {
    private final List<Assignment> assignmentList;

    public TopicAssignments(TopicRouteData topicRouteData) {
        this.assignmentList = new ArrayList<Assignment>();
        final List<Partition> partitions = topicRouteData.getPartitions();
        for (Partition partition : partitions) {
            if (MixAll.MASTER_BROKER_ID != partition.getBroker().getId()) {
                continue;
            }
            final MessageQueue mq = new MessageQueue(partition);
            final Assignment assignment = new Assignment(mq);
            assignmentList.add(assignment);
        }
    }

    public TopicAssignments(List<apache.rocketmq.v1.Assignment> assignmentList) {
        this.assignmentList = new ArrayList<Assignment>();
        for (apache.rocketmq.v1.Assignment item : assignmentList) {
            MessageQueue messageQueue = new MessageQueue(new Partition(item.getPartition()));
            this.assignmentList.add(new Assignment(messageQueue));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TopicAssignments that = (TopicAssignments) o;
        return Objects.equal(assignmentList, that.assignmentList);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(assignmentList);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                          .add("assignmentList", assignmentList)
                          .toString();
    }

    public List<Assignment> getAssignmentList() {
        return this.assignmentList;
    }
}