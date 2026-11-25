/**
 * Kiem tra du lieu nhap cua form tai khoan (dang ky, dat lai mat khau) ngay tren trinh duyet, de bao
 * loi tai tung o truoc khi gui. May chu van kiem tra lai dung cac quy tac nay (AuthService,
 * PasswordService) - phan nay chi de nguoi dung biet som minh nhap sai o dau.
 * Moi ham tra ve cau bao loi, hoac "" neu gia tri hop le.
 */

const RESERVED_USERNAMES = new Set(["admin", "administrator", "root", "system", "support", "codehub", "quantri", "quantrivien", "moderator"]);
const TOO_COMMON = new Set([
  "password1", "password123", "matkhau123", "12345678a", "a12345678", "abc12345", "abcd1234",
  "qwerty123", "1q2w3e4r", "1qaz2wsx", "iloveyou1", "admin123", "admin1234", "ptit@2026", "ptit2026",
]);
const EMAIL = /^[A-Za-z0-9_%+-]+(\.[A-Za-z0-9_%+-]+)*@[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*\.[A-Za-z]{2,}$/;

export function checkUsername(value: string): string {
  const u = value.trim();
  if (!u) return "Hãy nhập tên đăng nhập.";
  if (u.length < 3 || u.length > 32) return "Tên đăng nhập phải dài 3-32 ký tự.";
  if (!/^[A-Za-z0-9_.]+$/.test(u)) return "Tên đăng nhập chỉ gồm chữ không dấu, số, dấu chấm hoặc gạch dưới.";
  if (!/^[A-Za-z0-9]/.test(u) || u.endsWith(".") || u.includes("..")) {
    return "Tên đăng nhập phải bắt đầu bằng chữ hoặc số, không kết thúc bằng dấu chấm và không có hai dấu chấm liền nhau.";
  }
  if (RESERVED_USERNAMES.has(u.toLowerCase())) return "Tên đăng nhập này không dùng được, hãy chọn tên khác.";
  return "";
}

export function checkFullName(value: string): string {
  const name = value.trim();
  if (!name) return "Hãy nhập họ và tên.";
  if (name.length < 2 || name.length > 100 || !/^[\p{L}\p{M}][\p{L}\p{M} .'-]*$/u.test(name)) {
    return "Họ tên chỉ gồm chữ cái và khoảng trắng, từ 2 đến 100 ký tự.";
  }
  return "";
}

export function checkEmail(value: string): string {
  const email = value.trim();
  if (!email) return "Hãy nhập email.";
  if (email.length > 190) return "Email tối đa 190 ký tự.";
  const at = email.indexOf("@");
  if (at < 1 || at > 64 || !EMAIL.test(email)) return "Email không hợp lệ (ví dụ đúng: ten@example.com).";
  return "";
}

/** username: de kiem tra "mat khau khong chua ten dang nhap"; chua biet thi truyen "". */
export function checkPassword(password: string, username: string): string {
  if (!password) return "Hãy nhập mật khẩu.";
  if (password.length < 8) return "Mật khẩu phải có ít nhất 8 ký tự.";
  if (password.length > 128) return "Mật khẩu tối đa 128 ký tự.";
  if (password !== password.trim()) return "Mật khẩu không được bắt đầu hoặc kết thúc bằng khoảng trắng.";
  if (!/\p{L}/u.test(password) || !/\p{Nd}/u.test(password)) return "Mật khẩu phải có cả chữ cái và chữ số.";
  const lower = password.toLowerCase();
  if (TOO_COMMON.has(lower)) return "Mật khẩu này quá dễ đoán, hãy chọn mật khẩu khác.";
  const name = username.trim().toLowerCase();
  if (name.length >= 3 && lower.includes(name)) return "Mật khẩu không được chứa tên đăng nhập.";
  return "";
}

export function checkConfirm(password: string, confirm: string): string {
  if (!confirm) return "Hãy nhập lại mật khẩu.";
  return password === confirm ? "" : "Hai lần nhập mật khẩu không khớp.";
}

/** Ma sinh vien: khong bat buoc. */
export function checkStudentCode(value: string): string {
  const code = value.trim();
  if (!code) return "";
  if (code.length > 30) return "Mã sinh viên tối đa 30 ký tự.";
  return /^[A-Za-z0-9._-]+$/.test(code) ? "" : "Mã sinh viên chỉ gồm chữ không dấu, số, dấu chấm, gạch ngang hoặc gạch dưới.";
}

/** Ten lop / bo mon: khong bat buoc. */
export function checkLabel(value: string, label: string): string {
  const text = value.trim();
  if (!text) return "";
  if (text.length > 40) return `${label} tối đa 40 ký tự.`;
  return /^[\p{L}\p{M}\p{N} .,&/_-]*$/u.test(text) ? "" : `${label} chỉ gồm chữ, số, khoảng trắng và các dấu . , & / - _`;
}
