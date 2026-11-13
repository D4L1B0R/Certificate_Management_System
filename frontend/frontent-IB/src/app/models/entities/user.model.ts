import {Role} from '../enums/role';

export interface User {
  id: number;
  email: string;
  firstName: string;
  lastName: string;
  organization: string;
  role: Role;
  enabled: boolean;
}
