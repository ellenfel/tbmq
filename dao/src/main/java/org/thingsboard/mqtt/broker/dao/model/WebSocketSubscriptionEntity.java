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
package org.thingsboard.mqtt.broker.dao.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLJsonPGObjectJsonbType;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscription;
import org.thingsboard.mqtt.broker.common.data.ws.WebSocketSubscriptionConfiguration;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.util.mapping.JsonConverter;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity

@Table(name = ModelConstants.WEBSOCKET_SUBSCRIPTION_COLUMN_FAMILY_NAME)
public class WebSocketSubscriptionEntity extends BaseSqlEntity<WebSocketSubscription> implements BaseEntity<WebSocketSubscription> {

    @Column(name = ModelConstants.WEBSOCKET_SUBSCRIPTION_CONNECTION_ID_PROPERTY, columnDefinition = "uuid")
    private UUID webSocketConnectionId;

    @Convert(converter = JsonConverter.class)
    @JdbcType(PostgreSQLJsonPGObjectJsonbType.class)
    @Column(name = ModelConstants.WEBSOCKET_SUBSCRIPTION_CONFIGURATION_PROPERTY, columnDefinition = "jsonb")
    private JsonNode configuration;

    public WebSocketSubscriptionEntity() {
    }

    public WebSocketSubscriptionEntity(WebSocketSubscription subscription) {
        if (subscription.getId() != null) {
            this.setId(subscription.getId());
        }
        this.setCreatedTime(subscription.getCreatedTime());
        if (subscription.getWebSocketConnectionId() != null) {
            this.webSocketConnectionId = subscription.getWebSocketConnectionId();
        }
        this.configuration = JacksonUtil.convertValue(subscription.getConfiguration(), ObjectNode.class);
    }

    @Override
    public WebSocketSubscription toData() {
        WebSocketSubscription webSocketSubscription = new WebSocketSubscription();
        webSocketSubscription.setId(id);
        webSocketSubscription.setCreatedTime(createdTime);
        if (webSocketConnectionId != null) {
            webSocketSubscription.setWebSocketConnectionId(webSocketConnectionId);
        }
        webSocketSubscription.setConfiguration(JacksonUtil.convertValue(configuration, WebSocketSubscriptionConfiguration.class));
        return webSocketSubscription;
    }

}
