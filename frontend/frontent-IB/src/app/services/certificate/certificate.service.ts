import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import {
  Certificate,
  CertificateDetails,
  CertificateType,
  CsrCertificateRequest,
  IssueCertificateRequest,
  IssueEeAutogenerateRequest,
  IssuerCandidate,
  RevocationReason
} from '../../models/entities/certificate.model';
import { AuthService } from '../../auth/auth.service';

@Injectable({
  providedIn: 'root',
})
export class CertificateService {
  private baseUrl = '/api/certificates';

  constructor(private http: HttpClient, private auth: AuthService) {}

  issueCertificate(data: IssueCertificateRequest): Observable<Certificate> {
    return this.http.post<Certificate>(`${this.baseUrl}/issue-intermediate`, data);
  }

  issueEeAutogenerate(data: IssueEeAutogenerateRequest): Observable<Blob> {
    return this.http.post(`${this.baseUrl}/issue/end-entity/autogenerate`, data, {
      responseType: 'blob'
    });
  }

  getCertificates(): Observable<Certificate[]> {
    return this.http.get<Certificate[]>(`${this.baseUrl}`);
  }

  validateCertificate(id: number): Observable<boolean> {
    return this.http.get<boolean>(`${this.baseUrl}/validate/${id}`);
  }

  getMyCertificates(): Observable<Certificate[]> {
    return this.http.get<Certificate[]>(`${this.baseUrl}/my`);
  }

  getCertificateDetails(id: number): Observable<CertificateDetails> {
    return this.http.get<CertificateDetails>(`${this.baseUrl}/${id}`);
  }
  getIssuerCandidates(targetType: CertificateType): Observable<IssuerCandidate[]> {
    if (targetType === 'ROOT_CA') {
      return new Observable((obs) => {
        obs.next([]);
        obs.complete();
      });
    }

    return this.http.get<Certificate[]>(`${this.baseUrl}/issuers`, {
      params: { targetType },
      observe: 'response',
    }).pipe(
      map((resp) => {
        const orgIdHdrRaw = resp.headers.get('X-ORG-ID');
        const orgNameHdrRaw = resp.headers.get('X-ORG-NAME');

        const orgIdHdr = (orgIdHdrRaw ?? '').trim();
        const orgNameHdr = (orgNameHdrRaw ?? '').trim();

        const parsedOrgId = orgIdHdr ? Number(orgIdHdr) : null;
        const orgId = parsedOrgId != null && !Number.isNaN(parsedOrgId) ? parsedOrgId : null;

        // Keep previous context unless we *actually* received a non-empty header value
        const prevId = this.auth.getOrganizationIdFromContext();
        const prevName = this.auth.getOrganizationNameFromContext();

        const nextId = orgId !== null ? orgId : prevId;
        const nextName = orgNameHdr ? orgNameHdr : prevName;

        if (nextId !== prevId || nextName !== prevName) {
          this.auth.setOrgContext(nextId, nextName);
        }

        // Debug aid (remove later): tells you if headers are readable at all
        if (!orgNameHdr && orgNameHdrRaw == null) {
          console.warn('[issuers] X-ORG-NAME header not readable (likely missing Access-Control-Expose-Headers).');
        }

        const certs = resp.body ?? [];
        return certs.map((c) => ({
          id: c.id,
          subjectCN: c.subjectCN,
          type: c.type,
          validFrom: c.validFrom,
          validTo: c.validTo,
          revoked: c.revoked,
          valid: c.valid,
        }));
      })
    );
  }

  downloadCertificate(id: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/download/${id}`, {
      responseType: 'blob',
    });
  }

  downloadCertificatePem(id: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/download-pem/${id}`, {
      responseType: 'blob',
    });
  }

  downloadKeystorePkcs12(id: number, password: string = 'changeit'): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/download-keystore/${id}`, {
      params: { password },
      responseType: 'blob',
    });
  }

  createRootCertificate(
    commonName: string,
    ou: string,
    country: string,
    organizationId: number,
    validFrom: string,
    validTo: string
  ) {
    const params = {
      commonName,
      organizationalUnit: ou,
      country,
      organizationId,
      validFrom,
      validTo
    };

    return this.http.post('/api/certificates/root', {
      commonName,
      organizationalUnit: ou,
      country,
      organizationId,
      validFrom,
      validTo
    });
  }

  issueCertificateFromCsr(data: CsrCertificateRequest): Observable<Certificate> {
    return this.http.post<Certificate>(`${this.baseUrl}/issue-csr`, data);
  }

  revokeCertificate(id: number, reason: RevocationReason): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/revoke/${id}`, { reason });
  }

  getOrganizationCertificates(): Observable<Certificate[]> {
    return this.http.get<Certificate[]>(`${this.baseUrl}/organization`);
  }
}
