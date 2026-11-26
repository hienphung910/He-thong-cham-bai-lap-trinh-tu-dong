import type {
  AuthUser,
  LanguageInfo,
  LoginResponse,
  NewProblemRequest,
  NewProblemResponse,
  Problem,
  RegisterRequest,
  ScoreRow,
  Submission,
  SubmitRequest,
  Topic,
  UpdateProblemRequest,
} from "./types";

/**
 * Lop mong goi REST API cua may chu Java.
 * Khi chay "npm run dev", Vite chuyen tiep /api sang http://localhost:8080 (xem vite.config.ts).
 * Khi chay che do --serve, frontend duoc chinh may chu Java phuc vu nen cung duong dan.
 *
 * Token dang nhap tu dong gan vao header Authorization cua moi request. Noi giu token:
 *  - "Ghi nho dang nhap": localStorage - dong tab, tat trinh duyet, mo tab moi van con dang nhap (may chu
 *    giu phien 30 ngay ke tu lan dung gan nhat);
 *  - khong ghi nho: sessionStorage - dong tab la mat.
 */

export const TOKEN_KEY = "oj.token";

let token: string | null = readStoredToken();
let onUnauthorized: (() => void) | null = null;

function readStoredToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY) ?? localStorage.getItem(TOKEN_KEY);
  } catch {
    return null; // trinh duyet chan bo nho trang (che do rieng tu) -> chi giu trong bo nho
  }
}

/** remember = true: giu token qua ca khi dong tab / trinh duyet (localStorage). */
export function setToken(value: string | null, remember = false) {
  token = value;
  try {
    // Ghi de truc tiep (khong xoa roi ghi lai): cac tab khac nghe su kien "storage" se khong tuong la vua dang xuat.
    if (value && remember) {
      localStorage.setItem(TOKEN_KEY, value);
      sessionStorage.removeItem(TOKEN_KEY);
    } else if (value) {
      sessionStorage.setItem(TOKEN_KEY, value);
      localStorage.removeItem(TOKEN_KEY);
    } else {
      sessionStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(TOKEN_KEY);
    }
  } catch {
    // khong luu duoc thi van dung duoc trong phien hien tai
  }
}

/** Tab khac vua dang xuat (xoa token trong localStorage): tab nay cung bo token dang giu. */
export function forgetTokenFromOtherTab(storedToken: string | null): boolean {
  if (storedToken !== null || token === null) return false;
  let mine: string | null = null;
  try { mine = sessionStorage.getItem(TOKEN_KEY); } catch { mine = null; }
  // Tab nay dang nhap rieng (khong ghi nho) thi khong lien quan toi tab kia.
  if (mine !== null) return false;
  token = null;
  return true;
}

/** Tab khac vua dang nhap co ghi nho: tab nay (dang o man hinh dang nhap) dung luon token do. */
export function adoptTokenFromOtherTab(storedToken: string | null): boolean {
  if (storedToken === null || token !== null) return false;
  token = storedToken;
  return true;
}

export function getToken(): string | null {
  return token;
}

/** App dang ky ham nay de tu dang xuat khi may chu tra ve 401. */
export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(path, { ...init, headers: { ...headers, ...(init?.headers ?? {}) } });
  const text = await res.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      throw new Error(`Máy chủ trả về dữ liệu không phải JSON: ${text.slice(0, 120)}`);
    }
  }
  if (!res.ok) {
    if (res.status === 401) {
      setToken(null);
      onUnauthorized?.();
    }
    const message =
      data && typeof data === "object" && data !== null && "error" in data
        ? String((data as { error: unknown }).error)
        : `Lỗi HTTP ${res.status}`;
    throw new Error(message);
  }
  return data as T;
}

export function postJson<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, { method: "POST", body: JSON.stringify(body) });
}

export function putJson<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, { method: "PUT", body: JSON.stringify(body) });
}

export const api = {
  // --- xac thuc ---
  /** Cau hinh cong khai cho form dang nhap / dang ky: turnstileSiteKey khac "" thi phai lam CAPTCHA truoc khi gui. */
  config: () => request<{ turnstileSiteKey: string; allowRegister: boolean; passwordReset: boolean }>("/api/config"),
  /** pending = true: tai khoan giang vien vua tao con phai cho quan tri vien duyet. */
  /** emailVerification = true: email chua co hieu luc, may chu vua gui thu xac minh toi dia chi do. */
  register: (body: RegisterRequest) =>
    postJson<{ message: string; pending: boolean; emailVerification: boolean }>("/api/auth/register", body),
  /** Xac minh email bang ma trong link cua thu. */
  verifyEmail: (token: string) => postJson<{ ok: boolean; username: string; email: string }>("/api/auth/email/verify", { token }),
  /** captcha: token Turnstile, chi can khi may chu bat CAPTCHA (xem config()). */
  /** remember: "Ghi nho dang nhap" - may chu giu phien 30 ngay ke tu lan dung gan nhat thay vi 12 gio. */
  login: (username: string, password: string, captcha = "", remember = false) =>
    postJson<LoginResponse>("/api/auth/login", { username, password, captcha, remember }),
  me: () => request<AuthUser>("/api/auth/me"),
  /** Quen mat khau: account la ten dang nhap hoac email. May chu luon tra loi nhu nhau du tai khoan co hay khong. */
  forgotPassword: (account: string, captcha = "") =>
    postJson<{ ok: boolean; message: string; retryAfter: number }>("/api/auth/forgot", { account, captcha }),
  /** Ma trong link dat lai mat khau con dung duoc khong. */
  checkResetToken: (token: string) => postJson<{ valid: boolean }>("/api/auth/reset/check", { token }),
  resetPassword: (token: string, password: string) =>
    postJson<{ ok: boolean; username: string }>("/api/auth/reset", { token, password }),
  logout: () => postJson<{ ok: boolean }>("/api/auth/logout", {}),

  // --- du lieu ---
  languages: () => request<LanguageInfo[]>("/api/languages"),
  problems: () => request<Problem[]>("/api/problems"),
  topics: () => request<Topic[]>("/api/topics"),
  submissions: () => request<Submission[]>("/api/submissions"),
  submission: (id: string) => request<Submission>(`/api/submissions/${encodeURIComponent(id)}`),
  scoreboard: () => request<ScoreRow[]>("/api/scoreboard"),
  /** Nop bai: may chu cham xong moi tra ve ban ghi bai nop kem ket qua tung test. */
  submit: (body: SubmitRequest) => postJson<Submission>("/api/submit", body),

  // --- quan tri (backend van kiem tra lai vai tro) ---
  createProblem: (body: NewProblemRequest) =>
    postJson<NewProblemResponse>("/api/admin/problems", body),
  /** Sua bai da co: ten, de bai, gioi han, chu de, do kho. */
  updateProblem: (id: string, body: UpdateProblemRequest) =>
    putJson<NewProblemResponse>(`/api/admin/problems/${encodeURIComponent(id)}`, body),
  /** Xoa bai; may chu tu choi neu bai da co nguoi nop hoac dang duoc dung trong ky thi / bai giao / lop. */
  deleteProblem: (id: string) =>
    request<{ ok: boolean; message: string }>(`/api/admin/problems/${encodeURIComponent(id)}`, { method: "DELETE" }),
};
