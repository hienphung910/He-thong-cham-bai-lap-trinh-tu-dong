import type { RoleCode } from "./types";

/** Kieu du lieu cua cac module mo rong - khop 1-1 voi JSON do cac lop ...Api.java tra ve. */

export interface BasicUser {
  id: string;
  username: string;
  fullName: string;
  role: RoleCode;
  roleName: string;
  /** Lop cua hoc sinh hoac bo mon cua giang vien (chi co o /api/users). */
  detail?: string;
  /** false = giang vien tu dang ky dang cho quan tri vien duyet (chi co o /api/users). */
  approved?: boolean;
  /** Tai khoan dang cho duyet co gui kem file minh chung khong (chi co o /api/users). */
  hasEvidence?: boolean;
}

export interface ProblemRef { id: string; title: string }
export interface Count { key: string; count: number }

// ---- to chuc, lop hoc, phu huynh ----
export interface Organization { id: string; name: string; address: string; description: string; classCount: number }

export interface Classroom {
  id: string;
  name: string;
  organizationId: string;
  organizationName: string;
  courseId: string;
  courseTitle: string;
  teacherName: string;
  studentCount: number;
  canManage: boolean;
  /** Giang vien phu trach lop (hoac quan tri vien): xoa lop, xoa hoc sinh. */
  isOwner: boolean;
  /** Hoc sinh da duoc moi nhung chua dong y - chi nguoi quan ly lop nhan duoc, khi lay chi tiet mot lop. */
  invited?: BasicUser[];
  /** Ma de hoc sinh tu tham gia lop; "" voi nguoi khong quan ly lop. */
  joinCode: string;
  practiceCount: number;
  /** Bai luyen tap giang vien them rieng cho lop - chi co khi lay chi tiet mot lop. */
  practice?: { id: string; title: string; difficulty: number; solved: boolean }[];
  /** Chi co khi lay chi tiet mot lop. */
  students?: (BasicUser & { solved: number })[];
}

export interface ParentContact {
  id: string; fullName: string; phone: string; email: string; studentName: string; studentUsername: string;
}
export interface ParentReport { parentName: string; studentName: string; lines: string[] }

// ---- khoa hoc, tai lieu, bai tap giao ----
export interface Lesson { title: string; content: string; problems: ProblemRef[] }
export interface Course { id: string; title: string; description: string; level: string; lessons: Lesson[] }
export interface DocumentItem {
  id: string; courseId: string; courseTitle: string; title: string;
  kind: "TEXT" | "LINK"; content: string; authorName: string; createdAt: string;
}

export interface Assignment {
  id: string; classroomId: string; classroomName: string; title: string; description: string;
  dueAt: string; overdue: boolean; createdBy: string; solvedCount: number; total: number;
  /**
   * status cua tung bai doi voi nguoi dang xem: DONE = AC trong khoang tu luc giao toi han nop,
   * LATE = chi AC sau han, EARLIER = da AC tu truoc khi giao, TODO = chua AC. solved = (status == DONE).
   */
  canManage: boolean; problems: (ProblemRef & { solved: boolean; status: AssignmentStatus })[];
}
export type AssignmentStatus = "DONE" | "LATE" | "EARLIER" | "TODO";
export interface AssignmentProgress {
  title: string; problemIds: string[];
  rows: { username: string; fullName: string; solvedCount: number; solved: boolean[]; statuses: AssignmentStatus[] }[];
}

// ---- binh luan duoi bai tap ----
export interface ProblemComment {
  id: string; problemId: string; authorName: string; authorRole: string; text: string; createdAt: string;
  /** Binh luan khong con qua buoc duyet (may chu loc luc gui) nen luon la APPROVED. */
  status: "APPROVED";
  mine: boolean; canDelete: boolean;
  /** Chi co o trang quan ly binh luan. */
  problemTitle?: string;
}

// ---- buoi hoc, phong hoc, diem danh, ban ghi ----
export interface Meeting {
  id: string; classroomId: string; classroomName: string; title: string; startAt: string;
  durationMin: number; hostName: string; status: "SCHEDULED" | "LIVE" | "ENDED";
  recorded: boolean; canManage: boolean;
  endAt: string;
  /** Theo gio: UPCOMING chua toi gio, OPEN dang trong gio hoc (vao phong duoc), ENDED da het gio. */
  phase: "UPCOMING" | "OPEN" | "ENDED";
  hasLink: boolean;
  /** Link phong hoc. Hoc sinh chi nhan duoc khi phase = OPEN; nguoi quan ly lop luon nhan duoc. */
  link: string;
}
export interface Stroke { color: string; points: string }
export interface RoomState {
  meeting: Meeting;
  participants: (BasicUser & { host: boolean; handRaised: boolean })[];
  code: string; language: string; codeVersion: number;
  collaborative: boolean; canEditCode: boolean; isHost: boolean; handRaised: boolean;
  strokesFrom: number; strokeCount: number; strokes: Stroke[];
  /** Tang 1 moi lan bang bi xoa. */
  boardVersion: number;
}
export interface AttendanceSheet {
  title: string; status: string;
  rows: (BasicUser & { status: string; joinedAt: string | null })[];
}
export interface Recording {
  id: string; meetingId: string; title: string; durationMin: number; createdAt: string;
  strokeCount: number; chatCount: number;
  code?: string; language?: string; strokes?: Stroke[];
  chat?: { senderName: string; text: string; sentAt: string }[];
}

