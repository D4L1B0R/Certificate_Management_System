import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { AdminService } from '../../services/admin/admin.service';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { AdminNavbarComponent } from '../../layouts/admin-navbar/admin-navbar.component';

@Component({
  selector: 'app-admin-organizations',
  imports: [CommonModule, FormsModule, AdminNavbarComponent],
  templateUrl: './admin-organizations.component.html',
  styleUrl: './admin-organizations.component.css',
})
export class AdminOrganizationsComponent {
  private adminService = inject(AdminService);
  private cdr = inject(ChangeDetectorRef);

  organizations: any[] = [];
  orgName = '';

  ngOnInit() {
    this.getOrganizations();
  }

  getOrganizations() {
    this.adminService.getOrganizations().subscribe((data: any) => {
      this.organizations = data;
      this.cdr.detectChanges();
    });
  }

  createOrganization() {
    this.adminService.createOrganization(this.orgName).subscribe(() => {
      this.getOrganizations();
      this.orgName = '';
    });
  }
}
