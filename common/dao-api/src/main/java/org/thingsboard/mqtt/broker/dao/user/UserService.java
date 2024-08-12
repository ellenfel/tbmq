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
package org.thingsboard.mqtt.broker.dao.user;

import org.thingsboard.mqtt.broker.common.data.User;
import org.thingsboard.mqtt.broker.common.data.page.PageData;
import org.thingsboard.mqtt.broker.common.data.page.PageLink;
import org.thingsboard.mqtt.broker.common.data.security.UserCredentials;

import java.util.UUID;

public interface UserService {

    User saveUser(User user);

    User findUserByEmail(String email);

    User findUserById(UUID userId);

    UserCredentials findUserCredentialsByUserId(UUID userId);

    UserCredentials findUserCredentialsByResetToken(String resetToken);

    UserCredentials saveUserCredentials(UserCredentials userCredentials);

    UserCredentials requestPasswordReset(String email);

    UserCredentials requestExpiredPasswordReset(UUID userCredentialsId);

    UserCredentials replaceUserCredentials(UserCredentials userCredentials);

    void deleteUser(UUID userId);

    PageData<User> findUsers(PageLink pageLink);

    void onUserLoginSuccessful(UUID userId);
}
