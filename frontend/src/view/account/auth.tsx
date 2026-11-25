import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { api, getToken, setToken, setUnauthorizedHandler } from "../../shared/api";
import type { AuthUser, RegisterRequest } from "../../shared/types";

interface AuthCtx {
  user: AuthUser | null;
  /** true trong luc dang hoi may chu xem token cu con hieu luc khong. */
  checking: boolean;
  login: (username: string, password: string, captcha?: string) => Promise<void>;
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
    api.me()
      .then((u) => alive && setUser(u))
      .catch(() => alive && setToken(null))
      .finally(() => alive && setChecking(false));
    return () => { alive = false; };
  }, []);

  const login = useCallback(async (username: string, password: string, captcha = "") => {
    const res = await api.login(username, password, captcha);
    setToken(res.token);
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
