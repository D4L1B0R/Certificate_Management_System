import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './login.html',
  styleUrls: ['./login.css'],
})
export class LoginComponent {
  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  private emailPattern = '^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$';
  private passwordPattern =
    '^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$';

  submitted = false;

  form = this.fb.group({
    email: ['', [Validators.required, Validators.pattern(this.emailPattern)]],
    password: ['', [
      Validators.required,
      Validators.minLength(8),
      Validators.pattern(this.passwordPattern)
    ]],
  });

  loading = false;
  error: string | null = null;

  constructor() {
    // focus email input shortly after component init as a backup
    setTimeout(() => {
      const input = document.querySelector('.login-container input[formcontrolname="email"]') as HTMLElement | null;
      if (input) input.focus();
    }, 50);
  }

  async submit() {
    this.submitted = true;
    if (this.form.invalid) return;
    this.loading = true;
    this.error = null;

    const { email, password } = this.form.value;

    try {
      const response = await this.auth.login(email!, password!);

      // 2fa is required
      if (response?.requires2fa) {
        await this.router.navigate(['/verify-2fa'], {
          state: { pendingToken: response.pendingToken }
        });
        return;
      }

      // no 2fa
      const token = this.auth.getAccessToken();
      if (!token) throw new Error('Nedostaje access token');

      await this.navigateByRole(token);
    } catch (e: any) {
      this.auth.clearTokens();
      if (e?.status === 401) this.error = 'Neispravan email ili lozinka';
      else this.error = e?.message || 'Greška pri prijavi';
    } finally {
      this.loading = false;
    }
  }

  private async navigateByRole(token: string) {
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
  }


}
