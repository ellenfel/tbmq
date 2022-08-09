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
package org.thingsboard.mqtt.broker.actors.client.service.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thingsboard.mqtt.broker.common.data.BasicCallback;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;
import org.thingsboard.mqtt.broker.service.subscription.SubscriptionPersistenceService;
import org.thingsboard.mqtt.broker.service.subscription.TopicSubscription;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionProcessor;
import org.thingsboard.mqtt.broker.service.subscription.shared.SharedSubscriptionTopicFilter;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import static org.thingsboard.mqtt.broker.common.data.util.CallbackUtil.createCallback;

@Slf4j
@Service
@RequiredArgsConstructor
// not thread-safe for operations with the same 'clientId'
public class ClientSubscriptionServiceImpl implements ClientSubscriptionService {

    private ConcurrentMap<String, Set<TopicSubscription>> clientSubscriptionsMap;

    private final SubscriptionPersistenceService subscriptionPersistenceService;
    private final SubscriptionService subscriptionService;
    private final SharedSubscriptionProcessor sharedSubscriptionProcessor;
    private final StatsManager statsManager;

    // TODO: sync subscriptions (and probably ClientSession)
    //      - store events for each action in separate topic + sometimes make snapshots (apply events on 'value' sequentially)
    //      - manage subscriptions in one thread and one node (probably merge subscriptions with ClientSession)


    @Override
    public void init(Map<String, Set<TopicSubscription>> clientTopicSubscriptions) {
        this.clientSubscriptionsMap = new ConcurrentHashMap<>(clientTopicSubscriptions);
        statsManager.registerClientSubscriptionsStats(clientSubscriptionsMap);

        log.info("Restoring persisted subscriptions for {} clients.", clientSubscriptionsMap.size());
        clientSubscriptionsMap.forEach((clientId, topicSubscriptions) -> {
            log.trace("[{}] Restoring subscriptions - {}.", clientId, topicSubscriptions);
            subscriptionService.subscribe(clientId, topicSubscriptions);
        });
    }

    @Override
    public void subscribeAndPersist(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        BasicCallback callback = createCallback(
                () -> log.trace("[{}] Persisted topic subscriptions", clientId),
                t -> log.warn("[{}] Failed to persist topic subscriptions. Exception - {}, reason - {}",
                        clientId, t.getClass().getSimpleName(), t.getMessage()));
        subscribeAndPersist(clientId, topicSubscriptions, callback);
    }

    @Override
    public void subscribeAndPersist(String clientId, Collection<TopicSubscription> topicSubscriptions, BasicCallback callback) {
        log.trace("[{}] Subscribing to {}.", clientId, topicSubscriptions);
        Set<TopicSubscription> clientSubscriptions = subscribe(clientId, topicSubscriptions);

        subscriptionPersistenceService.persistClientSubscriptionsAsync(clientId, clientSubscriptions, callback);
    }

    @Override
    public void subscribeInternally(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        log.trace("[{}] Subscribing internally to {}.", clientId, topicSubscriptions);
        subscribe(clientId, topicSubscriptions);
    }

    private Set<TopicSubscription> subscribe(String clientId, Collection<TopicSubscription> topicSubscriptions) {
        subscriptionService.subscribe(clientId, topicSubscriptions);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.computeIfAbsent(clientId, s -> new HashSet<>());
        clientSubscriptions.addAll(topicSubscriptions);
        return clientSubscriptions;
    }

    @Override
    public void unsubscribeAndPersist(String clientId, Collection<String> topicFilters) {
        BasicCallback callback = createCallback(
                () -> log.trace("[{}] Persisted unsubscribed topics", clientId),
                t -> log.warn("[{}] Failed to persist unsubscribed topics. Exception - {}, reason - {}",
                        clientId, t.getClass().getSimpleName(), t.getMessage()));
        unsubscribeAndPersist(clientId, topicFilters, callback);
    }

    @Override
    public void unsubscribeAndPersist(String clientId, Collection<String> topicFilters, BasicCallback callback) {
        log.trace("[{}] Unsubscribing from {}.", clientId, topicFilters);
        Set<TopicSubscription> updatedClientSubscriptions = unsubscribe(clientId, topicFilters);

        subscriptionPersistenceService.persistClientSubscriptionsAsync(clientId, updatedClientSubscriptions, callback);
    }

    @Override
    public void unsubscribeInternally(String clientId, Collection<String> topicFilters) {
        log.trace("[{}] Unsubscribing internally from {}.", clientId, topicFilters);
        unsubscribe(clientId, topicFilters);
    }

    private Set<TopicSubscription> unsubscribe(String clientId, Collection<String> topicFilters) {
        subscriptionService.unsubscribe(clientId, topicFilters);

        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.computeIfAbsent(clientId, s -> new HashSet<>());
        clientSubscriptions.removeIf(topicSubscription -> {
            boolean unsubscribe = topicFilters.contains(topicSubscription.getTopic());
            if (unsubscribe) {
                processSharedUnsubscribe(topicSubscription);
            }
            return unsubscribe;
        });
        return clientSubscriptions;
    }

    @Override
    public void clearSubscriptionsAndPersist(String clientId, BasicCallback callback) {
        log.trace("[{}] Clearing all subscriptions.", clientId);
        clearSubscriptions(clientId);
        subscriptionPersistenceService.persistClientSubscriptionsAsync(clientId, Collections.emptySet(), callback);
    }

    @Override
    public void clearSubscriptionsInternally(String clientId) {
        log.trace("[{}] Clearing all subscriptions internally.", clientId);
        clearSubscriptions(clientId);
    }

    private void clearSubscriptions(String clientId) {
        Set<TopicSubscription> clientSubscriptions = clientSubscriptionsMap.remove(clientId);
        if (clientSubscriptions == null) {
            log.debug("[{}] There were no active subscriptions for client.", clientId);
            return;
        }
        List<String> unsubscribeTopics = clientSubscriptions.stream()
                .peek(this::processSharedUnsubscribe)
                .map(TopicSubscription::getTopic)
                .collect(Collectors.toList());
        subscriptionService.unsubscribe(clientId, unsubscribeTopics);
    }

    @Override
    public Set<TopicSubscription> getClientSubscriptions(String clientId) {
        return new HashSet<>(clientSubscriptionsMap.getOrDefault(clientId, Collections.emptySet()));
    }

    private void processSharedUnsubscribe(TopicSubscription topicSubscription) {
        if (isSharedSubscription(topicSubscription)) {
            unsubscribeSharedSubscription(topicSubscription);
        }
    }

    private boolean isSharedSubscription(TopicSubscription topicSubscription) {
        return !StringUtils.isEmpty(topicSubscription.getShareName());
    }

    private void unsubscribeSharedSubscription(TopicSubscription topicSubscription) {
        sharedSubscriptionProcessor.unsubscribe(getSharedSubscriptionTopicFilter(topicSubscription));
    }

    private SharedSubscriptionTopicFilter getSharedSubscriptionTopicFilter(TopicSubscription topicSubscription) {
        return new SharedSubscriptionTopicFilter(topicSubscription.getTopic(), topicSubscription.getShareName());
    }
}
