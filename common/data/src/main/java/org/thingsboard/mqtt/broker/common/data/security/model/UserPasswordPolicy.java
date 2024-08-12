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
package org.thingsboard.mqtt.broker.common.data.security.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserPasswordPolicy implements Serializable {

    @Serial
    private static final long serialVersionUID = -8217290530652788398L;

    private Integer minimumLength;
    private Integer maximumLength;
    private Integer minimumUppercaseLetters;
    private Integer minimumLowercaseLetters;
    private Integer minimumDigits;
    private Integer minimumSpecialCharacters;
    private Boolean allowWhitespaces = false;
    private Boolean forceUserToResetPasswordIfNotValid = false;

    private Integer passwordExpirationPeriodDays;
    private Integer passwordReuseFrequencyDays;

}
