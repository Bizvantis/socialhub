// Step 36 - pure validation functions (no DOM access), so they are easy to reason about and test.
// The server validates again: client-side checks are for fast feedback, never for security.

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const USERNAME = /^[A-Za-z0-9_]{3,30}$/;

export function validateEmail(value) {
  if (!value.trim()) return "Email is required.";
  if (!EMAIL.test(value.trim())) return "Enter a valid email address.";
  return "";
}

export function validateUsername(value) {
  if (!value.trim()) return "Username is required.";
  if (!USERNAME.test(value.trim())) return "Use 3-30 letters, digits or underscores.";
  return "";
}

export function validatePassword(value) {
  if (!value) return "Password is required.";
  if (value.length < 8) return "Use at least 8 characters.";
  if (value.length > 72) return "Use at most 72 characters.";
  return "";
}

/** 0-4 strength score: length, mixed case, digits, symbols. */
export function passwordStrength(value) {
  let score = 0;
  if (value.length >= 8) score++;
  if (value.length >= 12) score++;
  if (/[a-z]/.test(value) && /[A-Z]/.test(value)) score++;
  if (/\d/.test(value) && /[^A-Za-z0-9]/.test(value)) score++;
  return Math.min(score, 4);
}
