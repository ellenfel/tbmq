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

import { Component, OnDestroy, OnInit } from '@angular/core';
import { User } from '@shared/models/user.model';
import { PageComponent } from '@shared/components/page.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import {
  AbstractControl,
  UntypedFormBuilder,
  UntypedFormGroup,
  FormGroupDirective,
  ValidationErrors,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { DialogService } from '@core/services/dialog.service';
import { ActivatedRoute } from '@angular/router';
import { ActionNotificationShow } from '@core/notification/notification.actions';
import { Subject } from 'rxjs';
import { isEqual } from '@core/utils';
import { AuthService } from '@core/http/auth.service';
import { UserPasswordPolicy } from '@shared/models/settings.models';

@Component({
  selector: 'tb-security',
  templateUrl: './security.component.html',
  styleUrls: ['./security.component.scss']
})
export class SecurityComponent extends PageComponent implements OnInit, OnDestroy {

  private readonly destroy$ = new Subject<void>();

  changePassword: UntypedFormGroup;

  user: User;
  passwordPolicy: UserPasswordPolicy;

  constructor(protected store: Store<AppState>,
              private route: ActivatedRoute,
              public dialog: MatDialog,
              public dialogService: DialogService,
              public fb: UntypedFormBuilder,
              private authService: AuthService) {
    super(store);
  }

  ngOnInit() {
    this.user = this.route.snapshot.data.user;
    this.buildChangePasswordForm();
    this.loadPasswordPolicy();
  }

  ngOnDestroy() {
    super.ngOnDestroy();
    this.destroy$.next();
    this.destroy$.complete();
  }

  private buildChangePasswordForm() {
    this.changePassword = this.fb.group({
      currentPassword: [''],
      newPassword: ['', Validators.required],
      newPassword2: ['', this.samePasswordValidation(false, 'newPassword')]
    });
  }

  private loadPasswordPolicy() {
    this.authService.getUserPasswordPolicy().subscribe(policy => {
      this.passwordPolicy = policy;
      this.changePassword.get('newPassword').setValidators([
        this.passwordStrengthValidator(),
        this.samePasswordValidation(true, 'currentPassword'),
        Validators.required
      ]);
      this.changePassword.get('newPassword').updateValueAndValidity({emitEvent: false});
    });
  }

  private passwordStrengthValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value: string = control.value;
      const errors: any = {};

      if (this.passwordPolicy.minimumUppercaseLetters > 0 &&
        !new RegExp(`(?:.*?[A-Z]){${this.passwordPolicy.minimumUppercaseLetters}}`).test(value)) {
        errors.notUpperCase = true;
      }

      if (this.passwordPolicy.minimumLowercaseLetters > 0 &&
        !new RegExp(`(?:.*?[a-z]){${this.passwordPolicy.minimumLowercaseLetters}}`).test(value)) {
        errors.notLowerCase = true;
      }

      if (this.passwordPolicy.minimumDigits > 0
        && !new RegExp(`(?:.*?\\d){${this.passwordPolicy.minimumDigits}}`).test(value)) {
        errors.notNumeric = true;
      }

      if (this.passwordPolicy.minimumSpecialCharacters > 0 &&
        !new RegExp(`(?:.*?[\\W_]){${this.passwordPolicy.minimumSpecialCharacters}}`).test(value)) {
        errors.notSpecial = true;
      }

      if (!this.passwordPolicy.allowWhitespaces && /\s/.test(value)) {
        errors.hasWhitespaces = true;
      }

      if (this.passwordPolicy.minimumLength > 0 && value.length < this.passwordPolicy.minimumLength) {
        errors.minLength = true;
      }

      if (!value.length || this.passwordPolicy.maximumLength > 0 && value.length > this.passwordPolicy.maximumLength) {
        errors.maxLength = true;
      }

      return isEqual(errors, {}) ? null : errors;
    };
  }

  private samePasswordValidation(isSame: boolean, key: string): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      const value: string = control.value;
      const keyValue = control.parent?.value[key];

      if (isSame) {
        return value === keyValue ? {samePassword: true} : null;
      }
      return value !== keyValue ? {differencePassword: true} : null;
    };
  }

  onChangePassword(form: FormGroupDirective): void {
    if (this.changePassword.valid) {
      this.authService.changePassword(this.changePassword.get('currentPassword').value,
        this.changePassword.get('newPassword').value, {ignoreErrors: true}).subscribe(() => {
          this.discardChanges(form);
        },
        (error) => {
          if (error.status === 400 && error.error.message === 'Current password doesn\'t match!') {
            this.changePassword.get('currentPassword').setErrors({differencePassword: true});
          } else if (error.status === 400 && error.error.message.startsWith('Password must')) {
            this.loadPasswordPolicy();
          } else if (error.status === 400 && error.error.message.startsWith('Password was already used')) {
            this.changePassword.get('newPassword').setErrors({alreadyUsed: error.error.message});
          } else {
            this.store.dispatch(new ActionNotificationShow({
              message: error.error.message,
              type: 'error',
              target: 'changePassword'
            }));
          }
        });
    } else {
      this.changePassword.markAllAsTouched();
    }
  }

  discardChanges(form: FormGroupDirective, event?: MouseEvent) {
    if (event) {
      event.stopPropagation();
    }
    form.resetForm({
      currentPassword: '',
      newPassword: '',
      newPassword2: ''
    });
  }
}
