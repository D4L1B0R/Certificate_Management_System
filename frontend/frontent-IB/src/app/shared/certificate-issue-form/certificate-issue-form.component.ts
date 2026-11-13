import { CommonModule } from '@angular/common';
import {Component, EventEmitter, Input, Output, inject, numberAttribute} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../../services/admin/admin.service';
import { CertificateService } from '../../services/certificate/certificate.service';
import {
  Certificate,
  CertificateExtensions,
  CertificateType,
  IssueCertificateRequest,
  IssueEeAutogenerateRequest,
  IssuerCandidate,
} from '../../models/entities/certificate.model';
import { AuthService } from '../../auth/auth.service';
import { MatSnackBar } from '@angular/material/snack-bar';

export interface Organization {
  id: number;
  name: string;
}

@Component({
  selector: 'app-certificate-issue-form',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './certificate-issue-form.component.html',
  styleUrl: './certificate-issue-form.component.css',
})
export class CertificateIssueFormComponent {
  private certService = inject(CertificateService);
  private adminService = inject(AdminService);
  private auth = inject(AuthService);
  private snackBar = inject(MatSnackBar);

  /** admin: ROOT/INTERMEDIATE/END_ENTITY; ca: INTERMEDIATE/END_ENTITY; user: END_ENTITY only */
  @Input() mode: 'admin' | 'ca' | 'user' = 'admin';

  /** If provided, force organizationId (CA/user should not change org). */
  @Input({transform: numberAttribute}) fixedOrganizationId: number | null = null;

  /** Optional display name for fixed org (preferred for CA/user UI). */
  @Input() fixedOrganizationName: string | null = null;

  @Output() issued = new EventEmitter<Certificate>();

  organizations: any[] = [];
  issuerCandidates: IssuerCandidate[] = [];

  form: IssueCertificateRequest = {
    type: 'END_ENTITY',
    commonName: '',
    organizationId: null as any,
    organizationalUnit: '',
    country: '',
    validFrom: '',
    validTo: '',
    issuerCertificateId: null,
  };

  keyUsageOptions = [
    { key: 'digitalSignature', label: 'digitalSignature' },
    { key: 'nonRepudiation', label: 'nonRepudiation/contentCommitment' },
    { key: 'keyEncipherment', label: 'keyEncipherment' },
    { key: 'dataEncipherment', label: 'dataEncipherment' },
    { key: 'keyAgreement', label: 'keyAgreement' },
    { key: 'keyCertSign', label: 'keyCertSign (CA)' },
    { key: 'cRLSign', label: 'cRLSign (CA)' },
  ];

  ekUOptions = [
    { key: 'serverAuth', label: 'serverAuth' },
    { key: 'clientAuth', label: 'clientAuth' },
    { key: 'codeSigning', label: 'codeSigning' },
    { key: 'emailProtection', label: 'emailProtection' },
  ];

  extensionUi = {
    basicConstraintsCa: false,
    pathLenConstraint: null as number | null,
    keyUsages: {} as Record<string, boolean>,
    extendedKeyUsages: {} as Record<string, boolean>,
  };

  // only used when form.type === 'END_ENTITY'
  keystorePassword = '';
  keystorePasswordConfirm = '';

  allowedTypes: { value: CertificateType; label: string }[] = [];

  submitting = false;
  submitOk: string | null = null;
  submitErr: string | null = null;

  ngOnInit() {
    if (this.mode === 'admin') {
      this.allowedTypes = [
        { value: 'ROOT_CA', label: 'ROOT CA (self-signed)' },
        { value: 'INTERMEDIATE_CA', label: 'INTERMEDIATE CA' },
        { value: 'END_ENTITY', label: 'END ENTITY' },
      ];
    }

    if (this.mode === 'ca') {
      this.allowedTypes = [
        { value: 'INTERMEDIATE_CA', label: 'INTERMEDIATE CA' },
        { value: 'END_ENTITY', label: 'END ENTITY' },
      ];
    }

    if (this.mode === 'user') {
      this.allowedTypes = [
        { value: 'END_ENTITY', label: 'END ENTITY' },
      ];
    }

    this.form.type = (this.mode === 'admin') ? 'INTERMEDIATE_CA' : 'END_ENTITY';

    if (this.mode === 'admin') {
      this.adminService.getOrganizations().subscribe({
        next: (res: any[]) => {
          this.organizations = res;

          if (res.length > 0 && !this.form.organizationId) {
            this.form.organizationId = res[0].id;
          }
        },
        error: () => this.organizations = []
      });
    } else {
      if (this.fixedOrganizationId == null) {
        this.fixedOrganizationId = this.auth.getOrganizationIdFromContext();
      }
      // name may arrive later from /issuers headers; initialize if available
      if (!this.fixedOrganizationName) {
        this.fixedOrganizationName = this.auth.getOrganizationNameFromContext();
      }
      // re-read once after a tick in case headers arrived between construction and init
      setTimeout(() => {
        this.fixedOrganizationName = this.fixedOrganizationName || this.auth.getOrganizationNameFromContext();
      }, 0);
    }

    this.onTypeChange();
  }

