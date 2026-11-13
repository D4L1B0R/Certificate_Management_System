import { Component, inject, ChangeDetectorRef, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CertificateService } from '../../services/certificate/certificate.service';
import { Certificate, IssuerCandidate } from '../../models/entities/certificate.model';

@Component({
  selector: 'app-user-csr',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './user-csr.html',
  styleUrl: './user-csr.css',
})
export class UserCsr implements OnInit {
  private fb = inject(FormBuilder);
  private certService = inject(CertificateService);
  private cdr = inject(ChangeDetectorRef);

  issuers: IssuerCandidate[] = [];
  issuedCert: Certificate | null = null;
  error: string | null = null;
  submitting = false;

  form: FormGroup = this.fb.group({
    issuerCertificateId: [null, Validators.required],
    validFrom: ['', Validators.required],
    validTo: ['', Validators.required],
    csrPem: ['', Validators.required],
  });

  ngOnInit() {
    this.certService.getIssuerCandidates('END_ENTITY').subscribe({
      next: (list) => { this.issuers = list; this.cdr.markForCheck(); },
      error: () => { this.error = 'Failed to load CA list'; this.cdr.markForCheck(); },
    });
  }

  get selectedIssuer(): IssuerCandidate | undefined {
    return this.issuers.find((c) => c.id === +this.form.value.issuerCertificateId);
  }

  get minDate(): string { return (this.selectedIssuer?.validFrom ?? '').substring(0, 16); }
  get maxDate(): string { return (this.selectedIssuer?.validTo ?? '').substring(0, 16); }

  onFileSelected(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      this.form.patchValue({ csrPem: reader.result as string });
      this.cdr.markForCheck();
    };
    reader.readAsText(file);
  }

  submit() {
    if (this.form.invalid) return;
    this.submitting = true;
    this.error = null;
    this.issuedCert = null;
    this.certService.issueCertificateFromCsr(this.form.value).subscribe({
      next: (cert) => { this.issuedCert = cert; this.submitting = false; this.cdr.markForCheck(); },
      error: (e) => { this.error = e?.error?.message ?? 'Issuing failed'; this.submitting = false; this.cdr.markForCheck(); },
    });
  }

  download(kind: 'cer' | 'pem') {
    if (!this.issuedCert) return;
    const id = this.issuedCert.id;
    const obs = kind === 'cer'
      ? this.certService.downloadCertificate(id)
      : this.certService.downloadCertificatePem(id);
    obs.subscribe((blob) => this.saveBlob(blob, `certificate_${id}.${kind}`));
  }

  private saveBlob(blob: Blob, fileName: string) {
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.click();
    window.URL.revokeObjectURL(url);
  }
}
