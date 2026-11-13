import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';


@Injectable({
  providedIn: 'root',
})
export class AdminService {

  constructor(private http: HttpClient) {}
  private baseUrl = '/api';

  getUsers() {
    return this.http.get(`${this.baseUrl}/admin/users`);
  }

  getOrganizations() {
    return this.http.get<any[]>(`${this.baseUrl}/auth/organizations`);
  }

  createCaUser(data: any) {
    return this.http.post(`${this.baseUrl}/admin/ca-users`, data);
  }

  createOrganization(name: string) {
    return this.http.post(`${this.baseUrl}/admin/organizations`, {name: name});
  }

  // rotate master key
  rotateMaster(body?: any) {
    return this.http.post(`${this.baseUrl}/admin/rotate-master`, body || {});
  }

  // get rotation status
  getRotationStatus() {
    return this.http.get(`${this.baseUrl}/admin/rotation-status`);
  }

}