  onTypeChange() {
    if (this.form.type === 'ROOT_CA') {
      this.form.issuerCertificateId = null;
      this.issuerCandidates = [];
      return;
    }

    this.refreshIssuerCandidates();
  }

  refreshIssuerCandidates() {
    if (!this.form.type || this.form.type === 'ROOT_CA') {
      this.issuerCandidates = [];
      return;
    }

    this.certService.getIssuerCandidates(this.form.type).subscribe({
      next: (cands) => {
        this.issuerCandidates = cands ?? [];

        if (this.mode !== 'admin') {
          this.fixedOrganizationName = this.auth.getOrganizationNameFromContext() || this.fixedOrganizationName;
          if (this.fixedOrganizationId == null) {
            this.fixedOrganizationId = this.auth.getOrganizationIdFromContext();
          }
        }

        if (
          this.form.issuerCertificateId &&
          !this.issuerCandidates.some(c => c.id === this.form.issuerCertificateId)
        ) {
          this.form.issuerCertificateId = null;
        }
      },
      error: (err) => {
        console.error('Issuer load failed', err);
        this.issuerCandidates = [];
      }
    });
  }

  private buildExtensionsPayload(): CertificateExtensions | undefined {
    const keyUsages = Object.entries(this.extensionUi.keyUsages)
      .filter(([, v]) => v)
      .map(([k]) => k);

    const extendedKeyUsages = Object.entries(this.extensionUi.extendedKeyUsages)
      .filter(([, v]) => v)
      .map(([k]) => k);

    const extensions: CertificateExtensions = {
      basicConstraintsCa: this.extensionUi.basicConstraintsCa,
      pathLenConstraint: this.extensionUi.pathLenConstraint,
      keyUsages: keyUsages.length ? keyUsages : undefined,
      extendedKeyUsages: extendedKeyUsages.length ? extendedKeyUsages : undefined,
    };

    const hasAny =
      extensions.basicConstraintsCa !== undefined ||
      extensions.pathLenConstraint !== undefined ||
      !!extensions.keyUsages?.length ||
      !!extensions.extendedKeyUsages?.length;

    return hasAny ? extensions : undefined;
  }

  canPickOrganization(): boolean {
    return this.mode === 'admin' && this.isRoot();
  }

  submit() {
    if (this.submitting) return;

    this.submitOk = null;
    this.submitErr = null;

    const orgId =
      this.isRoot()
        ? this.form.organizationId
        : this.fixedOrganizationId ?? this.auth.getOrganizationIdFromContext();

    if (!this.validateForm(orgId)) return;

    if (this.mode !== 'admin' && (!this.fixedOrganizationName || !this.fixedOrganizationName.trim())) {
      console.warn('Organization name not loaded (will still submit using organizationId).');
    }

    this.submitting = true;

    if (this.form.type === 'ROOT_CA') {
      this.submitRoot(orgId!);
      return;
    }

    if (!this.form.issuerCertificateId) {
      this.submitErr = 'Issuer must be selected!';
      this.submitting = false;
      return;
    }

    if (this.form.type === 'END_ENTITY') {
      this.submitEndEntity(orgId!);
      return;
    }

    this.submitCa(orgId!);
  }

