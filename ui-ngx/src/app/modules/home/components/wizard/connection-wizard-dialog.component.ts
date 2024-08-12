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

import { Component, Inject, ViewChild } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { select, Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { FormBuilder, FormGroup, UntypedFormGroup, Validators } from '@angular/forms';
import { DialogComponent } from '@shared/components/dialog.component';
import { Router } from '@angular/router';
import { MatStepper, StepperOrientation } from '@angular/material/stepper';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { StepperSelectionEvent } from '@angular/cdk/stepper';
import { BreakpointObserver } from '@angular/cdk/layout';
import { MediaBreakpoints, ValueType } from '@shared/models/constants';
import { deepTrim, isDefinedAndNotNull, isNotEmptyStr, isObject } from '@core/utils';
import { CredentialsType } from '@shared/models/credentials.model';
import { ClientCredentialsService } from '@core/http/client-credentials.service';
import { ClientType, clientTypeTranslationMap } from '@shared/models/client.model';
import {
  AboveSecWebSocketTimeUnit,
  clientCredentialsNameRandom,
  clientIdRandom,
  clientUserNameRandom,
  connectionName,
  DataSizeUnitType,
  DataSizeUnitTypeTranslationMap,
  MqttVersions,
  TimeUnitTypeTranslationMap,
  WebSocketConnection,
  WebSocketConnectionConfiguration,
  WebSocketTimeUnit,
  WsAddressProtocolType,
  WsCredentialsGeneratortTypeTranslationMap,
  WsCredentialsGeneratorType
} from '@shared/models/ws-client.model';
import { getCurrentAuthUser, selectUserDetails } from '@core/auth/auth.selectors';
import { ConfigParams } from '@shared/models/config.model';
import { BasicClientCredentials } from '@home/pages/client-credentials/client-credentials.component';
import { WebSocketConnectionService } from '@core/http/ws-connection.service';
import { ConnectivitySettings, connectivitySettingsKey } from '@shared/models/settings.models';
import { SettingsService } from '@core/http/settings.service';

export interface ConnectionDialogData {
  entity?: WebSocketConnection;
  connectionsTotal?: number;
}

@Component({
  selector: 'tb-connection-wizard',
  templateUrl: './connection-wizard-dialog.component.html',
  styleUrls: ['./connection-wizard-dialog.component.scss']
})
export class ConnectionWizardDialogComponent extends DialogComponent<ConnectionWizardDialogComponent, WebSocketConnection> {

  @ViewChild('addConnectionWizardStepper', {static: true}) addConnectionWizardStepper: MatStepper;

  connectionFormGroup: UntypedFormGroup;
  connectionAdvancedFormGroup: UntypedFormGroup;
  lastWillFormGroup: UntypedFormGroup;
  userPropertiesFormGroup: UntypedFormGroup;

  credentialsType = CredentialsType;
  credentialsGeneratorType = WsCredentialsGeneratorType.AUTO;

  clientType = ClientType;
  clientTypeTranslationMap = clientTypeTranslationMap;

  wsAddressProtocolType = WsAddressProtocolType;
  wsCredentialsGeneratorType = WsCredentialsGeneratorType;
  wsCredentialsGeneratortTypeTranslationMap = WsCredentialsGeneratortTypeTranslationMap;

  timeUnitTypes = Object.keys(WebSocketTimeUnit);
  keepAliveTimeUnitTypes = Object.keys(AboveSecWebSocketTimeUnit);
  timeUnitTypeTranslationMap = TimeUnitTypeTranslationMap;
  dataSizeUnitTypes = Object.keys(DataSizeUnitType);
  dataSizeUnitTypeTranslationMap = DataSizeUnitTypeTranslationMap;

  title = 'ws-client.connections.add-connection';
  connection: WebSocketConnection;
  passwordRequired: boolean;
  mqttVersions = MqttVersions;
  mqttVersion: number;
  stepperOrientation: Observable<StepperOrientation>;
  stepperLabelPosition: Observable<'bottom' | 'end'>;
  selectedIndex = 0;
  showNext = true;
  addressProtocol = WsAddressProtocolType.WS;
  displayUrlWarning: boolean;

  private urlConfig = {
    [WsAddressProtocolType.WS]: {
      port: 8084,
      protocol: 'ws://',
      host: window.location.hostname
    },
    [WsAddressProtocolType.WSS]: {
      port: 8085,
      protocol: 'wss://',
      host: window.location.hostname
    }
  };
  private readonly urlPrefix: string = '/mqtt';
  private webSocketSettings: ConnectivitySettings;

  constructor(protected store: Store<AppState>,
              protected router: Router,
              public dialogRef: MatDialogRef<ConnectionWizardDialogComponent, WebSocketConnection>,
              private clientCredentialsService: ClientCredentialsService,
              private webSocketConnectionService: WebSocketConnectionService,
              private breakpointObserver: BreakpointObserver,
              private settingsService: SettingsService,
              private fb: FormBuilder,
              @Inject(MAT_DIALOG_DATA) public data: ConnectionDialogData) {
    super(store, router, dialogRef);
    this.connection = this.data.entity;
    this.iniForms();
    this.init();
  }

  private init() {
    this.stepperOrientation = this.breakpointObserver.observe(MediaBreakpoints['gt-sm'])
      .pipe(map(({matches}) => matches ? 'horizontal' : 'vertical'));
    this.stepperLabelPosition = this.breakpointObserver.observe(MediaBreakpoints['gt-sm'])
      .pipe(map(({matches}) => matches ? 'end' : 'bottom'));
    this.store.pipe(
      select(selectUserDetails),
      map((user) => user?.additionalInfo?.config))
      .pipe(map((data) => {
        this.urlConfig[WsAddressProtocolType.WS].port = data[ConfigParams.wsPort];
        this.urlConfig[WsAddressProtocolType.WSS].port = data[ConfigParams.wssPort];
        return data;
      })).subscribe();
    if (this.connection) {
      this.title = 'ws-client.connections.edit';
      if (isDefinedAndNotNull(this.connection.configuration.clientCredentialsId)) {
        this.credentialsGeneratorType = WsCredentialsGeneratorType.EXISTING;
        this.onCredentialsGeneratorChange(WsCredentialsGeneratorType.EXISTING);
      } else {
        this.credentialsGeneratorType = WsCredentialsGeneratorType.CUSTOM;
        this.onCredentialsGeneratorChange(WsCredentialsGeneratorType.CUSTOM);
      }
    }
    this.checkUrl();
  }

  private iniForms() {
    this.setConnectionFormGroup();
    this.setConnectionAdvancedFormGroup();
    this.setLastWillFormGroup();
    this.setUserPropertiesFormGroup();
    this.getWebSocketSettings();
  }

  private setConnectionFormGroup() {
    const entity: WebSocketConnection = this.connection;
    this.connectionFormGroup = this.fb.group({
      name: [entity ? entity.name : connectionName(this.data.connectionsTotal + 1), [Validators.required]],
      url: [entity ? entity.configuration.url : this.getUrl(this.addressProtocol, this.urlConfig[this.addressProtocol].host, this.urlConfig[this.addressProtocol].port), [Validators.required]],
      rejectUnauthorized: [entity ? entity.configuration.rejectUnauthorized : true, []],
      credentialsName: [{
        value: clientCredentialsNameRandom(),
        disabled: true
      }, [Validators.required]],
      clientId: [{value: entity ? entity.configuration.clientId : clientIdRandom(), disabled: true}, [Validators.required]],
      username: [{value: entity ? entity.configuration.username : clientUserNameRandom(), disabled: true}, []],
      password: [null, []],
      clientCredentials: [entity ? entity.configuration.clientCredentialsId : null, []],
      clientCredentialsId: [null, []]
    });
    this.connectionFormGroup.get('clientCredentials').valueChanges.subscribe(
      credentials => {
        if (credentials?.credentialsValue) {
          const credentialsValue = JSON.parse(credentials.credentialsValue);
          this.connectionFormGroup.patchValue({
            clientId: credentialsValue.clientId || this.connection?.configuration?.clientId || clientIdRandom(),
            username: credentialsValue.userName,
            password: null
          });
          if (isDefinedAndNotNull(credentialsValue.password)) {
            this.passwordRequired = true;
            this.connectionFormGroup.get('password').setValidators([Validators.required]);
          } else {
            this.passwordRequired = false;
            this.connectionFormGroup.get('password').clearValidators();
          }
          if (isDefinedAndNotNull(credentialsValue.clientId)) {
            this.connectionFormGroup.get('clientId').disable();
          } else {
            this.connectionFormGroup.get('clientId').enable();
          }
        } else {
          this.passwordRequired = false;
          this.connectionFormGroup.get('password').clearValidators();
        }
        this.connectionFormGroup.get('password').updateValueAndValidity();
      }
    );
    this.connectionFormGroup.get('url').valueChanges.subscribe(() => this.checkUrl());
    if (entity?.configuration?.url?.includes('wss://')) {
      this.addressProtocol = WsAddressProtocolType.WSS;
    }
  }

  private setConnectionAdvancedFormGroup() {
    const entity: WebSocketConnection = this.connection;
    this.connectionAdvancedFormGroup = this.fb.group({
      clean: [entity ? entity.configuration.cleanStart : true, []],
      keepalive: [entity ? entity.configuration.keepAlive : 60, [Validators.required]],
      keepaliveUnit: [entity ? entity.configuration.keepAliveUnit : WebSocketTimeUnit.SECONDS, []],
      connectTimeout: [entity ? entity.configuration.connectTimeout : 30 * 1000, [Validators.required]],
      connectTimeoutUnit: [entity ? entity.configuration.connectTimeoutUnit : WebSocketTimeUnit.MILLISECONDS, []],
      reconnectPeriod: [entity ? entity.configuration.reconnectPeriod : 1000, [Validators.required]],
      reconnectPeriodUnit: [entity ? entity.configuration.reconnectPeriodUnit : WebSocketTimeUnit.MILLISECONDS, []],
      protocolVersion: [entity ? entity.configuration.mqttVersion : 5, []],
      properties: this.fb.group({
        sessionExpiryInterval: [entity ? entity.configuration.sessionExpiryInterval : 0, []],
        sessionExpiryIntervalUnit: [entity ? entity.configuration.sessionExpiryIntervalUnit : WebSocketTimeUnit.SECONDS, []],
        maximumPacketSize: [entity ? entity.configuration.maxPacketSize : 256, []],
        maximumPacketSizeUnit: [entity ? entity.configuration.maxPacketSizeUnit : DataSizeUnitType.MEGABYTE, []],
        topicAliasMaximum: [entity ? entity.configuration.topicAliasMax : 0, []],
        receiveMaximum: [entity ? entity.configuration.receiveMax : 65535, []],
        requestResponseInfo: [entity ? entity.configuration.requestResponseInfo : false, []],
      })
    });
    this.mqttVersion = this.connectionAdvancedFormGroup.get('protocolVersion').value;
    setTimeout(() => {
      this.connectionAdvancedFormGroup.get('protocolVersion').patchValue(this.connectionAdvancedFormGroup.get('protocolVersion').value, {emitEvent: true});
    }, 0);
    this.connectionAdvancedFormGroup.get('protocolVersion').valueChanges.subscribe((version) => {
      this.mqttVersion = version;
      const properties = this.connectionAdvancedFormGroup.get('properties') as FormGroup;
      Object.keys(properties.controls).forEach(key => {
        if (version !== 5) {
          properties.controls[key].disable();
        } else {
          properties.controls[key].enable();
        }
        properties.controls[key].updateValueAndValidity();
      });
    });
  }

  private setLastWillFormGroup() {
    this.lastWillFormGroup = this.fb.group({
      lastWillMsg: [this.connection?.configuration?.lastWillMsg?.topic?.length ? this.connection.configuration.lastWillMsg : null, []]
    });
  }

  private setUserPropertiesFormGroup() {
    this.userPropertiesFormGroup = this.fb.group({
        userProperties: [this.connection?.configuration?.userProperties ? this.connection.configuration.userProperties : null, []]
      }
    );
  }

  onAddressProtocolChange(type: WsAddressProtocolType) {
    this.addressProtocol = type;
    const url = this.getUrl(type, this.urlConfig[type].host, this.urlConfig[type].port);
    if (type === WsAddressProtocolType.WS) {
      this.connectionFormGroup.get('rejectUnauthorized').patchValue(true);
    }
    this.setUrl(url);
    this.checkUrl();
  }

  onCredentialsGeneratorChange(value: WsCredentialsGeneratorType) {
    this.credentialsGeneratorType = value;
    if (value === WsCredentialsGeneratorType.AUTO) {
      this.connectionFormGroup.get('credentialsName').patchValue(clientCredentialsNameRandom());
      this.connectionFormGroup.get('credentialsName').disable();
      this.connectionFormGroup.get('clientId').patchValue(clientIdRandom());
      this.connectionFormGroup.get('clientId').disable();
      this.connectionFormGroup.get('username').patchValue(clientUserNameRandom());
      this.connectionFormGroup.get('username').disable();
      this.connectionFormGroup.get('password').disable();
      this.connectionFormGroup.get('password').patchValue(null);
      this.connectionFormGroup.get('clientCredentials').patchValue(null);
      this.connectionFormGroup.get('clientCredentialsId').patchValue(null);
      this.connectionFormGroup.get('clientCredentials').clearValidators();
      this.connectionFormGroup.get('clientCredentials').updateValueAndValidity();
    }
    if (value === WsCredentialsGeneratorType.CUSTOM) {
      this.connectionFormGroup.get('clientId').enable();
      this.connectionFormGroup.get('clientId').patchValue(this.connection?.configuration?.clientId ? this.connection.configuration.clientId : clientIdRandom());
      this.connectionFormGroup.get('username').enable();
      this.connectionFormGroup.get('username').patchValue(this.connection?.configuration.username ? this.connection.configuration.username : null);
      this.connectionFormGroup.get('password').enable();
      this.connectionFormGroup.get('password').patchValue(null);
      this.connectionFormGroup.get('clientCredentialsId').patchValue(null);
      this.connectionFormGroup.get('clientCredentials').patchValue(null);
      this.connectionFormGroup.get('clientCredentials').clearValidators();
      this.connectionFormGroup.get('clientCredentials').updateValueAndValidity();
    }
    if (value === WsCredentialsGeneratorType.EXISTING) {
      this.connectionFormGroup.get('credentialsName').enable();
      this.connectionFormGroup.get('clientId').patchValue(null);
      this.connectionFormGroup.get('clientId').disable();
      this.connectionFormGroup.get('username').patchValue(null);
      this.connectionFormGroup.get('username').disable();
      this.connectionFormGroup.get('password').patchValue(null);
      this.connectionFormGroup.get('password').enable();
      this.connectionFormGroup.get('clientCredentials').setValidators(Validators.required);
    }
    this.connectionFormGroup.updateValueAndValidity();
  }

  cancel(): void {
    this.dialogRef.close(null);
  }

  previousStep(): void {
    this.addConnectionWizardStepper.previous();
  }

  nextStep(): void {
    this.addConnectionWizardStepper.next();
  }

  getFormLabel(index: number): string {
    switch (index) {
      case 0:
        return;
      case 1:
        return 'ws-client.connections.advanced-settings';
      case 2:
        return 'ws-client.last-will.last-will';
      case 3:
        return 'retained-message.user-properties';
    }
  }

  get maxStepperIndex(): number {
    return this.addConnectionWizardStepper?._steps?.length - 1;
  }

  save(): void {
    if (this.allValid()) {
      if (this.credentialsGeneratorType === WsCredentialsGeneratorType.AUTO) {
        const name = this.connectionFormGroup.get('credentialsName').value;
        const clientId = this.connectionFormGroup.get('clientId').value;
        const username = this.connectionFormGroup.get('username').value;
        const newCredentials = new BasicClientCredentials(name, clientId, username);
        this.clientCredentialsService.saveClientCredentials(newCredentials).subscribe(
          credentials => {
            this.connectionFormGroup.get('clientCredentials').patchValue(credentials);
            this.saveWebSocketConnection().subscribe(
              (entity) => {
                entity.configuration.password = this.connectionFormGroup.get('password').value;
                return this.dialogRef.close(entity);
              }
            );
          }
        );
      } else {
        this.saveWebSocketConnection().subscribe(
          (entity) => {
            entity.configuration.password = this.connectionFormGroup.get('password').value;
            return this.dialogRef.close(entity);
          }
        );
      }
    }
  }

  private saveWebSocketConnection(): Observable<WebSocketConnection> {
    const connectionFormGroupValue = {
      ...this.connectionFormGroup.getRawValue(),
      ...this.connectionAdvancedFormGroup.getRawValue(),
      ...this.lastWillFormGroup.getRawValue(),
      ...this.userPropertiesFormGroup.getRawValue()
    };
    const connection: WebSocketConnection = this.transformValues(deepTrim(connectionFormGroupValue));
    return this.webSocketConnectionService.saveWebSocketConnection(connection).pipe(
      catchError(e => {
        this.addConnectionWizardStepper.selectedIndex = 0;
        return throwError(e);
      })
    );
  }

  private transformValues(formValues: any): WebSocketConnection {
    const entity = {} as WebSocketConnection;
    const config = {} as WebSocketConnectionConfiguration;
    entity.name = formValues.name;
    entity.createdTime = this.connection?.createdTime;
    if (isDefinedAndNotNull(this.connection)) {
      entity.id = this.connection.id;
      entity.userId = this.connection.userId;
    } else {
      entity.userId = getCurrentAuthUser(this.store).userId;
    }
    config.url = formValues.url;
    config.rejectUnauthorized = formValues.rejectUnauthorized;
    config.clientCredentialsId = isDefinedAndNotNull(formValues.clientCredentials) ? formValues.clientCredentials.id : null;
    config.clientId = formValues.clientId || this.connection?.configuration?.clientId || clientIdRandom();
    config.username = formValues.username;
    config.passwordRequired = !!formValues.password?.length;
    config.cleanStart = formValues.clean;
    config.keepAlive = formValues.keepalive;
    config.connectTimeout = formValues.connectTimeout;
    config.reconnectPeriod = formValues.reconnectPeriod;
    config.sessionExpiryInterval = formValues.properties?.sessionExpiryInterval;
    config.maxPacketSize = formValues.properties?.maximumPacketSize;
    config.topicAliasMax = formValues.properties?.topicAliasMaximum;
    config.receiveMax = formValues.properties?.receiveMaximum;
    config.requestResponseInfo = formValues.properties?.requestResponseInfo;
    // config.requestProblemInfo = formValues.properties?.requestProblemInformation;
    config.mqttVersion = formValues.protocolVersion;
    config.keepAliveUnit = formValues.keepaliveUnit;
    config.connectTimeoutUnit = formValues.connectTimeoutUnit;
    config.reconnectPeriodUnit = formValues.reconnectPeriodUnit;
    config.sessionExpiryIntervalUnit = formValues.properties?.sessionExpiryIntervalUnit;
    config.maxPacketSizeUnit = formValues.properties?.maximumPacketSizeUnit;
    config.userProperties = formValues.userProperties;
    if (isNotEmptyStr(formValues.lastWillMsg?.topic)) {
      config.lastWillMsg = {};
      config.lastWillMsg.topic = formValues.lastWillMsg.topic;
      config.lastWillMsg.qos = formValues.lastWillMsg.qos;
      config.lastWillMsg.payload = isObject(formValues.lastWillMsg.payload) ? JSON.stringify(formValues.lastWillMsg.payload) : formValues.lastWillMsg.payload;
      config.lastWillMsg.payloadType = isObject(formValues.lastWillMsg.payload) ? ValueType.JSON : ValueType.STRING;
      config.lastWillMsg.retain = formValues.lastWillMsg.retain;
      config.lastWillMsg.payloadFormatIndicator = formValues.lastWillMsg.payloadFormatIndicator;
      config.lastWillMsg.contentType = formValues.lastWillMsg.contentType;
      config.lastWillMsg.msgExpiryInterval = formValues.lastWillMsg.msgExpiryInterval;
      config.lastWillMsg.msgExpiryIntervalUnit = formValues.lastWillMsg.msgExpiryIntervalUnit;
      config.lastWillMsg.willDelayInterval = formValues.lastWillMsg.willDelayInterval;
      config.lastWillMsg.willDelayIntervalUnit = formValues.lastWillMsg.willDelayIntervalUnit;
      config.lastWillMsg.responseTopic = formValues.lastWillMsg.responseTopic;
      config.lastWillMsg.correlationData = formValues.lastWillMsg.correlationData;
    }
    entity.configuration = config;
    return entity;
  }

  allValid(): boolean {
    return !this.addConnectionWizardStepper.steps.find((item, index) => {
      if (item.stepControl.invalid) {
        item.interacted = true;
        this.addConnectionWizardStepper.selectedIndex = index;
        return true;
      } else {
        return false;
      }
    });
  }

  changeStep($event: StepperSelectionEvent): void {
    this.selectedIndex = $event.selectedIndex;
    this.showNext = this.selectedIndex !== this.maxStepperIndex;
  }

  regenerate(type: string) {
    switch (type) {
      case 'name':
        this.connectionFormGroup.patchValue({
          credentialsName: clientCredentialsNameRandom()
        });
        break;
      case 'clientId':
        this.connectionFormGroup.patchValue({
          clientId: clientIdRandom()
        });
        break;
      case 'username':
        this.connectionFormGroup.patchValue({
          username: clientUserNameRandom()
        });
        break;
    }
  }

  calcMax(type: string): number {
    if (type === 'keepaliveUnit') {
      const unit = this.connectionAdvancedFormGroup.get(type)?.value;
      switch (unit) {
        case WebSocketTimeUnit.MILLISECONDS:
          return 65535000;
        case WebSocketTimeUnit.SECONDS:
          return 65535;
        case WebSocketTimeUnit.MINUTES:
          return 1092;
        case WebSocketTimeUnit.HOURS:
          return 18;
      }
    }
    if (type === 'sessionExpiryIntervalUnit') {
      const unit = this.connectionAdvancedFormGroup.get('properties')?.get(type)?.value;
      switch (unit) {
        case WebSocketTimeUnit.MILLISECONDS:
          return 4294967295000;
        case WebSocketTimeUnit.SECONDS:
          return 4294967295;
        case WebSocketTimeUnit.MINUTES:
          return 71582788;
        case WebSocketTimeUnit.HOURS:
          return 1193046;
      }
    }
    if (type === 'maximumPacketSizeUnit') {
      const unit = this.connectionAdvancedFormGroup.get('properties')?.get(type)?.value;
      switch (unit) {
        case DataSizeUnitType.BYTE:
          return 268435456;
        case DataSizeUnitType.KILOBYTE:
          return 262144;
        case DataSizeUnitType.MEGABYTE:
          return 256;
      }
    }
  }

  private getWebSocketSettings() {
    this.settingsService.getGeneralSettings<ConnectivitySettings>(connectivitySettingsKey).subscribe(
      settings => {
        if (settings?.jsonValue) {
          // @ts-ignore
          this.webSocketSettings = settings.jsonValue;
          if (settings.jsonValue.ws?.enabled) {
            this.urlConfig[WsAddressProtocolType.WS].host = this.webSocketSettings.ws.host;
            this.urlConfig[WsAddressProtocolType.WS].port = this.webSocketSettings.ws.port;
          }
          if (settings.jsonValue.wss?.enabled) {
            this.urlConfig[WsAddressProtocolType.WSS].host = this.webSocketSettings.wss.host;
            this.urlConfig[WsAddressProtocolType.WSS].port = this.webSocketSettings.wss.port;
          }
          if (!this.connection) {
            const url = this.getUrl(this.addressProtocol, this.urlConfig[this.addressProtocol].host, this.urlConfig[this.addressProtocol].port);
            this.setUrl(url);
          }
        }
      },
      () => {}
    );
  }

  private getUrl(type: WsAddressProtocolType, host: string, port: number | string): string {
    return `${this.urlConfig[type].protocol}${host}:${port}${this.urlPrefix}`;
  }

  private setUrl(url: string) {
    this.connectionFormGroup.get('url').patchValue(url);
  }

  private checkUrl() {
    this.displayUrlWarning = this.addressProtocol !== WsAddressProtocolType.WSS && window.location.protocol.includes('https');
  }
}
