import { UserRole } from '../../core/session/session.service';

export const USER_ROLE_LABELS: Record<UserRole, string> = {
  ML_ENGINEER: 'ML engineer',
  REVIEWER: 'Reviewer',
  ADMIN: 'Admin',
};

export const PROJECT_ROLE_LABELS: Record<'OWNER' | 'EDITOR' | 'VIEWER', string> = {
  OWNER: 'Owner',
  EDITOR: 'Editor',
  VIEWER: 'Viewer',
};
