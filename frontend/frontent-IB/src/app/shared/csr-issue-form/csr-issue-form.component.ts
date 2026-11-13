import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin/admin.service';
import { CertificateService } from '../../services/certificate/certificate.service';
import { Certificate, IssuerCandidate } from '../../models/entities/certificate.model';

@Component({
  selector: 'app-csr-issue-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './csr-issue-form.component.html',
  styleUrl: './csr-issue-form.component.css',
})
export class CsrIssueFormComponent {
  private certService = inject(CertificateService);
  private adminService = inject(AdminService);

  @Input() fixedOrganizationId: number | null = null;

  @Output() issued = new EventEmitter<Certificate>();

  organizationId: number = 0;
  organizations: any[] = [];

  issuerCandidates: IssuerCandidate[] = [];

  validFrom = '';
  validTo = '';
  issuerCertificateId: number | null = null;

  csrPem = '';

  ngOnInit() {
    if (this.fixedOrganizationId) {
      this.organizationId = this.fixedOrganizationId;
    }

    this.adminService.getOrganizations().subscribe({
      next: (res: any) => {
        this.organizations = res;
        if (!this.organizationId && this.organizations?.length) {
          this.organizationId = this.organizations[0].id;
        }
      },
      error: () => {},
    });

    this.refreshIssuers();
  }

  refreshIssuers() {
    this.certService.getIssuerCandidates('END_ENTITY').subscribe({
      next: (cands) => {
        this.issuerCandidates = cands;
      },
      error: () => (this.issuerCandidates = []),
    });
  }

  canPickOrg(): boolean {
    return !this.fixedOrganizationId;
  }

  submit() {
    alert("CSR is not implemented yet");
    return;
  }
}
