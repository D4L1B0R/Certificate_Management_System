import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RevocationReason } from '../../models/entities/certificate.model';

@Component({
  selector: 'app-revoke-dialog',
  imports: [CommonModule, FormsModule],
  templateUrl: './revoke-dialog.component.html',
  styleUrl: './revoke-dialog.component.css',
  standalone: true
})
export class RevokeDialogComponent {
  @Input() certId: number | null = null;
  @Input() isOpen: boolean = false;

  @Output() closed = new EventEmitter<void>();
  @Output() revoked = new EventEmitter<{ certId: number; reason: RevocationReason }>();

  reason: RevocationReason | null = null;

  reasons = Object.values(RevocationReason);

  close() {
    this.reason = null;
    this.closed.emit();
  }

  confirm() {
    if (!this.certId || !this.reason) return;

    this.revoked.emit({
      certId: this.certId,
      reason: this.reason
    });
  }
}
