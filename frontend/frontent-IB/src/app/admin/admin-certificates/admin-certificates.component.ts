import { Component, inject, ChangeDetectorRef  } from '@angular/core';
import { CommonModule } from '@angular/common';
import { forkJoin } from 'rxjs';
import { map } from 'rxjs/operators';

import { CertificateService } from '../../services/certificate/certificate.service';
import { AdminNavbarComponent } from '../../layouts/admin-navbar/admin-navbar.component';
import { CertificateIssueFormComponent } from '../../shared/certificate-issue-form/certificate-issue-form.component';
import { CertificateDownloadModalComponent } from '../../shared/certificate-download-modal/certificate-download-modal.component';
import { AdminService } from '../../services/admin/admin.service';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';

import { CertificateDetailsDialogComponent } from '../certificate-details-dialog/certificate-details-dialog.component';
import { RevocationReason } from '../../models/entities/certificate.model';
import { FormsModule } from '@angular/forms';
import { RevokeDialogComponent } from '../../shared/revoke-dialog/revoke-dialog.component';

@Component({
  selector: 'app-admin-certificates',
  standalone: true,
  imports: [
    CommonModule,
    AdminNavbarComponent,
    CertificateIssueFormComponent,
    CertificateDownloadModalComponent,
    MatDialogModule,
    FormsModule,
    RevokeDialogComponent
  ],
  templateUrl: './admin-certificates.component.html',
  styleUrl: './admin-certificates.component.css',
})
export class AdminCertificatesComponent {

  private certService = inject(CertificateService);
  private adminService = inject(AdminService);
  private dialog = inject(MatDialog);
  private cdr = inject(ChangeDetectorRef);

  certificates: any[] = [];
  downloadModalOpen = false;
  downloadModalCertId: number | null = null;

  private downloadProbeInFlight = new Set<number>();

  reasons = Object.values(RevocationReason);

  selectedReason: RevocationReason | null = null;
  selectedCertId: number | null = null;

  ngOnInit() {
    this.load();
  }

  load() {
    this.certService.getCertificates().subscribe((res: any[]) => {

      this.certificates = res;

      this.cdr.detectChanges();

      if (res.length === 0) return;

      const detailRequests = res.map(cert =>
        this.certService.getCertificateDetails(cert.id).pipe(
          map(details => ({ cert, details }))
        )
      );

      forkJoin(detailRequests).subscribe({
        next: (results: any[]) => {
          results.forEach(({ cert, details }) => {
            cert.subjectCN = details.subjectCN;
            cert.organizationalUnit = details.organizationalUnit;
            cert.country = details.country;
            cert.issuerCN = details.issuerCN;
            cert.serialNumber = details.serialNumber;
          });

          this.cdr.detectChanges();
        },
        error: (err) => console.error(err)
      });
    });
  }

  onIssued() {
    this.load();
  }

  viewDetails(id: number) {
    this.certService.getCertificateDetails(id).subscribe({
      next: (details) => {
        const cert = this.certificates.find(c => c.id === id);

        this.dialog.open(CertificateDetailsDialogComponent, {
          width: '600px',
          data: {
            ...details,
            valid: cert?.valid
          }
        });
      }
    });
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

    if (format === 'cer') this.download(this.downloadModalCertId);
    else this.downloadPem(this.downloadModalCertId);
  }

  download(id: number) {
    this.certService.downloadCertificate(id).subscribe({
      next: (blob) => this.saveBlob(blob, `certificate_${id}.cer`),
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

  formatType(type: string): string {
    return type.replace(/_/g, ' ');
  }

  getBadgeClass(type: string): string {
    return type.toLowerCase().replace(/_/g, '-');
  }

  openRevoke(certId: number) {
    console.log('OPEN REVOKE CLICK', certId);
    this.selectedCertId = certId;
    this.selectedReason = null;
  }

  confirmRevoke() {
    if (!this.selectedCertId || !this.selectedReason) return;

    this.certService
      .revokeCertificate(this.selectedCertId, this.selectedReason)
      .subscribe({
        next: () => {
          this.selectedCertId = null;
          this.selectedReason = null;
          this.load();
        },
        error: () => alert('Failed to revoke certificate')
      });
  }

  handleRevoke(event: { certId: number; reason: RevocationReason }) {
    this.certService.revokeCertificate(event.certId, event.reason)
      .subscribe({
        next: () => {
          this.selectedCertId = null;
          this.load();
        },
        error: () => alert('Revoke failed')
      });
  }
}