  private validateForm(orgId: number | null): boolean {
    if (orgId == null) {
      this.submitErr = 'Organization missing';
      return false;
    }
    if (!this.form.commonName || !this.form.organizationalUnit ||
        !this.form.country || !this.form.validFrom || !this.form.validTo) {
      this.submitErr = 'All fields must be filled!';
      return false;
    }
    if (this.form.country.trim().length !== 2) {
      this.submitErr = 'Country must be exactly 2 letters (RS, US)';
      return false;
    }
    if (new Date(this.form.validFrom) >= new Date(this.form.validTo)) {
      this.submitErr = 'Valid From must be before Valid To!';
      return false;
    }

    const now = new Date();

    const from = new Date(this.form.validFrom);
    const to = new Date(this.form.validTo);

    if (from < now) {
      this.snackBar.open('Valid From cannot be in the past', 'Close', {
        duration: 4000,
        panelClass: ['snackbar-error']
      });
      return false;
    }

    if (to < now) {
      this.snackBar.open('Valid To cannot be in the past', 'Close', {
        duration: 4000,
        panelClass: ['snackbar-error']
      });
      return false;
    }
    return true;
  }

  private formatDate(d: string): string {
    return d?.slice(0, 19);
  }

  private submitRoot(orgId: number) {
    this.certService.createRootCertificate(
      this.form.commonName,
      this.form.organizationalUnit,
      this.form.country,
      orgId,
      this.formatDate(this.form.validFrom),
      this.formatDate(this.form.validTo)
    ).subscribe({
      next: (res: any) => {
        this.submitOk = 'Certificate issued successfully.';
        this.issued.emit(res);
        this.submitting = false;
      },
      error: (err) => {
        const message =
          err?.error?.message ||
          err?.error?.error ||
          err?.message ||
           'Error issuing certificate';

          this.snackBar.open(message, 'Close', {
            duration: 5000,
            panelClass: ['snackbar-error']
          });

          this.submitErr = message;
          this.submitting = false;
        }
    });
  }

  private submitCa(orgId: number) {
    const payload: IssueCertificateRequest = {
      ...this.form,
      organizationId: orgId,
      country: this.form.country.toUpperCase().trim(),
      extensions: this.buildExtensionsPayload(),
      basicConstraintsCA: this.extensionUi.basicConstraintsCa,
      keyCertSign: this.extensionUi.keyUsages['keyCertSign'] || false,
      digitalSignature: this.extensionUi.keyUsages['digitalSignature'] || false,
      keyEncipherment: this.extensionUi.keyUsages['keyEncipherment'] || false,
    };

    this.certService.issueCertificate(payload).subscribe({
      next: (cert) => {
        this.submitOk = 'Certificate issued successfully.';
        this.issued.emit(cert);
        this.submitting = false;
      },
      error: (err) => {
        console.error(err);
        this.submitErr = err?.error?.message || err?.message || 'Error issuing certificate';
        this.submitting = false;
      },
    });
  }

  private submitEndEntity(orgId: number) {
    if (this.keystorePassword.length < 8) {
      this.submitErr = 'Keystore password must be at least 8 characters';
      this.submitting = false;
      return;
    }
    if (this.keystorePassword !== this.keystorePasswordConfirm) {
      this.submitErr = 'Passwords do not match';
      this.submitting = false;
      return;
    }

    const payload: IssueEeAutogenerateRequest = {
      commonName: this.form.commonName,
      organizationId: orgId,
      organizationalUnit: this.form.organizationalUnit,
      country: this.form.country.toUpperCase().trim(),
      validFrom: this.formatDate(this.form.validFrom),
      validTo: this.formatDate(this.form.validTo),
      issuerCertificateId: this.form.issuerCertificateId!,
      digitalSignature: this.extensionUi.keyUsages['digitalSignature'] || false,
      keyEncipherment: this.extensionUi.keyUsages['keyEncipherment'] || false,
      keystorePassword: this.keystorePassword,
    };

    this.certService.issueEeAutogenerate(payload).subscribe({
      next: (blob) => {
        this.downloadKeystore(blob);
        this.resetKeystoreFields();
        this.submitOk = 'Certificate issued and keystore downloaded.';
        this.issued.emit({} as Certificate);
        this.submitting = false;
      },
      error: (err) => {
        console.error(err);
        this.submitErr = 'Failed to issue certificate';
        this.submitting = false;
      },
    });
  }

  private downloadKeystore(blob: Blob) {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'certificate.p12';
    a.click();
    window.URL.revokeObjectURL(url);
  }

  private resetKeystoreFields() {
    this.keystorePassword = '';
    this.keystorePasswordConfirm = '';
  }

  isRoot(): boolean {
    return this.form.type === 'ROOT_CA';
  }
}
