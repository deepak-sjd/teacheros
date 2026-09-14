/// Which of the backend's six roles the current user has. Decoded
/// client-side from the JWT payload purely for routing/UI purposes (which
/// shell to show, which nav items to render) - this is NEVER a security
/// boundary. The backend enforces the real authorization on every request
/// regardless of what this app thinks the role is.
enum UserRole { owner, teacher, staff, student, parent, admin }

UserRole? userRoleFromJson(String? value) {
  switch (value) {
    case 'OWNER':
      return UserRole.owner;
    case 'TEACHER':
      return UserRole.teacher;
    case 'STAFF':
      return UserRole.staff;
    case 'STUDENT':
      return UserRole.student;
    case 'PARENT':
      return UserRole.parent;
    case 'ADMIN':
      return UserRole.admin;
    default:
      return null;
  }
}

/// Staff roles see the operational app (batches, roster, marking
/// attendance, fee collection). Parent/Student see a read-only view scoped
/// to just their own linked data.
bool isStaffRole(UserRole role) {
  switch (role) {
    case UserRole.owner:
    case UserRole.teacher:
    case UserRole.staff:
    case UserRole.admin:
      return true;
    case UserRole.student:
    case UserRole.parent:
      return false;
  }
}
