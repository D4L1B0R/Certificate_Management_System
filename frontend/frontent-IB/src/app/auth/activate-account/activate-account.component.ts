import { Component, ChangeDetectorRef } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'app-activate-account',
  imports: [FormsModule, CommonModule, MatSnackBarModule],
  templateUrl: './activate-account.component.html',
  styleUrl: './activate-account.component.css',
})
export class ActivateAccountComponent {

  token = '';
  mode: 'password' | 'crypto' = 'password';
  loading = false;
  activationInProgress = false;

  password = '';
  confirmPassword = '';

  privateKeyPem = '';

  private baseUrl = 'https://localhost:8443';

  constructor(
    private route: ActivatedRoute,
    private http: HttpClient,
    private router: Router,
    private snackBar: MatSnackBar,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.token = this.route.snapshot.queryParamMap.get('token') || '';


    const modeParam = this.route.snapshot.queryParamMap.get('mode');
    if (modeParam === 'crypto') {
      this.mode = 'crypto';
    } else {
      this.mode = 'password';
    }

    if (!this.token) {
      this.showError('Invalid activation link');
      this.router.navigate(['/login']);
      return;
    }
  }

  // PASSWORD MODE (CA Users)
  activatePassword() {
    if (this.activationInProgress) {
      console.warn('Activation already in progress');
      return;
    }

    if (this.password.length < 8) {
      this.showError('Password must be at least 8 characters');
      return;
    }

    if (this.password !== this.confirmPassword) {
      this.showError('Passwords do not match');
      return;
    }

    this.activationInProgress = true;
    this.loading = true;

    this.http.post(
      `${this.baseUrl}/api/auth/activate?token=${this.token}&mode=password`,
      { password: this.password }
    ).subscribe({
      next: () => {
        this.loading = false;
        this.activationInProgress = false;
        this.cdr.detectChanges();
        this.showSuccess('Account activated! You can now login.');
        setTimeout(() => this.router.navigate(['/login']), 1500);
      },
      error: (err: any) => {
        console.error('Password activation error full:', err);
        this.loading = false;
        this.activationInProgress = false;
        this.cdr.detectChanges();

        let errorMsg = 'Unknown error';

        if (err?.error?.message) {
          errorMsg = err.error.message;
        } else if (err?.error?.statusText) {
          errorMsg = err.error.statusText;
        } else if (typeof err?.error === 'string') {
          errorMsg = err.error;
        } else if (err?.message) {
          errorMsg = err.message;
        } else if (err?.statusText) {
          errorMsg = err.statusText;
        }

        console.error('Final error message:', errorMsg);
        this.showError('Activation failed: ' + errorMsg);
      }
    });
  }

  async activateCrypto() {
    if (this.activationInProgress) {
      console.warn('Activation already in progress');
      return;
    }

    this.activationInProgress = true;
    this.loading = true;

    try {
      if (!this.privateKeyPem) {
        this.showError('Please upload your private key');
        this.loading = false;
        this.activationInProgress = false;
        return;
      }

      const privateKey = await this.importPrivateKey(this.privateKeyPem);

      const encrypted: any = await firstValueFrom(
        this.http.get(`${this.baseUrl}/api/auth/activation-challenge?token=${this.token}`)
      );

      const decrypted = await this.decrypt(encrypted.challenge, privateKey);

      const response: any = await firstValueFrom(
        this.http.post(
          `${this.baseUrl}/api/auth/activate?token=${this.token}&mode=crypto`,
          { decryptedChallenge: decrypted }
        )
      );

      console.log('Activation successful:', response);

      this.loading = false;
      this.activationInProgress = false;
      this.cdr.detectChanges();
      this.showSuccess('Account activated successfully!');
      setTimeout(() => this.router.navigate(['/login']), 1500);

    } catch (e: any) {
      console.error('Activation error full:', e);
      this.loading = false;
      this.activationInProgress = false;
      this.cdr.detectChanges();


      let errorMsg = 'Unknown error';

      if (e?.error?.message) {
        errorMsg = e.error.message;
      } else if (e?.error?.statusText) {
        errorMsg = e.error.statusText;
      } else if (typeof e?.error === 'string') {
        errorMsg = e.error;
      } else if (e?.message) {
        errorMsg = e.message;
      } else if (e?.statusText) {
        errorMsg = e.statusText;
      }

      console.error('Final error message:', errorMsg);
      this.showError('Activation failed: ' + errorMsg);
    }
  }

  async importPrivateKey(pem: string): Promise<CryptoKey> {
    const clean = pem
      .replace('-----BEGIN PRIVATE KEY-----', '')
      .replace('-----END PRIVATE KEY-----', '')
      .replace(/\s/g, '');

    const binary = atob(clean);
    const bytes = Uint8Array.from(binary, c => c.charCodeAt(0));

    return crypto.subtle.importKey(
      'pkcs8',
      bytes.buffer,
      { name: 'RSA-OAEP', hash: 'SHA-256' },
      false,
      ['decrypt']
    );
  }

  async decrypt(cipherText: string, privateKey: CryptoKey): Promise<string> {
    const encryptedBytes = Uint8Array.from(
      atob(cipherText),
      c => c.charCodeAt(0)
    );

    const decrypted = await crypto.subtle.decrypt(
      { name: 'RSA-OAEP' },
      privateKey,
      encryptedBytes
    );

    return new TextDecoder().decode(decrypted);
  }

  onFileSelected(event: any) {
    const file = event.target.files[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = () => {
      this.privateKeyPem = reader.result as string;
      this.cdr.detectChanges();
    };
    reader.readAsText(file);
  }

  private showSuccess(message: string) {
    this.snackBar.open(message, 'Close', {
      duration: 4000,
      panelClass: ['snackbar-success'],
      horizontalPosition: 'end',
      verticalPosition: 'bottom'
    });
  }

  private showError(message: string) {
    this.snackBar.open(message, 'Close', {
      duration: 4000,
      panelClass: ['snackbar-error'],
      horizontalPosition: 'end',
      verticalPosition: 'bottom'
    });
  }

  isPasswordModeValid(): boolean {
    return this.password.length >= 8 && this.password === this.confirmPassword;
  }

  isCryptoModeValid(): boolean {
    return !!this.privateKeyPem;
  }
}
