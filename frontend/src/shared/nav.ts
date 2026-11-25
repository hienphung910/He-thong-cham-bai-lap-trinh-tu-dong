import { createContext, useCallback, useContext, useEffect, useState } from "react";

/** Ten cac trang cua ung dung - cung la khoa cua cac muc trong menu tren cung (xem App.tsx). */
export type PageKey =
  | "Problems" | "Classrooms" | "Assignments" | "Contests" | "Meetings"
  | "Playground" | "CpTools"
  | "Analytics" | "Account"
  | "Chat" | "Notifications"
  | "Submissions" | "MySubmissions" | "Leaderboard"
  | "Dashboard" | "MyProblems"
  | "Admin" | "Accounts" | "Comments" | "MyComments" | "Organizations" | "System" | "Audit";

interface Nav {
  /** Chuyen sang trang khac. */
  go: (page: PageKey) => void;
  /** Mo trang lam bai cua mot bai tap (dung cho cac duong link toi bai). */
  openProblem: (problemId: string) => void;
}

export const NavContext = createContext<Nav>({ go: () => undefined, openProblem: () => undefined });

export function useNav() {
  return useContext(NavContext);
}

/**
 * Ung dung la mot trang duy nhat (SPA): doi trang chi la doi state cua React, nen neu khong ghi
 * vao lich su trinh duyet thi nut Back se roi han khoi CodeHub. Moi lan chuyen trang, App.tsx
 * ghi mot muc vao lich su (history.pushState) dang { route, sub }:
 *   - route: trang dang mo va ma bai dang lam (App.tsx quan ly, co mat tren URL sau dau #),
 *   - sub  : trang con ben trong mot trang (dang xem lop nao, ky thi nao...) - do hook duoi day ghi.
 * Su kien nay duoc phat khi App.tsx vua chuyen trang, de cac trang con dang mo doc lai trang thai.
 */
export const NAV_EVENT = "oj-nav";

function subState(key: string): unknown {
  const state = window.history.state as { sub?: Record<string, unknown> } | null;
  return state?.sub?.[key];
}

/**
 * Giong useState, nhung nut Back cua trinh duyet quay lai duoc gia tri truoc do.
 * Dung cho "trang con" cua mot trang, vi du dang xem chi tiet lop nao (null = dang o danh sach).
 * Dat gia tri khac initial -> ghi mot muc lich su moi; dat ve initial -> lui lai mot muc.
 */
export function useBackState<T>(key: string, initial: T): [T, (value: T) => void] {
  const [value, setValue] = useState<T>(() => (subState(key) as T | undefined) ?? initial);

  useEffect(() => {
    const sync = () => setValue((subState(key) as T | undefined) ?? initial);
    window.addEventListener("popstate", sync);
    window.addEventListener(NAV_EVENT, sync);
    return () => {
      window.removeEventListener("popstate", sync);
      window.removeEventListener(NAV_EVENT, sync);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  const set = useCallback((next: T) => {
    if (next === initial) {
      // Nut "quay lai" tren giao dien lam dung viec cua nut Back; popstate se dat lai gia tri.
      if (subState(key) !== undefined) window.history.back();
      else setValue(initial);
      return;
    }
    const state = (window.history.state ?? {}) as { sub?: Record<string, unknown> };
    window.history.pushState({ ...state, sub: { ...(state.sub ?? {}), [key]: next } }, "");
    setValue(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  return [value, set];
}

/** Tach chuoi "K001, K002 K003" thanh mang ma bai. */
export function parseIds(text: string): string[] {
  return text.split(/[\s,;]+/).map((s) => s.trim().toUpperCase()).filter((s) => s !== "");
}
