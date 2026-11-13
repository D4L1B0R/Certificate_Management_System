import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface MeResponse {
  userId: number;
  email: string;
  role: string;
  organizationId: number | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private accessToken: string | null = null; // keep in memory
  private refreshTokenKey = 'refresh_token_fallback'; // fallback only
  private userEmailKey = 'user_email';
  private userEmailInMemory: string | null = null;

  private orgIdKey = 'org_id';
  private orgNameKey = 'org_name';
  private orgIdInMemory: number | null = null;
  private orgNameInMemory: string | null = null;

  constructor(private http: HttpClient) {}

  async login(email: string, password: string): Promise<any> {
    const resp: any = await this.http
      .post('/api/auth/login', { email, password })
      .toPromise();

    if (resp?.accessToken) {
      this.setAccessToken(resp.accessToken);
    }

    // If backend returns refresh token, store fallback
    if (resp?.refreshToken) {
      this.setRefreshTokenFallback(resp.refreshToken);
    }

    // store user email for UI
    this.setUserEmail(email);

    return resp;
  }

  logout(): Promise<void> {
    // Ask backend to invalidate refresh if supported
    const p = this.http.post('/api/auth/logout', {}).toPromise();
    this.clearTokens();
    this.clearUserEmail();

     localStorage.removeItem('access_token');
     localStorage.removeItem(this.refreshTokenKey);
     localStorage.removeItem(this.userEmailKey);
     localStorage.removeItem(this.orgIdKey);
     localStorage.removeItem(this.orgNameKey);
     this.accessToken = null;
     this.userEmailInMemory = null;
     this.orgIdInMemory = null;
     this.orgNameInMemory = null;

    return p.then(() => undefined).catch(() => undefined);
  }

  setOrgContext(orgId: number | null, orgName: string | null) {
    this.orgIdInMemory = orgId ?? null;

    // treat blank as "no update" (prevents wiping a previously known name)
    const normalizedName = (orgName != null && orgName.trim() !== '') ? orgName : null;
    this.orgNameInMemory = normalizedName;

    if (orgId == null) localStorage.removeItem(this.orgIdKey);
    else localStorage.setItem(this.orgIdKey, String(orgId));

    if (normalizedName == null) localStorage.removeItem(this.orgNameKey);
    else localStorage.setItem(this.orgNameKey, normalizedName);
  }

  getAccessToken(): string | null {
    if (this.accessToken) return this.accessToken;

    const token = localStorage.getItem('access_token');
    this.accessToken = token;
    return token;
  }

  setAccessToken(token: string | null) {
    this.accessToken = token;

    if (token) localStorage.setItem('access_token', token);
    else localStorage.removeItem('access_token');
  }

  clearTokens() {
    this.setAccessToken(null);
    this.setRefreshTokenFallback(null);
  }

  getRefreshTokenFallback(): string | null {
    return localStorage.getItem(this.refreshTokenKey);
  }

  setRefreshTokenFallback(token: string | null) {
    if (token) localStorage.setItem(this.refreshTokenKey, token);
    else localStorage.removeItem(this.refreshTokenKey);
  }

  // user email helpers (used by UI to show logged-in user)
  setUserEmail(email: string | null) {
    this.userEmailInMemory = email;
    if (email) localStorage.setItem(this.userEmailKey, email);
    else localStorage.removeItem(this.userEmailKey);
  }

  getUserEmail(): string | null {
    if (this.userEmailInMemory) return this.userEmailInMemory;
    const fromStorage = localStorage.getItem(this.userEmailKey);
    this.userEmailInMemory = fromStorage;
    return fromStorage;
  }

  clearUserEmail() {
    this.userEmailInMemory = null;
    localStorage.removeItem(this.userEmailKey);
  }

  hasRefreshTokenFallback(): boolean {
    const t = this.getRefreshTokenFallback();
    return !!t && t.trim() !== '';
  }

  // Refresh access token by calling backend
  async refreshAccessToken(): Promise<string> {
    console.log('REFRESH CALLED');
    const fallback = this.getRefreshTokenFallback();
    if (!fallback || fallback.trim?.() === '') {
      // no refresh token -> cannot refresh
      throw Object.assign(new Error('Missing refresh token'), { code: 'NO_REFRESH_TOKEN' });
    }

    const body: any = { refreshToken: fallback };

    const resp: any = await this.http.post('/api/auth/refresh', body).toPromise();
    if (!resp || !resp.accessToken) {
      throw Object.assign(new Error('Neuspešno osvežavanje tokena'), { code: 'REFRESH_FAILED' });
    }

    this.setAccessToken(resp.accessToken);
    if (resp.refreshToken) this.setRefreshTokenFallback(resp.refreshToken);

    return resp.accessToken;
  }

  getOrganizationIdFromContext(): number | null {
    if (this.orgIdInMemory != null) return this.orgIdInMemory;
    const s = localStorage.getItem(this.orgIdKey);
    this.orgIdInMemory = s != null ? Number(s) : null;
    return this.orgIdInMemory;
  }

  getOrganizationNameFromContext(): string | null {
    if (this.orgNameInMemory != null) return this.orgNameInMemory;
    const s = localStorage.getItem(this.orgNameKey);
    this.orgNameInMemory = s;
    return s;
  }

  getMe() {
    // also update orgId context – org name arrives via issuers headers in your current backend
    return this.http.get<MeResponse>('/api/auth/me');
  }

  register(data: any) {
    return firstValueFrom(
      this.http.post('/api/auth/register', data)
    );
  }

  async verify2fa(pendingToken: string, code: string): Promise<any> {
    const resp: any = await firstValueFrom(
      this.http.post('/api/auth/2fa/verify-login', { pendingToken, code })
    );

    if (resp?.accessToken) {
      this.setAccessToken(resp.accessToken);
    }
    if (resp?.refreshToken) {
      this.setRefreshTokenFallback(resp.refreshToken);
    }
    return resp;
  }

  setup2fa() {
    return this.http.post<{ qrCodeDataUri: string; secret: string }>(
      '/api/user/2fa/setup', {}
    );
  }

  verify2faSetup(code: string) {
    return this.http.post<{ message: string }>(
      '/api/user/2fa/verify-setup', { code }
    );
  }

  disable2fa(password: string, code: string) {
    return this.http.post<{ message: string }>(
      '/api/user/2fa/disable', { password, code }
    );
  }

  get2faStatus() {
    return this.http.get<{ enabled: boolean }>('/api/user/2fa/status');
  }
}
