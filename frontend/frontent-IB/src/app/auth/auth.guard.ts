import { Injectable, inject } from '@angular/core';
import { CanActivate, Router, ActivatedRouteSnapshot } from '@angular/router';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class AuthGuard implements CanActivate {
  private auth = inject(AuthService);
  private router = inject(Router);

  canActivate(route: ActivatedRouteSnapshot): boolean {
    const token = this.auth.getAccessToken();
    if (!token) {
      this.router.navigateByUrl('/login');
      return false;
    }

    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const role = payload.role;
      const allowedRoles: string[] = route.data?.['roles'] ?? [];

      if (allowedRoles.length > 0 && !allowedRoles.includes(role)) {
        this.router.navigateByUrl('/login');
        return false;
      }

      return true;
    } catch (e) {
      this.router.navigateByUrl('/login');
      return false;
    }
  }
}
