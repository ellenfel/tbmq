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
package org.thingsboard.mqtt.broker.dao.model.sql;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.thingsboard.mqtt.broker.common.data.AdminSettings;
import org.thingsboard.mqtt.broker.dao.model.BaseEntity;
import org.thingsboard.mqtt.broker.dao.model.BaseSqlEntity;
import org.thingsboard.mqtt.broker.dao.util.mapping.JsonConverter;

import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.ADMIN_SETTINGS_COLUMN_FAMILY_NAME;
import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.ADMIN_SETTINGS_JSON_VALUE_PROPERTY;
import static org.thingsboard.mqtt.broker.dao.model.ModelConstants.ADMIN_SETTINGS_KEY_PROPERTY;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = ADMIN_SETTINGS_COLUMN_FAMILY_NAME)
public final class AdminSettingsEntity extends BaseSqlEntity<AdminSettings> implements BaseEntity<AdminSettings> {

    @Column(name = ADMIN_SETTINGS_KEY_PROPERTY)
    private String key;

    @Convert(converter = JsonConverter.class)
    @Column(name = ADMIN_SETTINGS_JSON_VALUE_PROPERTY)
    private JsonNode jsonValue;

    public AdminSettingsEntity() {
        super();
    }

    public AdminSettingsEntity(AdminSettings adminSettings) {
        if (adminSettings.getId() != null) {
            this.setId(adminSettings.getId());
        }
        this.setCreatedTime(adminSettings.getCreatedTime());
        this.key = adminSettings.getKey();
        this.jsonValue = adminSettings.getJsonValue();
    }

    @Override
    public AdminSettings toData() {
        AdminSettings adminSettings = new AdminSettings(id);
        adminSettings.setCreatedTime(createdTime);
        adminSettings.setKey(key);
        adminSettings.setJsonValue(jsonValue);
        return adminSettings;
    }

}
