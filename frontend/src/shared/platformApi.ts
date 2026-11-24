import { postJson, putJson, request } from "./api";
import type {
  AssistantReply, Assignment, AssignmentProgress, AttendanceSheet, AuditRow, BasicUser, Billing,
  ChatMessage, Classroom, Contest, ContestEvent, Course, Dashboard, DocumentItem, ForumPost, Meeting,
  NotificationList, Organization, ParentContact, ParentReport, Payment, PointRow, Profile, Recording,
  RoomState, RunResult, Settings, StressResult, TestcaseItem,
} from "./platformTypes";

/** Goi REST API cua cac module mo rong (cac lop ...Api.java). Moi ham ung voi mot endpoint. */

type Ok = { ok: boolean };
const enc = encodeURIComponent;
const del = <T = Ok>(path: string) => request<T>(path, { method: "DELETE" });

export const papi = {
  // --- tai khoan (Module 1, 35) ---
  users: () => request<BasicUser[]>("/api/users"),
  createUser: (body: { role: string; username: string; password: string; fullName: string; detail: string }) =>
    postJson<BasicUser>("/api/admin/users", body),
  changePassword: (oldPassword: string, newPassword: string) =>
    postJson<Ok>("/api/auth/password", { oldPassword, newPassword }),

  // --- to chuc, lop hoc, phu huynh (Module 2, 3, 32) ---
  organizations: () => request<Organization[]>("/api/organizations"),
  createOrganization: (body: { name: string; address: string; description: string }) =>
    postJson<Ok>("/api/organizations", body),
  deleteOrganization: (id: string) => del(`/api/organizations/${enc(id)}`),

  classrooms: () => request<Classroom[]>("/api/classrooms"),
  classroom: (id: string) => request<Classroom>(`/api/classrooms/${enc(id)}`),
  createClassroom: (body: { name: string; organizationId: string; courseId: string }) =>
    postJson<Classroom>("/api/classrooms", body),
  deleteClassroom: (id: string) => del(`/api/classrooms/${enc(id)}`),
  joinClassroom: (id: string) => postJson<Classroom>(`/api/classrooms/${enc(id)}/join`, {}),
  addStudent: (id: string, username: string) => postJson<Ok>(`/api/classrooms/${enc(id)}/students`, { username }),
  removeStudent: (id: string, userId: string) => del(`/api/classrooms/${enc(id)}/students/${enc(userId)}`),
  addPracticeProblems: (id: string, problemIds: string[]) =>
    postJson<Ok>(`/api/classrooms/${enc(id)}/problems`, { problemIds }),
  removePracticeProblem: (id: string, problemId: string) =>
    del(`/api/classrooms/${enc(id)}/problems/${enc(problemId)}`),
  addAssistant: (id: string, username: string) => postJson<Ok>(`/api/classrooms/${enc(id)}/assistants`, { username }),
  removeAssistant: (id: string, userId: string) => del(`/api/classrooms/${enc(id)}/assistants/${enc(userId)}`),

  parents: () => request<ParentContact[]>("/api/parents"),
  addParent: (body: { studentUsername: string; fullName: string; phone: string; email: string }) =>
    postJson<Ok>("/api/parents", body),
  deleteParent: (id: string) => del(`/api/parents/${enc(id)}`),
  parentReport: (id: string) => request<ParentReport>(`/api/parents/${enc(id)}/report`),

  // --- khoa hoc, tai lieu, bai tap giao (Module 4, 36, 13) ---
  courses: () => request<Course[]>("/api/courses"),
  createCourse: (body: { title: string; description: string; level: string }) => postJson<Course>("/api/courses", body),
  deleteCourse: (id: string) => del(`/api/courses/${enc(id)}`),
  addLesson: (courseId: string, body: { title: string; content: string; problemIds: string[] }) =>
    postJson<Course>(`/api/courses/${enc(courseId)}/lessons`, body),
  documents: () => request<DocumentItem[]>("/api/documents"),
  addDocument: (body: { courseId: string; title: string; kind: string; content: string }) =>
    postJson<Ok>("/api/documents", body),
  deleteDocument: (id: string) => del(`/api/documents/${enc(id)}`),

  assignments: () => request<Assignment[]>("/api/assignments"),
  createAssignment: (body: { classroomId: string; title: string; description: string; problemIds: string[]; dueAt: string }) =>
    postJson<Ok>("/api/assignments", body),
  deleteAssignment: (id: string) => del(`/api/assignments/${enc(id)}`),
  assignmentProgress: (id: string) => request<AssignmentProgress>(`/api/assignments/${enc(id)}/progress`),

  // --- buoi hoc, phong hoc, diem danh, ban ghi (Module 14-20, 37) ---
  meetings: () => request<Meeting[]>("/api/meetings"),
  createMeeting: (body: { classroomId: string; title: string; startAt: string; durationMin: number; link: string }) =>
    postJson<Ok>("/api/meetings", body),
  /** Dan / sua link phong hoc cua mot buoi trong lich; link rong = go link. */
  setMeetingLink: (id: string, link: string) => putJson<Ok>(`/api/meetings/${enc(id)}/link`, { link }),
  startMeeting: (id: string) => postJson<Ok>(`/api/meetings/${enc(id)}/start`, {}),
  endMeeting: (id: string) => postJson<Ok>(`/api/meetings/${enc(id)}/end`, {}),
  deleteMeeting: (id: string) => del(`/api/meetings/${enc(id)}`),
  room: (id: string, knownStrokes: number, knownBoard: number) =>
    request<RoomState>(`/api/meetings/${enc(id)}/room?strokes=${knownStrokes}&board=${knownBoard}`),
  roomCode: (id: string, code: string, language: string) =>
    putJson<Ok>(`/api/meetings/${enc(id)}/room/code`, { code, language }),
  roomCollab: (id: string, on: boolean) => postJson<Ok>(`/api/meetings/${enc(id)}/room/collab`, { on }),
  roomStroke: (id: string, color: string, points: string) =>
    postJson<Ok>(`/api/meetings/${enc(id)}/room/strokes`, { color, points }),
  roomClearBoard: (id: string) => del(`/api/meetings/${enc(id)}/room/strokes`),
  roomHand: (id: string) => postJson<Ok>(`/api/meetings/${enc(id)}/room/hand`, {}),
  attendance: (id: string) => request<AttendanceSheet>(`/api/meetings/${enc(id)}/attendance`),
  markAttendance: (id: string, userId: string, status: string) =>
    putJson<Ok>(`/api/meetings/${enc(id)}/attendance`, { userId, status }),
  recordings: () => request<Recording[]>("/api/recordings"),
  recording: (id: string) => request<Recording>(`/api/recordings/${enc(id)}`),

  // --- cuoc thi (Module 21, 22) ---
  contests: () => request<Contest[]>("/api/contests"),
  /** minute bo trong = bang xep hang cuoi cung; co minute = bang tai phut do (xem lai). */
  contest: (id: string, minute?: number) =>
    request<Contest>(`/api/contests/${enc(id)}${minute === undefined ? "" : `?minute=${minute}`}`),
  contestEvents: (id: string) => request<ContestEvent[]>(`/api/contests/${enc(id)}/events`),
  createContest: (body: { title: string; description: string; startAt: string; durationMin: number; problemIds: string[] }) =>
    postJson<Contest>("/api/contests", body),
  deleteContest: (id: string) => del(`/api/contests/${enc(id)}`),
  /** Them / bot mot bai trong ky thi do chinh minh tao. */
  addContestProblem: (id: string, problemId: string) =>
    postJson<Contest>(`/api/contests/${enc(id)}/problems`, { problemId }),
  removeContestProblem: (id: string, problemId: string) =>
    del<Contest>(`/api/contests/${enc(id)}/problems/${enc(problemId)}`),

  // --- phan tich, thanh tich, bao cao (Module 23-26, 33, 38) ---
  myProfile: () => request<Profile>("/api/analytics/me"),
  profileOf: (username: string) => request<Profile>(`/api/analytics/users/${enc(username)}`),
  dashboard: () => request<Dashboard>("/api/dashboard"),
  pointBoard: () => request<PointRow[]>("/api/gamification/leaderboard"),

  // --- tro ly AI mo phong (Module 27, 28) ---
  hint: (body: { problemId: string; code: string; question: string }) =>
    postJson<AssistantReply>("/api/assistant/hint", body),
  classSummary: (classroomId: string) => request<AssistantReply>(`/api/assistant/classrooms/${enc(classroomId)}`),
  studentComment: (username: string) => request<AssistantReply>(`/api/assistant/students/${enc(username)}`),

  // --- thong bao, chat, dien dan (Module 29, 30, 31) ---
  notifications: () => request<NotificationList>("/api/notifications"),
  markNotificationsRead: () => postJson<Ok>("/api/notifications/read", {}),
  announce: (body: { classroomId: string; title: string; body: string }) =>
    postJson<{ sent: number }>("/api/announcements", body),
  chat: (channel: string, after: number) => request<ChatMessage[]>(`/api/chat/${enc(channel)}?after=${after}`),
  sendChat: (channel: string, text: string) => postJson<Ok>(`/api/chat/${enc(channel)}`, { text }),
  forum: () => request<ForumPost[]>("/api/forum"),
  forumPost: (id: string) => request<ForumPost>(`/api/forum/${enc(id)}`),
  createPost: (body: { title: string; body: string; problemId: string }) => postJson<ForumPost>("/api/forum", body),
  replyPost: (id: string, body: string) => postJson<ForumPost>(`/api/forum/${enc(id)}/replies`, { body }),
  deletePost: (id: string) => del(`/api/forum/${enc(id)}`),

  // --- thanh toan mo phong, he thong, nhat ky (Module 34, 39, 40) ---
  billing: () => request<Billing>("/api/billing"),
  pay: (plan: string) => postJson<Ok>("/api/billing/pay", { plan }),
  allPayments: () => request<{ revenue: number; payments: Payment[] }>("/api/admin/payments"),
  settings: () => request<Settings>("/api/settings"),
  updateSettings: (body: Partial<Settings>) => putJson<Settings>("/api/admin/settings", body),
  systemInfo: () => request<{ label: string; value: string }[]>("/api/admin/system"),
  audit: (q: string) => request<AuditRow[]>(`/api/admin/audit?q=${enc(q)}`),

  // --- IDE, stress test, bo test (Module 10, 12, 7) ---
  run: (body: { language: string; code: string; input: string }) => postJson<RunResult>("/api/run", body),
  stress: (body: { language: string; solution: string; brute: string; generator: string; rounds: number }) =>
    postJson<StressResult>("/api/stress", body),
  testcases: (problemId: string) => request<TestcaseItem[]>(`/api/admin/problems/${enc(problemId)}/tests`),
  addTestcase: (problemId: string, body: { name: string; input: string; output: string; sample: boolean }) =>
    postJson<TestcaseItem[]>(`/api/admin/problems/${enc(problemId)}/tests`, body),
  deleteTestcase: (problemId: string, name: string) =>
    del<TestcaseItem[]>(`/api/admin/problems/${enc(problemId)}/tests/${enc(name)}`),
};
