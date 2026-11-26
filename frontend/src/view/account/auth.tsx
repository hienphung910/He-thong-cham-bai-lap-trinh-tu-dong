import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { TOKEN_KEY, adoptTokenFromOtherTab, api, forgetTokenFromOtherTab, getToken, setToken, setUnauthorizedHandler } from "../../shared/api";
import type { AuthUser, RegisterRequest } from "../../shared/types";

interface AuthCtx {
  user: AuthUser | null;
  /** true trong luc dang hoi may chu xem token cu con hieu luc khong. */
  checking: boolean;
  /** remember: "Ghi nho dang nhap" - dong tab / trinh duyet van con dang nhap. */
  login: (username: string, password: string, captcha?: string, remember?: boolean) => Promise<void>;
  /**
   * pending: tai khoan vua tao con phai cho quan tri vien duyet (giang vien).
   * emailVerification: may chu vua gui thu xac minh toi email da khai.
   */
  register: (body: RegisterRequest) => Promise<{ pending: boolean; emailVerification: boolean }>;
  logout: () => void;
  /** Hoi lai may chu thong tin tai khoan dang dang nhap (sau khi doi email...). */
  refresh: () => Promise<void>;
}

const Ctx = createContext<AuthCtx>({
  user: null,
  checking: true,
  login: async () => {},
  register: async () => ({ pending: false, emailVerification: false }),
  logout: () => {},
  refresh: async () => {},
});

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [checking, setChecking] = useState(true);

  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null));
    return () => setUnauthorizedHandler(null);
  }, []);

  useEffect(() => {
    let alive = true;
    if (!getToken()) {
      setChecking(false);
      return;
    }
    // Token het han / da dang xuat thi may chu tra 401 va request() tu xoa token. Loi khac (mat mang, may
    // chu dang deploy lai) thi GIU token: tai lai trang sau do van con dang nhap.
    api.me()
      .then((u) => alive && setUser(u))
      .catch(() => undefined)
      .finally(() => alive && setChecking(false));
    return () => { alive = false; };
  }, []);

  // Dong bo giua cac tab dung chung "ghi nho dang nhap": tab nay dang xuat thi tab kia cung ve man hinh
  // dang nhap; tab kia dang nhap thi tab dang o man hinh dang nhap vao luon.
  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key !== TOKEN_KEY && e.key !== null) return;
      const stored = e.key === null ? null : e.newValue;
      if (forgetTokenFromOtherTab(stored)) setUser(null);
      else if (adoptTokenFromOtherTab(stored)) api.me().then(setUser).catch(() => undefined);
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  const login = useCallback(async (username: string, password: string, captcha = "", remember = false) => {
    const res = await api.login(username, password, captcha, remember);
    setToken(res.token, remember);
    setUser(res.user);
  }, []);

  const register = useCallback(async (body: RegisterRequest) => {
    const res = await api.register(body);
    return { pending: res.pending === true, emailVerification: res.emailVerification === true };
  }, []);

  const logout = useCallback(() => {
    api.logout().catch(() => undefined);
    setToken(null);
    setUser(null);
  }, []);

  const refresh = useCallback(async () => {
    setUser(await api.me());
  }, []);

  return <Ctx.Provider value={{ user, checking, login, register, logout, refresh }}>{children}</Ctx.Provider>;
}

export function useAuth() {
  return useContext(Ctx);
}
