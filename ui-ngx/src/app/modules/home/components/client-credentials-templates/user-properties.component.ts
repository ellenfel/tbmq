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

import { ChangeDetectorRef, Component, forwardRef, Input, OnChanges, OnDestroy, OnInit, SimpleChanges } from '@angular/core';
import {
  ControlValueAccessor,
  FormBuilder,
  NG_VALIDATORS,
  NG_VALUE_ACCESSOR,
  UntypedFormArray,
  UntypedFormGroup,
  ValidationErrors,
  Validator
} from '@angular/forms';
import { Subject } from 'rxjs';
import { isDefinedAndNotNull } from '@core/utils';
import { WebSocketUserProperties } from '@shared/models/ws-client.model';
import { coerceBoolean } from '@shared/decorators/coercion';

export interface UserProperties {
  props: UserPropertiesObject[];
}

export interface UserPropertiesObject {
  k: string;
  v: string;
}

@Component({
  selector: 'tb-user-properties',
  templateUrl: './user-properties.component.html',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => UserPropertiesComponent),
      multi: true
    },
    {
      provide: NG_VALIDATORS,
      useExisting: forwardRef(() => UserPropertiesComponent),
      multi: true,
    }],
  styleUrls: ['./user-properties.component.scss']
})
export class UserPropertiesComponent implements ControlValueAccessor, Validator, OnDestroy, OnInit, OnChanges {

  @Input()
  disabled: boolean;

  @Input()
  mqttVersion: number;

  @Input()
  entity: UserProperties;

  @coerceBoolean()
  @Input()
  reset: boolean;

  userPropertiesFormGroup: UntypedFormGroup;

  private destroy$ = new Subject<void>();
  private propagateChange = (v: any) => {
  };

  get userPropertiesFormArray(): UntypedFormArray {
    return this.userPropertiesFormGroup.get('props') as UntypedFormArray;
  }

  constructor(public fb: FormBuilder,
              public cd: ChangeDetectorRef) {
  }

  ngOnInit() {
    const properties = this.entity?.props;
    this.userPropertiesFormGroup = this.fb.group({
      props: this.fb.array([])
    });
    if (isDefinedAndNotNull(properties?.length)) {
      for (let i=0; i < properties.length; i++) {
        const property = properties[i];
        this.userPropertiesFormArray.push(this.fb.group({
          k: [property.k, []],
          v: [property.v, []]
        }));
      }
    } else {
      this.userPropertiesFormArray.push(this.fb.group({
        k: [null, []],
        v: [null, []]
      }));
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    for (const propName of Object.keys(changes)) {
      const change = changes[propName];
      if (!change.firstChange && change.currentValue !== change.previousValue) {
        if (propName === 'reset' && change.currentValue) {
          this.userPropertiesFormArray.clear();
          this.userPropertiesFormArray.push({k: null, v: null});
        }
      }
    }
  }

  addRule(): void {
    this.userPropertiesFormArray.push(this.fb.group({
      k: [null, []],
      v: [null, []]
    }));
    this.cd.markForCheck();
  }

  removeRule(index: number) {
    this.userPropertiesFormArray.removeAt(index);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  registerOnChange(fn: any): void {
    this.propagateChange = fn;
  }

  registerOnTouched(fn: any): void {
  }

  setDisabledState(isDisabled: boolean) {
    this.disabled = isDisabled;
    if (this.disabled) {
      this.userPropertiesFormGroup.disable({emitEvent: false});
    } else {
      this.userPropertiesFormGroup.enable({emitEvent: false});
    }
  }

  validate(): ValidationErrors | null {
    return this.userPropertiesFormGroup.valid ? null : {userProperties: true};
  }

  writeValue(value: UserProperties): void {
    if (isDefinedAndNotNull(value)) {
      this.updateView(value);
    }
    this.userPropertiesFormGroup.valueChanges.subscribe((value) => {
      this.updateView(value);
    });
  }

  private updateView(value: UserProperties) {
    if (isDefinedAndNotNull(value)) {
      this.propagateChange(this.prepareValues(value));
    }
  }

  private prepareValues(value: UserProperties): WebSocketUserProperties {
    const userProperties = {
      props: []
    };
    if (value.props.length) {
      value.props.map(obj => {
        const k = obj?.k;
        const v = obj?.v;
        if (k) {
          userProperties.props.push({k, v});
        }
      });
      return userProperties;
    } else {
      return null;
    }
  }
}