// ---- cuoc thi ----
export interface ContestRow {
  rank: number; username: string; fullName: string; solved: number;
  /** Phut giai xong bai gan nhat; chi de phan dinh thu hang khi cung so bai. */
  lastSolvedMinute: number;
  cells: { problemId: string; wrong: number; solvedAt: number | null }[];
}
export interface Contest {
  id: string; title: string; description: string; startAt: string; endAt: string;
  durationMin: number; status: "UPCOMING" | "RUNNING" | "ENDED"; problems: ProblemRef[];
  /** So bai cua ky thi. problemsHidden = true: ky thi chua bat dau nen danh sach bai chua duoc gui ve. */
  problemCount: number; problemsHidden: boolean;
  /** Ho ten nguoi tao; canManage = nguoi dang xem duoc them / bot bai va xoa ky thi nay. */
  ownerName: string; canManage: boolean;
  minute?: number; standings?: ContestRow[];
  /** Lop duoc thi: chi thanh vien cua lop nay thay ky thi, chi hoc sinh cua lop vao thi duoc. */
  classroomId: string; classroomName: string;
  /** Nguoi tao ky thi bat / tat: co ghi lai viec roi tab, sao chep, dan cua thi sinh hay khong. */
  proctored: boolean;
  /** Nguoi dang xem da bam "Vao thi" / da nop bai som chua (finishedAt = luc nop, "" neu chua). */
  joined: boolean; finished: boolean; finishedAt: string;
  /** Nguoi dang xem la hoc sinh dang trong bai thi: giao dien chi con man hinh ky thi. */
  sitting: boolean;
  /** Chi nguoi tao ky thi nhan duoc: thi sinh da vao thi. */
  participants?: { username: string; fullName: string; joinedAt: string; finishedAt: string }[];
}
// ---- giam sat ky thi ----
export type ProctorEventType =
  | "TAB_HIDDEN" | "TAB_RETURN" | "WINDOW_BLUR" | "WINDOW_RETURN" | "COPY" | "CUT" | "PASTE" | "PAGE_CLOSED"
  | "MONITOR_START";
export interface ProctorLog {
  /** Tong so su kien da ghi; events chi chua 300 su kien moi nhat. */
  total: number;
  /** Mot dong cho moi thi sinh co ghi nhan, nhieu vi pham nhat dung truoc. */
  students: {
    username: string; fullName: string; violations: number;
    tabHidden: number; windowBlur: number; copy: number; paste: number; pageClosed: number; lastAt: string;
  }[];
  events: { id: string; at: string; username: string; fullName: string; type: ProctorEventType; detail: string; problemId: string }[];
}
export interface ContestEvent { minute: number; username: string; fullName: string; problemId: string; verdict: string }

// ---- phan tich, thanh tich, bao cao ----
export interface TopicStat {
  id: string; name: string; group: string; total: number; solved: number;
  attempts: number; failed: number; score: number; level: string; weak: boolean;
}
export interface Profile {
  user: BasicUser; totalSubmissions: number; solvedCount: number; acRate: number;
  verdicts: Count[]; activity: Count[]; topics: TopicStat[]; weaknessNotes: string[];
  recommendations: { problemId: string; title: string; difficulty: number; reason: string }[];
  points: number; rankName: string; streak: number;
  badges: { name: string; description: string; earned: boolean }[];
}
export interface Dashboard {
  users: number; students: number; teachers: number; problems: number; submissions: number;
  classrooms: number; courses: number; contests: number; revenue: number;
  verdicts: Count[]; activity: Count[];
  topProblems: { id: string; title: string; attempts: number; acRate: number }[];
}
export interface PointRow { rank: number; username: string; fullName: string; points: number; rankName: string; solved: number }

// ---- cong dong ----
export interface NotificationList {
  unread: number;
  /** link: trang mo ra khi bam vao thong bao - ten trang hoac "Problems/<ma bai>"; "" = khong co. */
  items: { id: string; title: string; body: string; createdAt: string; read: boolean; link: string }[];
}
export type NotificationItem = NotificationList["items"][number];
/** seq: so thu tu cua tin trong kenh; trinh duyet gui lai seq cua tin cuoi de chi nhan cac tin moi hon. */
export interface ChatMessage { id: string; seq: number; senderName: string; senderRole: string; text: string; sentAt: string }
export interface ForumPost {
  id: string; title: string; authorId: string; authorName: string; problemId: string;
  createdAt: string; replyCount: number;
  body?: string; replies?: { authorName: string; body: string; createdAt: string }[];
}

// ---- thanh toan, he thong ----
export interface Payment {
  id: string; planName: string; amount: number; createdAt: string; expiresAt: string;
  active: boolean; buyerName?: string;
}
export interface Billing {
  simulated: boolean; currentPlan: string;
  plans: { code: string; name: string; price: number; features: string }[];
  payments: Payment[];
}
/** bannedWords: tu cam bo sung cho bo loc binh luan, cach nhau dau phay - chi quan tri vien nhan duoc. */
export interface Settings { announcement: string; allowRegister: boolean; bannedWords?: string }
export interface AuditRow { id: string; time: string; username: string; action: string; detail: string; success: boolean }

// ---- cong cu ----
export interface RunResult {
  compileError: string; stdout?: string; stderr?: string; exitCode?: number; timeMs?: number; timedOut?: boolean;
}
export interface StressResult { found: boolean; rounds: number; message: string; input: string; expected: string; actual: string }
export interface TestcaseItem { name: string; sample: boolean; points: number; input: string; output: string }
export interface AssistantReply { simulated: boolean; lines: string[] }
