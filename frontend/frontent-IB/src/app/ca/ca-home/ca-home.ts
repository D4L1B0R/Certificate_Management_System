import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { CertificateService } from '../../services/certificate/certificate.service';
import { CertificateIssueFormComponent } from '../../shared/certificate-issue-form/certificate-issue-form.component';
import { CertificateDownloadModalComponent } from '../../shared/certificate-download-modal/certificate-download-modal.component';
import { Certificate, CertificateDetails } from '../../models/entities/certificate.model';
import { RevokeDialogComponent } from '../../shared/revoke-dialog/revoke-dialog.component';
import { RevocationReason } from '../../models/entities/certificate.model';

@Component({
  selector: 'app-ca-home',
  standalone: true,
  imports: [CommonModule, CertificateIssueFormComponent, CertificateDownloadModalComponent, RevokeDialogComponent],
  templateUrl: './ca-home.html',
  styleUrl: './ca-home.css',
})
export class CaHome {
  private authService = inject(AuthService);
  private router = inject(Router);
  private certService = inject(CertificateService);
  private cdr = inject(ChangeDetectorRef);

  myOrgId: number | null | undefined;
  myOrgName: string | null | undefined;

  certificates: Certificate[] | null = null;
  selectedCertificate: CertificateDetails | null = null;

  downloadModalOpen = false;
  downloadModalCertId: number | null = null;

  private downloadProbeInFlight = new Set<number>();

  revokeModalOpen = false;
  revokeCertId: number | null = null;

  loading = false;

  ngOnInit() {
    this.authService.getMe().subscribe(user => {
      this.myOrgId = user.organizationId;
      this.authService.setOrgContext(user.organizationId, null);

      // hydrate org name via /issuers headers (X-ORG-NAME)
      this.certService.getIssuerCandidates('END_ENTITY').subscribe({
        next: () => {
          this.myOrgName = this.authService.getOrganizationNameFromContext();
        },
        error: () => {
          this.myOrgName = this.authService.getOrganizationNameFromContext();
        }
      });
    });

    this.loadCertificates();
  }

  onIssued() {
    // stay on page; refresh org certificates list
    this.loadCertificates();
  }

  loadCertificates(): void {
    // show the table immediately (skeleton/loading row) by making certificates non-null
    this.loading = true;
    this.certificates = []; // not null => table renders; loading row will show
    this.selectedCertificate = null;
    this.cdr.detectChanges();

    this.certService.getOrganizationCertificates().subscribe({
      next: (data) => {
        this.certificates = data ?? [];
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.certificates = [];
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  selectCertificate(id: number): void {
    this.certService.getCertificateDetails(id).subscribe({
      next: (data) => {
        this.selectedCertificate = data;
        this.cdr.detectChanges();
      },
      error: () => {
        this.selectedCertificate = null;
        this.cdr.detectChanges();
      }
    });
  }

  closeDetails(): void {
    this.selectedCertificate = null;
  }

  openDownloadModal(id: number) {
    const cert = (this.certificates ?? []).find(c => c.id === id);

    const hasPrivateKey = cert?.hasPrivateKey === true;

    if (!hasPrivateKey) {
      this.downloadModalCertId = id;
      this.downloadModalOpen = true;
      return;
    }

    const password = window.prompt('Keystore password (PKCS#12):', 'changeit') ?? '';
    if (!password.trim()) return;

    this.certService.downloadKeystorePkcs12(id, password).subscribe({
      next: (blob) => this.saveBlob(blob, `certificate_${id}.p12`),
      error: (e) => {
        if (e?.status === 400) {
          this.downloadModalCertId = id;
          this.downloadModalOpen = true;
          return;
        }
        alert('Failed to download keystore');
      }
    });
  }

  private saveBlob(blob: Blob, fileName: string) {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.click();
    window.URL.revokeObjectURL(url);
  }

  closeDownloadModal() {
    this.downloadModalOpen = false;
    this.downloadModalCertId = null;
  }

  handleDownloadFormat(format: 'cer' | 'pem') {
    if (!this.downloadModalCertId) return;

    if (format === 'cer') this.downloadCer(this.downloadModalCertId);
    else this.downloadPem(this.downloadModalCertId);
  }

  downloadCer(id: number) {
    this.certService.downloadCertificate(id).subscribe({
      next: (blob) => this.saveBlob(blob, `certificate_${id}.cer`),
      error: () => alert('Failed to download .cer'),
    });
  }

  downloadPem(id: number) {
    this.certService.downloadCertificatePem(id).subscribe({
      next: (blob) => this.saveBlob(blob, `certificate_${id}.pem`),
      error: () => alert('Failed to download .pem'),
    });
  }

  // Keep if referenced by template; otherwise safe to delete later.
  downloadPkcs12(id: number) {
    const password = window.prompt('Keystore password (PKCS#12):', 'changeit') ?? '';
    if (!password.trim()) return;

    this.certService.downloadKeystorePkcs12(id, password).subscribe({
      next: (blob) => this.saveBlob(blob, `certificate_${id}.p12`),
      error: (e) => {
        if (e?.status === 400) alert('No private key present for this certificate.');
        else alert('Failed to download keystore');
      },
    });
  }

  // keep old name for template compatibility if still referenced anywhere
  download(id: number) {
    this.openDownloadModal(id);
  }

  openCertificates() {
    this.router.navigateByUrl('/ca/certificates');
  }

  logout() {
    this.authService.logout().then(() => {
      this.router.navigateByUrl('/login');
    });
  }

  openRevokeModal(id: number) {
    this.revokeCertId = id;
    this.revokeModalOpen = true;
  }

  confirmRevoke(event: { certId: number; reason: RevocationReason }) {
    this.certService.revokeCertificate(event.certId, event.reason).subscribe({
      next: () => {
        this.revokeModalOpen = false;
        this.revokeCertId = null;
        this.loadCertificates();
      },
      error: () => alert('Failed to revoke certificate')
    });
  }
}
