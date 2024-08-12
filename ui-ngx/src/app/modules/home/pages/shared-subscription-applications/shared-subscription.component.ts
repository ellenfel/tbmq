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

import { ChangeDetectorRef, Component, Inject, ViewChild } from '@angular/core';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { EntityComponent } from '@home/components/entity/entity.component';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { SharedSubscription } from "@shared/models/shared-subscription.model";
import { CopyButtonComponent } from '@shared/components/button/copy-button.component';

@Component({
  selector: 'tb-shared-subscriptions',
  templateUrl: './shared-subscription.component.html',
  styleUrls: ['./shared-subscription.component.scss']
})
export class SharedSubscriptionComponent extends EntityComponent<SharedSubscription> {

  @ViewChild('copyBtn')
  copyBtn: CopyButtonComponent;

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: SharedSubscription,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<SharedSubscription>,
              public fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: SharedSubscription): UntypedFormGroup {
    const form = this.fb.group(
      {
        name: [entity ? entity.name : '', [Validators.required]],
        partitions: [2, [Validators.required, Validators.min(1)]],
        topicFilter: [entity ? entity.topicFilter : '', [Validators.required]]
      }
    );
    return form;
  }

  updateForm(entity: SharedSubscription) {
    this.entityForm.patchValue({name: entity.name} );
    this.entityForm.patchValue({partitions: entity.partitions} );
    this.entityForm.patchValue({topicFilter: entity.topicFilter} );
    this.entityForm.get('partitions').disable();
    this.entityForm.get('topicFilter').disable();
  }

  onClickTbCopyButton(value: string) {
    this.copyBtn.copy(value);
  }
}
