import { createContext, useContext } from "react";

/** Ten cac trang cua ung dung - cung la khoa cua cac muc trong menu tren cung (xem App.tsx). */
export type PageKey =
  | "Problems" | "Classrooms" | "Assignments" | "Contests" | "Meetings"
  | "Playground" | "CpTools"
  | "Analytics" | "Account"
  | "Chat" | "Notifications"
  | "Submissions" | "MySubmissions" | "Leaderboard"
  | "Dashboard" | "MyProblems"
  | "Admin" | "Accounts" | "Comments" | "Organizations" | "System" | "Audit";

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

/** Tach chuoi "K001, K002 K003" thanh mang ma bai. */
export function parseIds(text: string): string[] {
  return text.split(/[\s,;]+/).map((s) => s.trim().toUpperCase()).filter((s) => s !== "");
}
