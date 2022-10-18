/**
 * Copyright © 2016-2022 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.mqtt.broker.service.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.actors.client.service.subscription.SubscriptionService;
import org.thingsboard.mqtt.broker.adaptor.ProtoConverter;
import org.thingsboard.mqtt.broker.common.data.MqttQoS;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.common.stats.MessagesStats;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos.PublishMsgProto;
import org.thingsboard.mqtt.broker.queue.TbQueueCallback;
import org.thingsboard.mqtt.broker.service.analysis.ClientLogger;
import org.thingsboard.mqtt.broker.service.mqtt.ClientSession;
import org.thingsboard.mqtt.broker.service.mqtt.PublishMsg;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCache;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.processing.downlink.DownLinkProxy;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.stats.timer.PublishMsgProcessingTimerStats;
import org.thingsboard.mqtt.broker.service.subscription.ClientSubscription;
import org.thingsboard.mqtt.broker.service.subscription.Subscription;
import org.thingsboard.mqtt.broker.service.subscription.ValueWithTopicFilter;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MsgDispatcherServiceImpl implements MsgDispatcherService {

    private final SubscriptionService subscriptionService;
    private final StatsManager statsManager;
    private final MsgPersistenceManager msgPersistenceManager;
    private final ClientSessionCache clientSessionCache;
    private final DownLinkProxy downLinkProxy;
    private final ClientLogger clientLogger;
    private final PublishMsgQueuePublisher publishMsgQueuePublisher;

    private MessagesStats producerStats;
    private PublishMsgProcessingTimerStats publishMsgProcessingTimerStats;

    @PostConstruct
    public void init() {
        this.producerStats = statsManager.createMsgDispatcherPublishStats();
        this.publishMsgProcessingTimerStats = statsManager.getPublishMsgProcessingTimerStats();
    }

    @Override
    public void persistPublishMsg(SessionInfo sessionInfo, PublishMsg publishMsg, TbQueueCallback callback) {
        log.trace("[{}] Persisting publish msg [topic:[{}], qos:[{}]].", sessionInfo.getClientInfo().getClientId(), publishMsg.getTopicName(), publishMsg.getQosLevel());
        PublishMsgProto publishMsgProto = ProtoConverter.convertToPublishProtoMessage(sessionInfo, publishMsg);
        producerStats.incrementTotal();
        callback = statsManager.wrapTbQueueCallback(callback, producerStats);
        publishMsgQueuePublisher.sendMsg(publishMsgProto, callback);
    }

    @Override
    public void processPublishMsg(PublishMsgProto publishMsgProto, PublishMsgCallback callback) {
        String senderClientId = ProtoConverter.getClientId(publishMsgProto);

        clientLogger.logEvent(senderClientId, this.getClass(), "Start msg processing");

        Collection<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilters =
                subscriptionService.getSubscriptions(publishMsgProto.getTopicName());
        List<Subscription> msgSubscriptions = convertToSubscriptions(clientSubscriptionWithTopicFilters, senderClientId);

        clientLogger.logEvent(senderClientId, this.getClass(), "Found msg subscribers");

        List<Subscription> persistentSubscriptions = new ArrayList<>();
        long notPersistentMessagesProcessingStartTime = System.nanoTime();
        for (Subscription msgSubscription : msgSubscriptions) {
            if (needToBePersisted(publishMsgProto, msgSubscription)) {
                persistentSubscriptions.add(msgSubscription);
            } else {
                sendToNode(createBasicPublishMsg(msgSubscription, publishMsgProto), msgSubscription);
            }
        }
        if (msgSubscriptions.size() != persistentSubscriptions.size()) {
            publishMsgProcessingTimerStats.logNotPersistentMessagesProcessing(System.nanoTime() - notPersistentMessagesProcessingStartTime, TimeUnit.NANOSECONDS);
        }

        if (!persistentSubscriptions.isEmpty()) {
            long persistentMessagesProcessingStartTime = System.nanoTime();
            msgPersistenceManager.processPublish(publishMsgProto, persistentSubscriptions, callback);
            publishMsgProcessingTimerStats.logPersistentMessagesProcessing(System.nanoTime() - persistentMessagesProcessingStartTime, TimeUnit.NANOSECONDS);
        } else {
            callback.onSuccess();
        }
        clientLogger.logEvent(senderClientId, this.getClass(), "Finished msg processing");
    }

    private List<Subscription> convertToSubscriptions(Collection<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilters,
                                                      String senderClientId) {
        long startTime = System.nanoTime();
        Collection<ValueWithTopicFilter<ClientSubscription>> filteredClientSubscriptions =
                filterClientSubscriptions(clientSubscriptionWithTopicFilters, senderClientId);

        List<Subscription> msgSubscriptions = filteredClientSubscriptions.stream()
                .map(clientSubscription -> {
                    String clientId = clientSubscription.getValue().getClientId();
                    ClientSession clientSession = clientSessionCache.getClientSession(clientId);
                    if (clientSession == null) {
                        log.debug("[{}] Client session not found for existent client subscription.", clientId);
                        return null;
                    }
                    return new Subscription(clientSubscription.getTopicFilter(), clientSubscription.getValue().getQosValue(),
                            clientSession.getSessionInfo(), clientSubscription.getValue().getOptions());
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        publishMsgProcessingTimerStats.logClientSessionsLookup(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
        return msgSubscriptions;
    }

    private boolean isNoLocalOptionMet(ValueWithTopicFilter<ClientSubscription> clientSubscriptionValueWithTopicFilter,
                                       String senderClientId) {
        return clientSubscriptionValueWithTopicFilter.getValue().getClientId().equals(senderClientId)
                && clientSubscriptionValueWithTopicFilter.getValue().getOptions().isNoLocal();
    }

    private Collection<ValueWithTopicFilter<ClientSubscription>> filterClientSubscriptions(
            Collection<ValueWithTopicFilter<ClientSubscription>> clientSubscriptionWithTopicFilters,
            String senderClientId) {

        return clientSubscriptionWithTopicFilters.stream()
                .filter(clientSubsWithTopicFilter -> !isNoLocalOptionMet(clientSubsWithTopicFilter, senderClientId))
                .collect(Collectors.toMap(
                        clientSubsWithTopicFilter -> clientSubsWithTopicFilter.getValue().getClientId(),
                        Function.identity(),
                        this::getSubscriptionWithHigherQos)
                )
                .values();
    }

    private ValueWithTopicFilter<ClientSubscription> getSubscriptionWithHigherQos(ValueWithTopicFilter<ClientSubscription> first,
                                                                                  ValueWithTopicFilter<ClientSubscription> second) {
        return first.getValue().getQosValue() > second.getValue().getQosValue() ? first : second;
    }

    private boolean needToBePersisted(QueueProtos.PublishMsgProto publishMsgProto, Subscription subscription) {
        return subscription.getSessionInfo().isPersistent()
                && subscription.getMqttQoSValue() != MqttQoS.AT_MOST_ONCE.value()
                && publishMsgProto.getQos() != MqttQoS.AT_MOST_ONCE.value();
    }

    private void sendToNode(QueueProtos.PublishMsgProto publishMsgProto, Subscription subscription) {
        String targetServiceId = subscription.getSessionInfo().getServiceId();
        String clientId = subscription.getSessionInfo().getClientInfo().getClientId();
        downLinkProxy.sendBasicMsg(targetServiceId, clientId, publishMsgProto);
    }

    private QueueProtos.PublishMsgProto createBasicPublishMsg(Subscription clientSubscription, QueueProtos.PublishMsgProto publishMsgProto) {
        var minQoSValue = Math.min(clientSubscription.getMqttQoSValue(), publishMsgProto.getQos());
        var retain = clientSubscription.getOptions().isRetain(publishMsgProto);
        return publishMsgProto.toBuilder()
                .setQos(minQoSValue)
                .setRetain(retain)
                .build();
    }
}
