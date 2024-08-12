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
package org.thingsboard.mqtt.broker.service.install.data;

import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.common.util.BrokerConstants;
import org.thingsboard.mqtt.broker.common.util.JacksonUtil;
import org.thingsboard.mqtt.broker.dao.client.connectivity.ConnectivityInfo;

import java.util.Map;

public class ConnectivitySettings {

    public static AdminSettings createConnectivitySettings() {
        AdminSettings connectivitySettings = new AdminSettings();
        connectivitySettings.setKey(BrokerConstants.CONNECTIVITY_KEY);

        Map<String, ConnectivityInfo> connectivityInfoMap = Map.of(
                BrokerConstants.MQTT_CONNECTIVITY, new ConnectivityInfo(false, "", "1883"),
                BrokerConstants.MQTTS_CONNECTIVITY, new ConnectivityInfo(false, "", "8883"),
                BrokerConstants.WS_CONNECTIVITY, new ConnectivityInfo(false, "", "8084"),
                BrokerConstants.WSS_CONNECTIVITY, new ConnectivityInfo(false, "", "8085")
        );
        connectivitySettings.setJsonValue(JacksonUtil.valueToTree(connectivityInfoMap));

        return connectivitySettings;
    }

}
