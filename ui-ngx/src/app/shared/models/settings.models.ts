///
/// Copyright © 2016-2024 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

export const smtpPortPattern: RegExp = /^([0-9]{1,4}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])$/;
export const connectivitySettingsKey = 'connectivity';
export const websocketSettingsKey = 'websocket';

export interface AdminSettings<T> {
  key: string;
  jsonValue: T;
}

export declare type SmtpProtocol = 'smtp' | 'smtps';

export interface MailServerSettings {
  showChangePassword: boolean;
  mailFrom: string;
  smtpProtocol: SmtpProtocol;
  smtpHost: string;
  smtpPort: number;
  timeout: number;
  enableTls: boolean;
  username: string;
  changePassword?: boolean;
  password?: string;
  enableProxy: boolean;
  proxyHost: string;
  proxyPort: number;
  proxyUser: string;
  proxyPassword: string;
}

export interface WebsocketSettings {
  isLoggingEnabled: boolean;
}

export type ConnectivityProtocol = 'mqtt' | 'mqtts' | 'ws' | 'wss';

export interface ConnectivityInfo {
  enabled: boolean;
  host: string;
  port: number;
}

export type ConnectivitySettings = Record<ConnectivityProtocol, ConnectivityInfo>;

export interface UserPasswordPolicy {
  minimumLength: number;
  maximumLength: number;
  minimumUppercaseLetters: number;
  minimumLowercaseLetters: number;
  minimumDigits: number;
  minimumSpecialCharacters: number;
  passwordExpirationPeriodDays: number;
  allowWhitespaces: boolean;
  forceUserToResetPasswordIfNotValid: boolean;
}

export interface SecuritySettings {
  passwordPolicy: UserPasswordPolicy;
}
