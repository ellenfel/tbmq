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
package org.thingsboard.mqtt.broker.service.limits;

import io.netty.handler.codec.mqtt.MqttMessage;
import org.thingsboard.mqtt.broker.common.data.SessionInfo;
import org.thingsboard.mqtt.broker.gen.queue.QueueProtos;

import java.util.UUID;

public interface RateLimitService {

    /**
     * Rate limits for incoming PUBLISH messages from any publisher with any QoS level
     */
    boolean checkIncomingLimits(String clientId, UUID sessionId, MqttMessage msg);

    /**
     * Rate limits for outgoing PUBLISH messages to non-persistent subscriber with QoS = 0
     */
    boolean checkOutgoingLimits(String clientId, QueueProtos.PublishMsgProto msg);

    void remove(String clientId);

    boolean checkSessionsLimit(String clientId);

    boolean checkApplicationClientsLimit(SessionInfo sessionInfo);

    boolean checkDevicePersistedMsgsLimit();

    long tryConsumeAsMuchAsPossibleDevicePersistedMsgs(long limit);

    boolean isDevicePersistedMsgsLimitEnabled();

    boolean checkTotalMsgsLimit();

    long tryConsumeAsMuchAsPossibleTotalMsgs(long limit);

    boolean isTotalMsgsLimitEnabled();

}
