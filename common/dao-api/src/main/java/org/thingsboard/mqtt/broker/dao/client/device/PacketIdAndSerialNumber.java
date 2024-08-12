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
package org.thingsboard.mqtt.broker.dao.client.device;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Getter
@RequiredArgsConstructor
@ToString
public class PacketIdAndSerialNumber implements Serializable {

    @Serial
    private static final long serialVersionUID = 818586488861227176L;

    private final AtomicInteger packetId;
    private final AtomicLong serialNumber;

    public static PacketIdAndSerialNumber newInstance(int packetId, long serialNumber) {
        return new PacketIdAndSerialNumber(
                new AtomicInteger(packetId),
                new AtomicLong(serialNumber));
    }

    public static PacketIdAndSerialNumber initialInstance() {
        return new PacketIdAndSerialNumber(new AtomicInteger(0), new AtomicLong(-1));
    }

    public int incrementAndGetPacketId() {
        return this.packetId.incrementAndGet();
    }

    public boolean compareAndSetPacketId() {
        return this.packetId.compareAndSet(0xffff, 1);
    }

    public long incrementAndGetSerialNumber() {
        return this.serialNumber.incrementAndGet();
    }
}
