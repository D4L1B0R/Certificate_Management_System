import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-certificate-download-modal',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="modal-overlay" *ngIf="isOpen" (click)="close()">
      <div class="modal-content" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h3>Download Certificate</h3>
          <button class="close-btn" (click)="close()">×</button>
        </div>

        <div class="modal-body">
          <p>Select download format:</p>

          <div class="download-options">
            <button class="option-btn" (click)="selectFormat('cer')">
              <span class="format">.cer</span>
              <span class="desc">DER format (binary)</span>
            </button>

            <button class="option-btn" (click)="selectFormat('pem')">
              <span class="format">.pem</span>
              <span class="desc">PEM format (text)</span>
            </button>

            <!-- Rule: .p12 is offered only when private key exists; we detect that in caller by attempting p12 first -->
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .modal-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .modal-content {
      background: #fff;
      border-radius: 10px;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      max-width: 400px;
      width: 90%;
    }

    .modal-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 16px;
      border-bottom: 1px solid #e2e8f0;
    }

    .modal-header h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 700;
      color: #0f172a;
    }

    .close-btn {
      background: transparent;
      border: none;
      font-size: 24px;
      cursor: pointer;
      color: #475569;
    }

    .modal-body {
      padding: 16px;
    }

    .modal-body p {
      margin: 0 0 12px 0;
      font-size: 14px;
      color: #475569;
      font-weight: 600;
    }

    .download-options {
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .option-btn {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      gap: 4px;
      padding: 12px 14px;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      background: #f8fafc;
      cursor: pointer;
      transition: all 0.2s;
      text-align: left;
    }

    .option-btn:hover {
      background: #e2e8f0;
      border-color: #2563eb;
    }

    .option-btn .format {
      font-weight: 700;
      color: #2563eb;
      font-size: 14px;
    }

    .option-btn .desc {
      font-size: 12px;
      color: #64748b;
    }
  `]
})
export class CertificateDownloadModalComponent {
  @Input() isOpen = false;
  @Output() formatSelected = new EventEmitter<'cer' | 'pem'>();
  @Output() closed = new EventEmitter<void>();

  selectFormat(format: 'cer' | 'pem') {
    this.formatSelected.emit(format);
    this.close();
  }

  close() {
    this.closed.emit();
  }
}
