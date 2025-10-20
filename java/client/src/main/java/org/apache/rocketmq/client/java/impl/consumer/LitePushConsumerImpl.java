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

package org.apache.rocketmq.client.java.impl.consumer;

import apache.rocketmq.v2.Code;
import apache.rocketmq.v2.LiteSubscriptionAction;
import apache.rocketmq.v2.NotifyUnsubscribeLiteCommand;
import apache.rocketmq.v2.ReceiveMessageRequest;
import apache.rocketmq.v2.Status;
import apache.rocketmq.v2.SyncLiteSubscriptionRequest;
import apache.rocketmq.v2.SyncLiteSubscriptionResponse;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.util.Durations;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.LitePushConsumer;
import org.apache.rocketmq.client.java.exception.LiteSubscriptionQuotaExceededException;
import org.apache.rocketmq.client.java.exception.StatusChecker;
import org.apache.rocketmq.client.java.route.Endpoints;
import org.apache.rocketmq.client.java.route.MessageQueueImpl;
import org.apache.rocketmq.client.java.rpc.RpcFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LitePushConsumerImpl extends PushConsumerImpl implements LitePushConsumer {
    private static final Logger log = LoggerFactory.getLogger(LitePushConsumerImpl.class);

    private volatile ScheduledFuture<?> syncAllScheduledFuture;
    private final LitePushConsumerSettings litePushConsumerSettings;

    public LitePushConsumerImpl(LitePushConsumerBuilderImpl builder) {
        super(builder.clientConfiguration, builder.consumerGroup, builder.subscriptionExpressions,
            builder.messageListener, builder.maxCacheMessageCount, builder.maxCacheMessageSizeInBytes,
            builder.consumptionThreadCount, false);
        this.litePushConsumerSettings = new LitePushConsumerSettings(builder, clientId, endpoints);
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();
        syncAllScheduledFuture = getScheduler().scheduleWithFixedDelay(() -> {
            try {
                syncAllLiteSubscription();
            } catch (Throwable t) {
                log.error("Schedule syncAllLiteSubscription error, clientId={}", clientId, t);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    @Override
    protected void shutDown() throws InterruptedException {
        super.shutDown();
        if (null != syncAllScheduledFuture) {
            syncAllScheduledFuture.cancel(false);
        }
    }

    @Override
    public void subscribeLite(String liteTopic) throws ClientException {
        checkRunning();
        if (litePushConsumerSettings.containsLiteTopic(liteTopic)) {
            return;
        }
        validateLiteTopic(liteTopic);
        checkLiteSubscriptionQuota(1);
        ListenableFuture<Void> future =
            syncLiteSubscription(LiteSubscriptionAction.PARTIAL_ADD, Collections.singleton(liteTopic));
        try {
            handleClientFuture(future);
        } catch (ClientException e) {
            log.error("Failed to subscribeLite {}", liteTopic, e);
            throw e;
        }
        litePushConsumerSettings.addLiteTopic(liteTopic);
        log.info("SubscribeLite {}, topic={}, group={}, clientId={}",
            liteTopic, litePushConsumerSettings.bindTopic.getName(), getConsumerGroup(), clientId);
    }

    private void checkLiteSubscriptionQuota(int delta) throws LiteSubscriptionQuotaExceededException {
        int quota = litePushConsumerSettings.getLiteSubscriptionQuota();
        if (litePushConsumerSettings.getLiteTopicSetSize() + delta > quota) {
            throw new LiteSubscriptionQuotaExceededException(
                Code.LITE_SUBSCRIPTION_QUOTA_EXCEEDED_VALUE, null, "Lite subscription quota exceeded " + quota);
        }
    }

    private void validateLiteTopic(String liteTopic) {
        if (StringUtils.isBlank(liteTopic)) {
            throw new IllegalArgumentException("liteTopic is blank");
        }
        if (liteTopic.length() > litePushConsumerSettings.getMaxLiteTopicSize()) {
            String errorMessage = String.format("liteTopic length exceeded max length %d, liteTopic: %s",
                litePushConsumerSettings.getMaxLiteTopicSize(), liteTopic);
            throw new IllegalArgumentException(errorMessage);
        }
    }

    @Override
    public void unsubscribeLite(String liteTopic) throws ClientException {
        checkRunning();
        if (!litePushConsumerSettings.containsLiteTopic(liteTopic)) {
            return;
        }
        ListenableFuture<Void> future =
            syncLiteSubscription(LiteSubscriptionAction.PARTIAL_REMOVE, Collections.singleton(liteTopic));
        try {
            handleClientFuture(future);
        } catch (ClientException e) {
            log.error("Failed to unsubscribeLite {}", liteTopic, e);
            throw e;
        }
        litePushConsumerSettings.removeLiteTopic(liteTopic);
        log.info("UnsubscribeLite {}, topic={}, group={}, clientId={}",
            liteTopic, litePushConsumerSettings.bindTopic.getName(), getConsumerGroup(), clientId);
    }

    @Override
    public Set<String> getLiteTopicSet() {
        return litePushConsumerSettings.getLiteTopicSet();
    }

    @VisibleForTesting
    protected void syncAllLiteSubscription() throws ClientException {
        checkLiteSubscriptionQuota(0);
        final Set<String> set = litePushConsumerSettings.getLiteTopicSet();
        ListenableFuture<Void> future = syncLiteSubscription(LiteSubscriptionAction.COMPLETE_ADD, set);
        handleClientFuture(future);
    }

    protected ListenableFuture<Void> syncLiteSubscription(LiteSubscriptionAction action, Collection<String> diff) {
        SyncLiteSubscriptionRequest request = SyncLiteSubscriptionRequest.newBuilder()
            .setAction(action)
            .setTopic(litePushConsumerSettings.bindTopic.toProtobuf())
            .setGroup(litePushConsumerSettings.group.toProtobuf())
            .addAllLiteTopicSet(diff)
            .build();
        Endpoints endpoints = getEndpoints();
        return syncLiteSubscription0(endpoints, request);
    }

    protected ListenableFuture<Void> syncLiteSubscription0(Endpoints endpoints, SyncLiteSubscriptionRequest request) {
        final Duration requestTimeout = clientConfiguration.getRequestTimeout();
        RpcFuture<SyncLiteSubscriptionRequest, SyncLiteSubscriptionResponse> future =
            this.getClientManager().syncLiteSubscription(endpoints, request, requestTimeout);

        return Futures.transformAsync(future, response -> {
            final Status status = response.getStatus();
            StatusChecker.check(status, future);
            return Futures.immediateVoidFuture();
        }, MoreExecutors.directExecutor());
    }

    @Override
    public void onNotifyUnsubscribeLiteCommand(Endpoints endpoints, NotifyUnsubscribeLiteCommand command) {
        String liteTopic = command.getLiteTopic();

        log.info("notify unsubscribe lite liteTopic={} group={} bindTopic={}",
            liteTopic, getConsumerGroup(), getSettings().bindTopic);

        if (StringUtils.isBlank(liteTopic)) {
            return;
        }

        litePushConsumerSettings.removeLiteTopic(liteTopic);
    }

    @Override
    public LitePushConsumerSettings getSettings() {
        return litePushConsumerSettings;
    }

    @Override
    ReceiveMessageRequest wrapReceiveMessageRequest(int batchSize, MessageQueueImpl mq,
        FilterExpression filterExpression, Duration longPollingTimeout, String attemptId) {
        attemptId = null == attemptId ? UUID.randomUUID().toString() : attemptId;
        return ReceiveMessageRequest.newBuilder()
            .setGroup(getProtobufGroup())
            .setMessageQueue(mq.toProtobuf())
            .setLongPollingTimeout(Durations.fromNanos(longPollingTimeout.toNanos()))
            .setBatchSize(batchSize)
            .setAttemptId(attemptId)
            .setAutoRenew(true)
            .build();
    }

    @VisibleForTesting
    protected void checkRunning() {
        if (!this.isRunning()) {
            log.error("lite push consumer not running, state={}, clientId={}",
                this.state(), clientId);
            throw new IllegalStateException("lite push consumer not running");
        }
    }
}