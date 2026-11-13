import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';

@Component({
  selector: 'app-main',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './main.html',
  styleUrls: ['./main.css']
})
export class MainComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  email = this.auth.getUserEmail();

  async logout() {
    this.auth.logout().catch(() => {});
    await this.router.navigateByUrl('/');
  }
}
