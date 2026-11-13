import { Component } from '@angular/core';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'app-admin-navbar',
  imports: [RouterModule],
  templateUrl: './admin-navbar.component.html',
  styleUrl: './admin-navbar.component.css',
})
export class AdminNavbarComponent {
  constructor(
    private authService: AuthService,
    private router: Router
  ) {
    this.email = this.authService.getUserEmail();
  }

  email: string | null = null;

  logout() {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
