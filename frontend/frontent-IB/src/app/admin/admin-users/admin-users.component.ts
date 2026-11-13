import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { AdminService } from '../../services/admin/admin.service';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AdminNavbarComponent } from '../../layouts/admin-navbar/admin-navbar.component';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'app-admin-users',
  imports: [CommonModule, FormsModule, AdminNavbarComponent, MatSnackBarModule],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.css',
})
export class AdminUsersComponent {
  private adminService = inject(AdminService);
  private cdr = inject(ChangeDetectorRef);
  private snackBar = inject(MatSnackBar);

  users: any[] = [];
  organizations: any[] = [];

  caUser = {
    email: '',
    name: '',
    surname: '',
    password: '',
    organizationId: null
  };

  // New rotation-related fields
  rotateBatchSize?: number;
  rotateKeyBase64?: string;
  rotating = false;
  lastRotationResult: any = null;
  lastRotationStatus: any = null;

  ngOnInit() {
    this.getUsers();
    this.getOrganizations();
  }

  getUsers() {
    this.adminService.getUsers().subscribe((data: any) => {
      this.users = data;
      this.cdr.detectChanges();
    });
  }

  getOrganizations() {
    this.adminService.getOrganizations().subscribe((data: any) => {
      this.organizations = data;
      this.cdr.detectChanges();
    });
  }

  createCaUser() {
    if (!this.caUser.email) {
      this.showError('Email is required');
      return;
    }
    if (!this.caUser.name) {
      this.showError('Name is required');
      return;
    }
    if (!this.caUser.surname) {
      this.showError('Surname is required');
      return;
    }
    if (!this.caUser.password) {
      this.showError('Password is required');
      return;
    }
    if (!this.caUser.organizationId) {
      this.showError('Organization is required');
      return;
    }

    if (!this.isValidEmail(this.caUser.email)) {
      this.showError('Invalid email format');
      return;
    }

    if (this.users.some(u => u.email === this.caUser.email)) {
      this.showError('Email already exists in the system');
      return;
    }

    this.adminService.createCaUser(this.caUser).subscribe({
      next: () => {
        this.showSuccess('User created successfully');
        this.resetForm();
        this.getUsers();
      },
      error: (err) => {
        const errorMessage = this.extractErrorMessage(err);
        this.showError(errorMessage);
        console.error('Error:', err);
      }
    });
  }

  private isValidEmail(email: string): boolean {
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  }

  private extractErrorMessage(err: any): string {
    console.log('Full error object:', err);
    console.log('Error status:', err?.status);
    console.log('Error.error:', err?.error);

    if (err?.error && typeof err.error === 'object') {
      for (const key in err.error) {
        if (err.error.hasOwnProperty(key)) {
          const value = err.error[key];
          if (typeof value === 'string') {
            return value;
          }
          if (Array.isArray(value) && value.length > 0) {
            return value[0];
          }
        }
      }
    }

    if (err?.error?.message) {
      return err.error.message;
    }
    if (err?.error?.error) {
      return err.error.error;
    }
    if (typeof err?.error === 'string') {
      return err.error;
    }
    if (err?.message) {
      return err.message;
    }
    return 'Error creating user';
  }

  private resetForm() {
    this.caUser = {
      email: '',
      name: '',
      surname: '',
      password: '',
      organizationId: null
    };
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
      duration: 5000,
      panelClass: ['snackbar-error'],
      horizontalPosition: 'end',
      verticalPosition: 'bottom'
    });
  }

  // rotate master key
  rotateMaster() {
    if (this.rotating) {
      return;
    }
    const body: any = {};
    if (this.rotateBatchSize && Number.isFinite(+this.rotateBatchSize)) {
      body.batchSize = +this.rotateBatchSize;
    }
    if (this.rotateKeyBase64 && this.rotateKeyBase64.trim().length > 0) {
      body.masterKeyBase64 = this.rotateKeyBase64.trim();
    }

    this.rotating = true;
    this.adminService.rotateMaster(body).subscribe({
      next: (res: any) => {
        this.lastRotationResult = res;
        console.log('rotateMaster response:', res);
        this.showSuccess('Master key rotated (see console for details)');
        // fetch status after rotation
        this.getRotationStatus();
      },
      error: (err) => {
        console.error('rotateMaster error:', err);
        const msg = this.extractErrorMessage(err) || 'Rotation failed';
        this.showError(msg);
      },
      complete: () => {
        this.rotating = false;
        this.cdr.detectChanges();
      }
    });
  }

  getRotationStatus() {
    this.adminService.getRotationStatus().subscribe({
      next: (res: any) => {
        this.lastRotationStatus = res;
        console.log('rotationStatus response:', res);
        this.showSuccess('Rotation status fetched (see console)');
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('rotationStatus error:', err);
        const msg = this.extractErrorMessage(err) || 'Failed to get rotation status';
        this.showError(msg);
      }
    });
  }
}
