import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-verify-tfa',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './verify-tfa.html',
  styleUrl: './verify-tfa.css',
})
export class VerifyTfa {
private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  pendingToken: string | null = null;
  email: string | null = null;

  form = this.fb.group({
    code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });

  loading = false;
  error: string | null = null;
  submitted = false;

  constructor() {
    const nav = this.router.getCurrentNavigation();
    this.pendingToken = (nav?.extras.state as any)?.pendingToken
                     ?? (history.state as any)?.pendingToken
                     ?? null;

    this.email = this.auth.getUserEmail();
  }

  async submit() {
    this.submitted = true;
    if (this.form.invalid || !this.pendingToken) return;

    this.loading = true;
    this.error = null;

    try {
      await this.auth.verify2fa(this.pendingToken, this.form.value.code!);

      const token = this.auth.getAccessToken();
      if (!token) throw new Error('Missing access token');

      let role: string | null = null;
      try {
        const payload = JSON.parse(atob(token.split('.')[1]));
        role = payload?.role ?? null;
      } catch {
        role = null;
      }

      if (role === 'ADMIN') await this.router.navigateByUrl('/admin/users');
      else if (role === 'CA') await this.router.navigateByUrl('/ca/home');
      else await this.router.navigateByUrl('/user/home');
    } catch (e: any) {
      if (e?.status === 401) this.error = 'Invalid token, try again';
      else this.error = e?.message || 'Error during validation';
    } finally {
      this.loading = false;
    }
  }

  cancel() {
    this.auth.clearTokens();
    this.router.navigateByUrl('/login');
  }
}
