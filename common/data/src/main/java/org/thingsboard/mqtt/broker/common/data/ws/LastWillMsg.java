/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.mqtt.broker.common.data.ws;

import lombok.Data;

import java.util.concurrent.TimeUnit;

@Data
public class LastWillMsg {

    private String topic;
    private int qos;
    private DataType payloadType;
    private String payload;
    private boolean retain;
    private boolean payloadFormatIndicator;
    private String contentType;
    private int willDelayInterval;
    private TimeUnit willDelayIntervalUnit;
    private int msgExpiryInterval;
    private TimeUnit msgExpiryIntervalUnit;
    private String responseTopic;
    private String correlationData;

}
