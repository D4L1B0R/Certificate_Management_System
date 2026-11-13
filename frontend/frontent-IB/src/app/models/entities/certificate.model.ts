export type CertificateType = 'ROOT_CA' | 'INTERMEDIATE_CA' | 'END_ENTITY';

export interface Certificate {
    id: number;
    subjectCN: string;
    issuerCN: string;
    type: CertificateType | string;
    validFrom: string;
    validTo: string;
    serialNumber: string;
    revoked: boolean;
    valid: boolean;
    hasPrivateKey: boolean;
}

export interface CertificateDetails {
    id: number;
    subjectCN: string;
    organizationalUnit: string;
    country: string;
    issuerCN: string;
    type: CertificateType | string;
    validFrom: string;
    validTo: string;
    serialNumber: string;
    revoked: boolean;
    certificatePem: string;
    signatureAlgorithm: string;
    publicKeyAlgorithm: string;
    publicKeySize: number;
    subjectDN: string;
    issuerDN: string;
}

export interface CertificateExtensions {
    basicConstraintsCa?: boolean;
    pathLenConstraint?: number | null;

    keyUsages?: string[];
    extendedKeyUsages?: string[];
}

export interface CsrCertificateRequest {
  issuerCertificateId: number;
  validFrom: string;
  validTo: string;
  csrPem: string;
}

export interface IssueCertificateRequest {
  type: CertificateType;
  commonName: string;
  organizationId: number;
  organizationalUnit: string;
  country: string;
  validFrom: string;
  validTo: string;
  issuerCertificateId: number | null;

  basicConstraintsCA?: boolean;
  keyCertSign?: boolean;
  digitalSignature?: boolean;
  keyEncipherment?: boolean;

  extensions?: CertificateExtensions;
}

export interface IssueEeAutogenerateRequest {
  commonName: string;
  organizationId: number;
  organizationalUnit: string;
  country: string;
  email?: string;
  state?: string;
  locality?: string;
  validFrom: string;
  validTo: string;
  issuerCertificateId: number;
  digitalSignature?: boolean;
  keyEncipherment?: boolean;
  keystorePassword: string;
}

export interface IssuerCandidate {
    id: number;
    subjectCN: string;
    type: CertificateType | string;
    validFrom: string;
    validTo: string;
    revoked?: boolean;
    valid?: boolean;
}

export enum RevocationReason {
  UNSPECIFIED = 'UNSPECIFIED',
  KEY_COMPROMISE = 'KEY_COMPROMISE',
  CA_COMPROMISE = 'CA_COMPROMISE',
  AFFILIATION_CHANGED = 'AFFILIATION_CHANGED',
  SUPERSEDED = 'SUPERSEDED',
  CESSATION_OF_OPERATION = 'CESSATION_OF_OPERATION',
  CERTIFICATE_HOLD = 'CERTIFICATE_HOLD',
  REMOVE_FROM_CRL = 'REMOVE_FROM_CRL',
  PRIVILEGE_WITHDRAWN = 'PRIVILEGE_WITHDRAWN',
  AA_COMPROMISE = 'AA_COMPROMISE'
}
