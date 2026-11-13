import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import {FormBuilder, ReactiveFormsModule, Validators} from '@angular/forms';
import { AuthService } from '../auth.service';
import { AdminService } from '../../services/admin/admin.service';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-register',
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css',
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private adminService = inject(AdminService);

  loading = false;
  error: string | null = null;
  message: string | null = null;
  organizations: any[] = [];


  private emailPattern = '^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$';
  private passwordPattern =
    '^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$';

  private privateKeyPem: string | null = null;

  form = this.fb.group({
    email: ['', [
      Validators.required,
      Validators.pattern(this.emailPattern)
    ]],

    password: ['', [
      Validators.required,
      Validators.minLength(8),
      Validators.pattern(this.passwordPattern)
    ]],

    confirmPassword: ['', Validators.required],

    firstName: ['', Validators.required],

    lastName: ['', Validators.required],

    organizationId: [null, Validators.required],

    publicKey: ['', Validators.required]
  });

  ngOnInit() {
    this.getOrganizations();
  }

  getOrganizations() {
    this.adminService.getOrganizations().subscribe((data: any) => {
      this.organizations = data;
    });
  }

  get passwordMismatch(): boolean {
    return this.form.value.password !== this.form.value.confirmPassword;
  }

  async submit() {

    if (this.form.invalid || this.passwordMismatch) {
      this.form.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.error = null;
    this.message = null;

    try {

      const request = {
        email: this.form.value.email,
        password: this.form.value.password,
        confirmPassword: this.form.value.confirmPassword,
        name: this.form.value.firstName,
        surname: this.form.value.lastName,
        organizationId: this.form.value.organizationId,
        publicKey: this.form.value.publicKey
      };

      await this.auth.register(request);

      this.message =
        'Uspešna registracija. Proverite email radi aktivacije naloga.';

      this.form.reset();

    } catch (e: any) {

      if (e?.status === 409) {
        this.error = 'Korisnik već postoji.';
      }
      else {
        this.error = e?.message || 'Greška pri registraciji.';
      }

    } finally {
      this.loading = false;
    }
  }

  async generateKeys() {

    const keyPair = await window.crypto.subtle.generateKey(
      {
        name: 'RSA-OAEP',
        modulusLength: 2048,
        publicExponent: new Uint8Array([1, 0, 1]),
        hash: 'SHA-256'
      },
      true,
      ['encrypt', 'decrypt']
    );

    // export public key
    const publicKeyBuffer =
      await window.crypto.subtle.exportKey(
        'spki',
        keyPair.publicKey
      );

    // export private key
    const privateKeyBuffer =
      await window.crypto.subtle.exportKey(
        'pkcs8',
        keyPair.privateKey
      );

    const publicKeyPem =
      this.arrayBufferToPem(
        publicKeyBuffer,
        'PUBLIC KEY'
      );

    const privateKeyPem =
      this.arrayBufferToPem(
        privateKeyBuffer,
        'PRIVATE KEY'
      );

    this.form.patchValue({
      publicKey: publicKeyPem
    });

    this.downloadPrivateKey(privateKeyPem);
  }

  arrayBufferToPem(buffer: ArrayBuffer, type: string): string {

    const bytes = new Uint8Array(buffer);

    let binary = '';

    bytes.forEach(b => binary += String.fromCharCode(b));

    const base64 = window.btoa(binary);

    const formatted = base64.match(/.{1,64}/g)?.join('\n');

    return `-----BEGIN ${type}-----\n${formatted}\n-----END ${type}-----`;
  }

  downloadPrivateKey(privateKey: string) {

    const blob = new Blob(
      [privateKey],
      { type: 'application/x-pem-file' }
    );

    const url = window.URL.createObjectURL(blob);

    const a = document.createElement('a');

    a.href = url;

    a.download = 'private_key.pem';

    a.click();

    window.URL.revokeObjectURL(url);
  }
}
