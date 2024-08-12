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
package org.thingsboard.mqtt.broker.actors.client.service.disconnect;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttReasonCodes;
import io.netty.handler.codec.mqtt.MqttVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.thingsboard.mqtt.broker.actors.client.messages.mqtt.MqttDisconnectMsg;
import org.thingsboard.mqtt.broker.actors.client.state.ClientActorStateInfo;
import org.thingsboard.mqtt.broker.common.data.ClientInfo;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.service.auth.AuthorizationRuleService;
import org.thingsboard.mqtt.broker.service.limits.RateLimitCacheService;
import org.thingsboard.mqtt.broker.service.limits.RateLimitService;
import org.thingsboard.mqtt.broker.service.mqtt.MqttMessageGenerator;
import org.thingsboard.mqtt.broker.service.mqtt.client.event.ClientSessionEventService;
import org.thingsboard.mqtt.broker.service.mqtt.client.session.ClientSessionCtxService;
import org.thingsboard.mqtt.broker.service.mqtt.flow.control.FlowControlService;
import org.thingsboard.mqtt.broker.service.mqtt.keepalive.KeepAliveService;
import org.thingsboard.mqtt.broker.service.mqtt.persistence.MsgPersistenceManager;
import org.thingsboard.mqtt.broker.service.mqtt.will.LastWillService;
import org.thingsboard.mqtt.broker.session.ClientSessionCtx;
import org.thingsboard.mqtt.broker.session.DisconnectReason;
import org.thingsboard.mqtt.broker.session.DisconnectReasonType;
import org.thingsboard.mqtt.broker.util.MqttPropertiesUtil;
import org.thingsboard.mqtt.broker.util.MqttReasonCodeResolver;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DisconnectServiceImpl implements DisconnectService {

    private final KeepAliveService keepAliveService;
    private final LastWillService lastWillService;
    private final ClientSessionCtxService clientSessionCtxService;
    private final MsgPersistenceManager msgPersistenceManager;
    private final ClientSessionEventService clientSessionEventService;
    private final RateLimitService rateLimitService;
    private final MqttMessageGenerator mqttMessageGenerator;
    private final AuthorizationRuleService authorizationRuleService;
    private final FlowControlService flowControlService;
    private final RateLimitCacheService rateLimitCacheService;

    @Override
    public void disconnect(ClientActorStateInfo actorState, MqttDisconnectMsg disconnectMsg) {
        DisconnectReason reason = disconnectMsg.getReason();
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();

        if (sessionCtx.getSessionInfo() == null) {
            if (log.isTraceEnabled()) {
                log.trace("[{}] Session wasn't fully initialized. Disconnect reason - {}.", sessionCtx.getSessionId(), reason);
            }
            closeChannel(sessionCtx);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("[{}][{}][{}] Init client disconnection. Reason - {}.", sessionCtx.getAddress(), sessionCtx.getClientId(), sessionCtx.getSessionId(), reason);
        }

        if (needSendDisconnectToClient(sessionCtx, reason)) {
            MqttReasonCodes.Disconnect code = MqttReasonCodeResolver.disconnect(reason.getType());
            sessionCtx.getChannel().writeAndFlush(mqttMessageGenerator.createDisconnectMsg(code));
        }

        MqttProperties properties = disconnectMsg.getProperties();
        var sessionExpiryInterval = getSessionExpiryInterval(properties);

        try {
            clearClientSession(actorState, disconnectMsg, sessionExpiryInterval);
        } catch (Exception e) {
            log.warn("[{}][{}] Failed to clean client session.", sessionCtx.getClientId(), sessionCtx.getSessionId(), e);
        }

        notifyClientDisconnected(actorState, sessionExpiryInterval);
        rateLimitService.remove(sessionCtx.getClientId());
        authorizationRuleService.evict(sessionCtx.getClientId());
        flowControlService.removeFromMap(sessionCtx.getClientId());
        closeChannel(sessionCtx);

        if (log.isDebugEnabled()) {
            log.debug("[{}][{}] Client disconnected due to {}.", sessionCtx.getClientId(), sessionCtx.getSessionId(), reason);
        }
    }

    private int getSessionExpiryInterval(MqttProperties properties) {
        MqttProperties.IntegerProperty property = MqttPropertiesUtil.getSessionExpiryIntervalProperty(properties);
        if (property != null) {
            return property.value();
        }
        return -1;
    }

    // only for mqtt 5 clients disconnect packet can be sent from server, when client did not send DISCONNECT or did not close the channel and connection was successful
    private boolean needSendDisconnectToClient(ClientSessionCtx sessionCtx, DisconnectReason reason) {
        if (DisconnectReasonType.ON_CHANNEL_CLOSED == reason.getType()) {
            return false;
        }
        return MqttVersion.MQTT_5 == sessionCtx.getMqttVersion() && DisconnectReasonType.ON_DISCONNECT_MSG != reason.getType()
                && !BrokerConstants.FAILED_TO_CONNECT_CLIENT_MSG.equals(reason.getMessage());
    }

    void closeChannel(ClientSessionCtx sessionCtx) {
        try {
            sessionCtx.closeChannel();
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("[{}][{}] Failed to close channel.", sessionCtx.getClientId(), sessionCtx.getSessionId(), e);
            }
        }
    }

    void notifyClientDisconnected(ClientActorStateInfo actorState, int sessionExpiryInterval) {
        if (log.isTraceEnabled()) {
            log.trace("[{}] Executing notifyClientDisconnected", actorState.getClientId());
        }
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        try {
            clientSessionEventService.notifyClientDisconnected(
                    sessionCtx.getSessionInfo().getClientInfo(),
                    actorState.getCurrentSessionId(),
                    sessionExpiryInterval);
        } catch (Exception e) {
            log.warn("[{}][{}][{}] Failed to notify client disconnected.",
                    sessionCtx.getClientId(), sessionCtx.getSessionId(), sessionExpiryInterval, e);
        }
    }

    void clearClientSession(ClientActorStateInfo actorState, MqttDisconnectMsg disconnectMsg, int sessionExpiryInterval) {
        ClientSessionCtx sessionCtx = actorState.getCurrentSessionCtx();
        ClientInfo clientInfo = sessionCtx.getSessionInfo().getClientInfo();
        var disconnectReasonType = disconnectMsg.getReason().getType();

        actorState.getQueuedMessages().clear();

        UUID sessionId = sessionCtx.getSessionId();
        keepAliveService.unregisterSession(sessionId);

        boolean sendLastWill = !DisconnectReasonType.ON_DISCONNECT_MSG.equals(disconnectReasonType);
        var newSessionCleanStart = disconnectMsg.isNewSessionCleanStart();
        lastWillService.removeAndExecuteLastWillIfNeeded(sessionId, sendLastWill, newSessionCleanStart, sessionExpiryInterval);

        if (sessionCtx.getSessionInfo().isPersistent()) {
            processPersistenceDisconnect(sessionCtx, clientInfo, sessionId);
        } else {
            if (!DisconnectReasonType.ON_CONFLICTING_SESSIONS.equals(disconnectReasonType)) {
                rateLimitCacheService.decrementSessionCount();
            }
        }

        clientSessionCtxService.unregisterSession(clientInfo.getClientId());
    }

    void processPersistenceDisconnect(ClientSessionCtx sessionCtx, ClientInfo clientInfo, UUID sessionId) {
        try {
            msgPersistenceManager.stopProcessingPersistedMessages(clientInfo);
            msgPersistenceManager.saveAwaitingQoS2Packets(sessionCtx);
        } catch (Exception e) {
            if (e instanceof TransactionException) {
                log.warn("[{}][{}] Couldn't properly stop processing persisted messages and saving QoS 2 packets.", clientInfo.getClientId(), sessionId);
            } else {
                throw e;
            }
        }
    }

}
