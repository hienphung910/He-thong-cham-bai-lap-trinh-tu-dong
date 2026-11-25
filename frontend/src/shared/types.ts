/** Cac kieu du lieu khop 1-1 voi JSON do ApiServer.java tra ve. */

export type VerdictCode =
  | "AC"
  | "WA"
  | "TLE"
  | "MLE"
  | "RE"
  | "CE"
  | "IE"
  | "PE"
  | "PENDING";

/** Ma vai tro luu trong CSDL (cot users.role). */
export type RoleCode = "STUDENT" | "TEACHER" | "ADMIN";

/** Tai khoan dang dang nhap - KHONG bao gio chua hash hay salt mat khau. */
export interface AuthUser {
  id: string;
  username: string;
  fullName: string;
  role: RoleCode;
  roleName: string;
  canCreateProblem: boolean;
}

export interface LoginResponse {
  token: string;
  user: AuthUser;
}

export interface RegisterRequest {
  /** Vai tro tu chon khi dang ky; may chu khong nhan ADMIN. */
  role: "STUDENT" | "TEACHER";
  /** Bo mon - chi dung khi dang ky giang vien. */
  department: string;
  username: string;
  password: string;
  fullName: string;
  studentCode: string;
  className: string;
  /** File minh chung dang base64 (PNG / JPG / WEBP / PDF, toi da 1 MB). Giang vien bat buoc co; hoc sinh de "". */
  evidence: string;
}

export interface LanguageInfo {
  name: string;
  extension: string;
  available: boolean;
}

export interface Sample {
  input: string;
  output: string;
}

export interface Problem {
  id: string;
  title: string;
  statement: string;
  timeLimitMs: number;
  memoryLimitMb: number;
  comparator: string;
  maxPoints: number;
  /** Ma chu de trong lo trinh hoc (data/topics.txt), "" neu chua phan loai. */
  topic: string;
  /** 1 = De, 2 = Vua, 3 = Kho, 0 = chua danh gia. */
  difficulty: number;
  /** Thu tu hoc trong chu de (tu 1); 0 = khong khai bao, dung sau cac bai co thu tu va xep theo ma bai. */
  order: number;
  /** Ten dang nhap cua giang vien so huu bai; "" = bai thuoc kho chung. */
  owner: string;
  /** Chi gom test vi du - test an khong bao gio duoc gui xuong trinh duyet. */
  samples: Sample[];
}

/** Mot chang trong lo trinh hoc, theo dung thu tu dong cua data/topics.txt. */
export interface Topic {
  id: string;
  name: string;
  description: string;
  /** Ten lop hoc (cot thu 4 trong topics.txt), "" neu khong chia lop. */
  group: string;
  order: number;
  problemCount: number;
}

export interface TestResult {
  id: string;
  sample: boolean;
  verdict: VerdictCode;
  runtimeMs: number;
  points: number;
  message: string;
}

export interface Submission {
  id: string;
  submittedAt: string;
  author: string;
  authorName: string;
  problem: { id: string; title: string };
  language: string;
  fileName: string;
  maxPoints: number;
  verdict: VerdictCode;
  verdictName: string;
  execTimeMs: number | null;
  judgeTimeMs?: number;
  score: number | null;
  passed: number;
  total: number;
  message?: string;
  tests?: TestResult[];
  sourceCode?: string;
}

export interface ScoreRow {
  rank: number;
  username: string;
  fullName: string;
  role: RoleCode;
  roleName: string;
  total: number;
  /** So bai dat diem toi da. */
  solved: number;
  /** So bai khac nhau tung co lan nop AC (dinh nghia "so bai da giai"). */
  solvedCount: number;
  attempts: number;
  perProblem: { problemId: string; score: number }[];
}

/** Nguoi nop duoc backend lay tu token, client khong gui username nua. */
export interface SubmitRequest {
  problemId: string;
  language: string;
  code: string;
}

export interface NewTestInput {
  name: string;
  input: string;
  output: string;
  sample: boolean;
}

export interface NewProblemRequest {
  id: string;
  title: string;
  statement: string;
  timeLimitMs: number;
  memoryLimitMb: number;
  comparator: string;
  totalPoints: number;
  topic: string;
  difficulty: number;
  tests: NewTestInput[];
}

/** PUT /api/admin/problems/{id}: doi chu de va do kho cua bai da co. */
export interface UpdateProblemRequest {
  topic: string;
  difficulty: number;
  /** Bo trong cac truong duoi day thi may chu giu nguyen gia tri cu. */
  title?: string;
  statement?: string;
  timeLimitMs?: number;
  memoryLimitMb?: number;
}

export interface NewProblemResponse {
  message: string;
  problem: {
    id: string;
    title: string;
    timeLimitMs: number;
    memoryLimitMb: number;
    comparator: string;
    maxPoints: number;
    topic: string;
    difficulty: number;
    order: number;
  };
}
