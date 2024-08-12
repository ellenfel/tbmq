/**
 * Copyright © 2016-2024 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.service.subscription;

import com.google.common.collect.Sets;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.exception.SubscriptionTrieClearException;
import org.thingsboard.mqtt.broker.service.stats.StatsManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConcurrentMapSubscriptionTrie<T> implements SubscriptionTrie<T> {

    private final AtomicInteger size;
    private final AtomicLong nodesCount;
    private final Node<T> root = new Node<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Setter
    @Value("${mqtt.subscription-trie.wait-for-clear-lock-ms}")
    private int waitForClearLockMs;

    public ConcurrentMapSubscriptionTrie(StatsManager statsManager) {
        this.size = statsManager.createSubscriptionSizeCounter();
        this.nodesCount = statsManager.createSubscriptionTrieNodesCounter();
    }

    @Override
    public List<ValueWithTopicFilter<T>> get(String topic) {
        if (topic == null) {
            throw new IllegalArgumentException("Topic cannot be null");
        }
        List<ValueWithTopicFilter<T>> result = new ArrayList<>();
        Stack<TopicPosition<T>> topicPositions = new Stack<>();
        topicPositions.add(new TopicPosition<>(BrokerConstants.NULL_CHAR_STR, 0, root));

        while (!topicPositions.isEmpty()) {
            TopicPosition<T> topicPosition = topicPositions.pop();
            if (topicPosition.segmentStartIndex > topic.length()) {
                result.addAll(wrapValuesWithTopicFilter(topicPosition.prevTopicFilter, topicPosition.node.values));

                Node<T> multiLevelWildcardSubs = topicPosition.node.children.get(BrokerConstants.MULTI_LEVEL_WILDCARD);
                if (multiLevelWildcardSubs != null) {
                    String currentTopicFilter = appendSegment(topicPosition.prevTopicFilter, BrokerConstants.MULTI_LEVEL_WILDCARD);
                    result.addAll(wrapValuesWithTopicFilter(currentTopicFilter, multiLevelWildcardSubs.values));
                }
                continue;
            }
            ConcurrentMap<String, Node<T>> childNodes = topicPosition.node.children;
            String segment = getSegment(topic, topicPosition.segmentStartIndex);
            int nextSegmentStartIndex = getNextSegmentStartIndex(topicPosition.segmentStartIndex, segment);

            if (notStartingWith$(topic, topicPosition)) {
                Node<T> multiLevelWildcardSubs = childNodes.get(BrokerConstants.MULTI_LEVEL_WILDCARD);
                if (multiLevelWildcardSubs != null) {
                    String currentTopicFilter = appendSegment(topicPosition.prevTopicFilter, BrokerConstants.MULTI_LEVEL_WILDCARD);
                    result.addAll(wrapValuesWithTopicFilter(currentTopicFilter, multiLevelWildcardSubs.values));
                }
                Node<T> singleLevelWildcardSubs = childNodes.get(BrokerConstants.SINGLE_LEVEL_WILDCARD);
                if (singleLevelWildcardSubs != null) {
                    String currentTopicFilter = appendSegment(topicPosition.prevTopicFilter, BrokerConstants.SINGLE_LEVEL_WILDCARD);
                    topicPositions.add(new TopicPosition<>(currentTopicFilter, nextSegmentStartIndex, singleLevelWildcardSubs));
                }
            }

            Node<T> segmentNode = childNodes.get(segment);
            if (segmentNode != null) {
                String currentTopicFilter = appendSegment(topicPosition.prevTopicFilter, segment);
                topicPositions.add(new TopicPosition<>(currentTopicFilter, nextSegmentStartIndex, segmentNode));
            }
        }
        return result;
    }

    private boolean notStartingWith$(String topic, TopicPosition<T> topicPosition) {
        return topicPosition.segmentStartIndex != 0 || topic.charAt(0) != '$';
    }

    private List<ValueWithTopicFilter<T>> wrapValuesWithTopicFilter(String topicFilter, Collection<T> values) {
        List<ValueWithTopicFilter<T>> result = new ArrayList<>(values.size());
        for (T value : values) {
            result.add(new ValueWithTopicFilter<>(value, topicFilter));
        }
        return result;
    }

    @Override
    public void put(String topicFilter, T val) {
        if (log.isTraceEnabled()) {
            log.trace("Executing put [{}] [{}]", topicFilter, val);
        }
        if (topicFilter == null || val == null) {
            throw new IllegalArgumentException("Topic filter or value cannot be null");
        }
        lock.readLock().lock();
        try {
            put(root, topicFilter, val, 0);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void put(Node<T> x, String key, T val, int segmentStartIndex) {
        if (segmentStartIndex > key.length()) {
            addOrReplace(x.values, val);
        } else {
            String segment = getSegment(key, segmentStartIndex);
            Node<T> nextNode = x.children.computeIfAbsent(segment, s -> {
                nodesCount.incrementAndGet();
                return new Node<>();
            });
            put(nextNode, key, val, getNextSegmentStartIndex(segmentStartIndex, segment));
        }
    }

    private void addOrReplace(Set<T> values, T val) {
        if (!values.add(val)) {
            values.remove(val);
            values.add(val);
        } else {
            size.getAndIncrement();
        }
    }

    @Override
    public boolean delete(String topicFilter, Predicate<T> deletionFilter) {
        if (log.isTraceEnabled()) {
            log.trace("Executing delete [{}]", topicFilter);
        }
        if (topicFilter == null || deletionFilter == null) {
            throw new IllegalArgumentException("Topic filter or deletionFilter cannot be null");
        }
        Node<T> x = getDeleteNode(root, topicFilter, 0);
        if (x != null) {
            Set<T> valuesToDelete = x.values.stream().filter(deletionFilter).collect(Collectors.toSet());
            if (valuesToDelete.isEmpty()) {
                return false;
            }
            if (valuesToDelete.size() > 1) {
                log.error("There are more than one value to delete!");
            }
            boolean deleted = x.values.removeAll(valuesToDelete);
            if (deleted) {
                size.decrementAndGet();
            }
            return deleted;
        }
        return false;
    }

    @Override
    public void clearEmptyNodes() throws SubscriptionTrieClearException {
        if (log.isTraceEnabled()) {
            log.trace("Executing clearEmptyNodes");
        }
        acquireClearTrieLock();
        long nodesBefore = nodesCount.get();
        long clearStartTime = System.currentTimeMillis();
        try {
            clearEmptyChildren(root);
            long nodesAfter = nodesCount.get();
            long clearEndTime = System.currentTimeMillis();
            if (log.isDebugEnabled()) {
                log.debug("Clearing trie took {} ms, cleared {} nodes.",
                        clearEndTime - clearStartTime, nodesBefore - nodesAfter);
            }
        } catch (Exception e) {
            long nodesAfter = nodesCount.get();
            log.error("Failed on clearing empty nodes. Managed to clear {} nodes.", nodesBefore - nodesAfter, e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void acquireClearTrieLock() throws SubscriptionTrieClearException {
        boolean successfullyAcquiredLock = false;
        try {
            successfullyAcquiredLock = lock.writeLock().tryLock(waitForClearLockMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            log.warn("Acquiring lock was interrupted.");
        }
        if (!successfullyAcquiredLock) {
            throw new SubscriptionTrieClearException("Couldn't acquire lock for clearing trie. " +
                    "There are a lot of clients subscribing right now.");
        }
    }

    private boolean clearEmptyChildren(Node<T> node) {
        boolean isNodeEmpty = node.values.isEmpty();
        for (Map.Entry<String, Node<T>> entry : node.children.entrySet()) {
            Node<T> value = entry.getValue();
            boolean isChildEmpty = clearEmptyChildren(value);
            if (isChildEmpty) {
                node.children.remove(entry.getKey());
                nodesCount.decrementAndGet();
            } else {
                isNodeEmpty = false;
            }
        }

        return isNodeEmpty;
    }

    private Node<T> getDeleteNode(Node<T> x, String key, int segmentStartIndex) {
        if (x == null) {
            return null;
        }
        if (segmentStartIndex > key.length()) {
            return x;
        }
        String segment = getSegment(key, segmentStartIndex);
        return getDeleteNode(x.children.get(segment), key, getNextSegmentStartIndex(segmentStartIndex, segment));
    }

    private int getNextSegmentStartIndex(int segmentStartIndex, String segment) {
        return segmentStartIndex + segment.length() + 1;
    }

    private String getSegment(String key, int segmentStartIndex) {
        int nextDelimiterIndex = key.indexOf(BrokerConstants.TOPIC_DELIMITER, segmentStartIndex);

        return nextDelimiterIndex == -1 ?
                key.substring(segmentStartIndex)
                : key.substring(segmentStartIndex, nextDelimiterIndex);
    }

    private String appendSegment(String topicFilter, String segment) {
        if (topicFilter.equals(BrokerConstants.NULL_CHAR_STR)) {
            return segment;
        }
        return topicFilter + BrokerConstants.TOPIC_DELIMITER + segment;
    }

    private static class Node<T> {
        private final ConcurrentMap<String, Node<T>> children = new ConcurrentHashMap<>();
        private final Set<T> values = Sets.newConcurrentHashSet();

        public Node() {
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Node<?> node = (Node<?>) o;
            return children.equals(node.children) &&
                    values.equals(node.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(children, values);
        }
    }

    @AllArgsConstructor
    private static class TopicPosition<T> {
        private final String prevTopicFilter;
        private final int segmentStartIndex;
        private final Node<T> node;
    }

}
