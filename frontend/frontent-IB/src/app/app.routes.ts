import { Routes } from '@angular/router';
import { AuthGuard } from './auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./landing/landing').then(m => m.LandingComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./auth/register/register.component').then(m => m.RegisterComponent)
  },
  {
    path: 'login',
    loadComponent: () => import('./auth/login/login').then(m => m.LoginComponent)
  },
  {
    path: 'verify-2fa',
    loadComponent: () => import('./auth/verify-tfa/verify-tfa').then(m => m.VerifyTfa)
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./auth/forgot-password/forgot-password.component')
        .then(m => m.ForgotPasswordComponent)
  },
  {
    path: 'admin/users',
    loadComponent: () =>
      import('./admin/admin-users/admin-users.component')
        .then(m => m.AdminUsersComponent),
    canActivate: [AuthGuard],
    data: { roles: ['ADMIN'] }
  },
  {
    path: 'admin/organizations',
    loadComponent: () =>
      import('./admin/admin-organizations/admin-organizations.component')
        .then(m => m.AdminOrganizationsComponent),
    canActivate: [AuthGuard],
    data: { roles: ['ADMIN'] }
  },
  {
    path: 'admin/certificates',
    loadComponent: () => import('./admin/admin-certificates/admin-certificates.component').then(m => m.AdminCertificatesComponent),
    canActivate: [AuthGuard],
    data: { roles: ['ADMIN'] }
  },
  {
    path: 'ca/home',
    loadComponent: () => import('./ca/ca-home/ca-home').then(m => m.CaHome),
    canActivate: [AuthGuard],
    data: { roles: ['CA'] }
  },
  {
    path: 'user/home',
    loadComponent: () => import('./user/user-home/user-home').then(m => m.UserHome),
    canActivate: [AuthGuard],
    data: { roles: ['USER'] }
  },
  {
    path: 'activate',
    loadComponent: () => import('./auth/activate-account/activate-account.component').then(m => m.ActivateAccountComponent)
  }
];
