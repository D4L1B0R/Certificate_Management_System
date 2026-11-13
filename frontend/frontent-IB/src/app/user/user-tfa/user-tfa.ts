import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-user-tfa',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './user-tfa.html',
  styleUrl: './user-tfa.css',
})
export class UserTfa {
  private auth = inject(AuthService);
  private cdr = inject(ChangeDetectorRef);

  totpEnabled = false;
  totpLoading = false;
  totpError: string | null = null;
  totpOk: string | null = null;

  // setup flow
  setupQrUri: string | null = null;
  setupSecret: string | null = null;
  setupCode = '';

  // disable flow
  disablePassword = '';
  disableCode = '';

  ngOnInit() {
    this.refreshStatus();
  }

  refreshStatus() {
    this.auth.get2faStatus().subscribe({
      next: (res) => {
        this.totpEnabled = res.enabled;
        this.cdr.markForCheck();
      },
      error: () => {
        this.totpEnabled = false;
        this.cdr.markForCheck();
      }
    });
  }

  startSetup() {
    this.totpLoading = true;
    this.totpError = null;
    this.totpOk = null;
    this.auth.setup2fa().subscribe({
      next: (res) => {
        this.setupQrUri = res.qrCodeDataUri;
        this.setupSecret = res.secret;
        this.totpLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.totpError = err?.error?.message || 'Failed to start 2FA setup';
        this.totpLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  confirmSetup() {
    if (!/^\d{6}$/.test(this.setupCode)) {
      this.totpError = 'Code must be 6 digits';
      return;
    }
    this.totpLoading = true;
    this.totpError = null;
    this.auth.verify2faSetup(this.setupCode).subscribe({
      next: () => {
        this.totpOk = '2FA enabled successfully.';
        this.totpEnabled = true;
        this.setupQrUri = null;
        this.setupSecret = null;
        this.setupCode = '';
        this.totpLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.totpError = err?.error?.message || 'Invalid code';
        this.totpLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  cancelSetup() {
    this.setupQrUri = null;
    this.setupSecret = null;
    this.setupCode = '';
    this.totpError = null;
  }

  disable() {
    if (!this.disablePassword) {
      this.totpError = 'Password is required';
      return;
    }
    if (!/^\d{6}$/.test(this.disableCode)) {
      this.totpError = 'Code must be 6 digits';
      return;
    }
    this.totpLoading = true;
    this.totpError = null;
    this.auth.disable2fa(this.disablePassword, this.disableCode).subscribe({
      next: () => {
        this.totpOk = '2FA disabled.';
        this.totpEnabled = false;
        this.disablePassword = '';
        this.disableCode = '';
        this.totpLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.totpError = err?.error?.message || 'Failed to disable 2FA';
        this.totpLoading = false;
        this.cdr.markForCheck();
      }
    });
  }
}
