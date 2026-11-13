import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-certificate-details-dialog',
  imports: [CommonModule, MatDialogModule],
  templateUrl: './certificate-details-dialog.component.html',
  styleUrl: './certificate-details-dialog.component.css',
})

export class CertificateDetailsDialogComponent {
  constructor(
    @Inject(MAT_DIALOG_DATA) public data: any,
    public dialogRef: MatDialogRef<CertificateDetailsDialogComponent>
  ) {}

  getStatus(): string {
    if (this.data.revoked) return 'INVALID (Revoked)';
    return this.data.valid ? 'VALID' : 'INVALID (Expired)';
  }

  getStatusClass(): string {
    if (this.data.revoked) return 'status-expired';
    return this.data.valid ? 'status-valid' : 'status-expired';
  }
}
