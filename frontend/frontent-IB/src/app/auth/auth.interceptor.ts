import { Injectable, inject } from '@angular/core';
import {
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
  HTTP_INTERCEPTORS,
} from '@angular/common/http';
import { Observable, from, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { AuthService } from './auth.service';
import { Router } from '@angular/router';

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  private auth = inject(AuthService);
  private router = inject(Router);

  // Single-flight refresh state
  private refreshInProgress = false;
  private refreshPromise: Promise<string> | null = null;

  private getPath(url: string): string {
    try {
      // handles absolute URLs
      return new URL(url).pathname;
    } catch {
      // handles relative URLs
      return url.split('?')[0];
    }
  }

  private isApiCall(url: string): boolean {
    const path = this.getPath(url);
    return path.startsWith('/api/');
  }

  private isPublicAuthEndpoint(url: string): boolean {
    const path = this.getPath(url);
    return (
      path === '/api/auth/login' ||
      path === '/api/auth/activate' ||
      path === '/api/auth/refresh'
    );
  }

  private isRefreshEndpoint(url: string): boolean {
    return this.getPath(url) === '/api/auth/refresh';
  }

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    // Only touch API calls; ignore assets/external calls
    if (!this.isApiCall(req.url)) {
      return next.handle(req);
    }

    // Skip token injection for public auth endpoints and refresh itself (avoid recursion)
    if (this.isPublicAuthEndpoint(req.url)) {
      return next.handle(req);
    }

    const access = this.auth.getAccessToken();
    const authReq = access
      ? req.clone({ setHeaders: { Authorization: `Bearer ${access}` } })
      : req;

    return next.handle(authReq).pipe(
      catchError((err) => {
        const status = err && err.status ? err.status : null;

        // If refresh itself failed, abort session (avoid loops)
        if ((status === 401 || status === 403) && this.isRefreshEndpoint(req.url)) {
          this.auth.logout();
          this.router.navigate(['/login']);
          return throwError(() => err);
        }

        if (status === 401 || status === 403) {
          // If we do not have refresh token, do NOT attempt refresh -> end session cleanly.
          if (!this.auth.hasRefreshTokenFallback()) {
            this.auth.logout();
            this.router.navigate(['/login']);
            return throwError(() => err);
          }

          if (!this.refreshInProgress) {
            this.refreshInProgress = true;
            this.refreshPromise = this.auth
              .refreshAccessToken()
              .then((newToken) => {
                this.refreshInProgress = false;
                this.refreshPromise = null;
                return newToken;
              })
              .catch((e) => {
                this.refreshInProgress = false;
                this.refreshPromise = null;
                this.auth.logout();
                this.router.navigate(['/login']);
                throw e;
              });
          }

          return from(this.refreshPromise!).pipe(
            switchMap((newToken) => {
              const retried = req.clone({
                setHeaders: { Authorization: `Bearer ${newToken}` },
              });
              return next.handle(retried);
            }),
            catchError((e) => throwError(() => e))
          );
        }

        return throwError(() => err);
      })
    );
  }
}

export const authInterceptorProvider = {
  multi: true,
  useClass: AuthInterceptor,
  provide: HTTP_INTERCEPTORS,
};
