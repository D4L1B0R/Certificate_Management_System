import { Component, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CertificateService } from '../../services/certificate/certificate.service';
import { Certificate, CertificateDetails } from '../../models/entities/certificate.model';
import { AuthService } from '../../auth/auth.service';
import { Router } from '@angular/router';
import { CertificateIssueFormComponent } from '../../shared/certificate-issue-form/certificate-issue-form.component';
import { CertificateDownloadModalComponent } from '../../shared/certificate-download-modal/certificate-download-modal.component';
import { UserTfa } from '../user-tfa/user-tfa'
import { UserCsr } from '../user-csr/user-csr';
import { RevocationReason } from '../../models/entities/certificate.model';
import { RevokeDialogComponent } from '../../shared/revoke-dialog/revoke-dialog.component';

@Component({
  selector: 'app-user-home',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    CertificateIssueFormComponent,
    CertificateDownloadModalComponent,
    UserTfa,
    UserCsr,
    RevokeDialogComponent
  ],
  templateUrl: './user-home.html',
  styleUrl: './user-home.css',
})
export class UserHome {
  certificates: Certificate[] | null = null;
  selectedCertificate: CertificateDetails | null = null;
  email: string | null = null;
  activeTab: 'my-certs' | 'generate' | 'csr' | 'security' = 'my-certs';

  revokeId: number | null = null;

  revokeReason: RevocationReason | null = null;

  revokeReasons = Object.values(RevocationReason);

  revokeModalOpen = false;
  revokeCertId: number | null = null;

//   revokeReasons = [
//     'unspecified',
//     'keyCompromise',
//     'cACompromise',
//     'affiliationChanged',
//     'superseded',
//     'cessationOfOperation',
//     'certificateHold',
//     'removeFromCRL',
//     'privilegeWithdrawn',
//     'aACompromise',
//   ];

  constructor(
    private certificateService: CertificateService,
    private cdr: ChangeDetectorRef,
    private authService: AuthService,
    private router: Router
  ) {
    this.email = this.authService.getUserEmail();
  }

  myOrgId: number | null = null;
  loading = true;

  ngOnInit() {
    this.authService.getMe().subscribe({
      next: (user) => {
        this.myOrgId = user.organizationId;
        this.email = user.email;
        this.loading = false;
      },
      error: () => {
        this.myOrgId = null;
        this.loading = false;
      }
    });
  }

  setTab(tab: 'my-certs' | 'generate' | 'csr' | 'security') {
    this.activeTab = tab;
  }

  loadCertificates(): void {
    this.certificateService.getMyCertificates().subscribe({
      next: (data) => {
        this.certificates = data;
      },
      error: () => {
        this.certificates = [];
      },
    });
  }

  onIssued() {
    this.setTab('my-certs');
    this.loadCertificates();
  }

  selectCertificate(id: number): void {
    this.certificateService.getCertificateDetails(id).subscribe({
      next: (data) => {
        this.selectedCertificate = data;
      },
    });
  }

  closeDetails(): void {
    this.selectedCertificate = null;
  }

  downloadModalOpen = false;
  downloadModalCertId: number | null = null;

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

    this.certificateService.downloadKeystorePkcs12(id, password).subscribe({
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
    this.certificateService.downloadCertificate(id).subscribe({
      next: (blob) => this.saveBlob(blob, `certificate_${id}.cer`),
      error: () => alert('Failed to download certificate'),
    });
  }

  downloadPem(id: number) {
    this.certificateService.downloadCertificatePem(id).subscribe({
      next: (blob) => this.saveBlob(blob, `certificate_${id}.pem`),
      error: () => alert('Failed to download .pem'),
    });
  }

  downloadPkcs12(id: number) {
    const password = window.prompt('Keystore password (PKCS#12):', 'changeit') ?? '';
    if (!password.trim()) return;

    this.certificateService.downloadKeystorePkcs12(id, password).subscribe({
      next: (blob) => this.saveBlob(blob, `certificate_${id}.p12`),
      error: (e) => {
        if (e?.status === 400) alert('No private key present for this certificate.');
        else alert('Failed to download keystore');
      },
    });
  }

  confirmRevoke(event: { certId: number; reason: RevocationReason }) {
    this.certificateService
      .revokeCertificate(event.certId, event.reason)
      .subscribe({
        next: () => {
          alert('Certificate revoked');
          this.revokeModalOpen = false;
          this.revokeCertId = null;
          this.loadCertificates();
        },
        error: () => {
          alert('Revoke failed');
        },
      });
  }

  logout(): void {
    this.authService.logout().then(() => {
      this.router.navigateByUrl('/login');
    });
  }

  openRevokeModal(id: number) {
    this.revokeCertId = id;
    this.revokeModalOpen = true;
  }
}
