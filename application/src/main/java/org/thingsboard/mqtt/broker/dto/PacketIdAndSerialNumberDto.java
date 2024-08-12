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
package org.thingsboard.mqtt.broker.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.thingsboard.mqtt.broker.dao.client.device.PacketIdAndSerialNumber;

@Getter
@RequiredArgsConstructor
public class PacketIdAndSerialNumberDto {

    private final int packetId;
    private final long serialNumber;

    public static PacketIdAndSerialNumberDto incrementAndGet(PacketIdAndSerialNumber packetIdAndSerialNumber) {
        boolean reachedLimit = packetIdAndSerialNumber.compareAndSetPacketId();
        if (reachedLimit) {
            return new PacketIdAndSerialNumberDto(1, packetIdAndSerialNumber.incrementAndGetSerialNumber());
        }
        return new PacketIdAndSerialNumberDto(packetIdAndSerialNumber.incrementAndGetPacketId(), packetIdAndSerialNumber.incrementAndGetSerialNumber());
    }
}
