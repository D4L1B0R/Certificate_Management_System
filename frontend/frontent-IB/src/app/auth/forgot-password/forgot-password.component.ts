import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

@Component({
  selector: 'app-forgot-password',
  imports: [CommonModule, FormsModule],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.css',
})
export class ForgotPasswordComponent {
  private http = inject(HttpClient);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);

  step = 1;
  email = '';
  newPassword = '';
  privateKeyPem = '';
  loading = false;

  async requestRecovery() {
    this.loading = true;
    console.log("STEP", this.step);

    try {
      await firstValueFrom(
        this.http.post('/api/auth/forgot-password', {
          email: this.email
        })
      );
      this.step = 2;
      console.log("STEP", this.step);
      this.cdr.detectChanges();
    } catch (err) {
      console.log("ERROR:", err);
    } finally {
      this.loading = false;
    }
  }

  onFile(event: any) {
    const file = event.target.files[0];

    const reader = new FileReader();
    reader.onload = () => {
      this.privateKeyPem = reader.result as string;
    };
    reader.readAsText(file);
  }

  async resetPassword() {

    const encrypted = await this.http
      .get(`/api/auth/recovery-challenge?email=${this.email}`)
      .toPromise();

    console.log("STEP 1 - RESPONSE:", encrypted);

    const privateKey = await this.importPrivateKey(this.privateKeyPem);

    console.log("STEP 2 - PRIVATE KEY OK");

    let decrypted;

    try {
      decrypted = await this.decrypt(
        (encrypted as any).challenge,
        privateKey
      );
      console.log("STEP 3 - DECRYPT OK:", decrypted);
    } catch (e) {
      console.error("STEP 3 - DECRYPT FAILED:", e);
      return;
    }

    await this.http.post('/api/auth/recover', {
      email: this.email,
      decryptedChallenge: decrypted,
      newPassword: this.newPassword
    }).toPromise();

    alert('Password changed!');

    setTimeout(() => {
      this.router.navigate(['/login']);
    }, 1500);
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

    const binaryString = atob(cipherText);

    const bytes = new Uint8Array([...binaryString].map(c => c.charCodeAt(0)));

    return crypto.subtle.decrypt(
      { name: 'RSA-OAEP' },
      privateKey,
      bytes
    ).then(buf => new TextDecoder().decode(buf));
  }


}
