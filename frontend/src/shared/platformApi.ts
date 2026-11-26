import { postJson, putJson, request } from "./api";
import type {
  Assignment, AssignmentProgress, BasicUser, ChatMessage, Classroom, Contest, ContestEvent, Dashboard, Meeting, NotificationList, Organization, ProctorEventType, ProctorLog, ProblemComment, Profile, RunResult, Settings, TestcaseItem,
} from "./platformTypes";

/** Goi REST API cua cac module mo rong (cac lop ...Api.java). Moi ham ung voi mot endpoint. */

type Ok = { ok: boolean };
const enc = encodeURIComponent;
const del = <T = Ok>(path: string) => request<T>(path, { method: "DELETE" });

export const papi = {
  // --- tai khoan (Module 1, 35) ---
  users: () => request<BasicUser[]>("/api/users"),
  approveUser: (id: string) => postJson<BasicUser>(`/api/admin/users/${enc(id)}/approve`, {}),
  rejectUser: (id: string) => postJson<Ok>(`/api/admin/users/${enc(id)}/reject`, {}),
  /** Khoa tai khoan dang dung (mo lai bang approveUser), doi vai tro, xoa han. */
  lockUser: (id: string) => postJson<BasicUser>(`/api/admin/users/${enc(id)}/lock`, {}),
  setUserRole: (id: string, role: string) => putJson<BasicUser>(`/api/admin/users/${enc(id)}/role`, { role }),
  deleteUser: (id: string) => del(`/api/admin/users/${enc(id)}`),
  /** File minh chung giang vien gui kem khi dang ky; data la noi dung file dang base64. */
  userEvidence: (id: string) => request<{ contentType: string; data: string }>(`/api/admin/users/${enc(id)}/evidence`),
  changePassword: (oldPassword: string, newPassword: string) =>
    postJson<Ok>("/api/auth/password", { oldPassword, newPassword }),
  /** Dang xuat moi thiet bi khac, giu phien dang dung. */
  logoutOthers: () => postJson<Ok>("/api/auth/logout-others", {}),
  /** Doi email khoi phuc cua chinh minh; phai nhap mat khau hien tai. */
  /** pendingEmail khac "": email moi con cho xac minh qua thu, email cu (neu co) van dang dung. */
  changeEmail: (password: string, email: string) =>
    putJson<{ email: string; pendingEmail: string }>("/api/auth/email", { password, email }),
  resendEmailVerification: () => postJson<Ok>("/api/auth/email/resend", {}),

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
  /** Loi moi vao lop dang cho minh dong y, va viec dong y / tu choi. */
  classroomInvites: () => request<{ id: string; name: string; teacherName: string }[]>("/api/classroom-invites"),
  acceptInvite: (id: string) => postJson<Ok>(`/api/classrooms/${enc(id)}/accept`, {}),
  declineInvite: (id: string) => postJson<Ok>(`/api/classrooms/${enc(id)}/decline`, {}),
  /** Xin vao lop bang MA THAM GIA. pending = true: da gui yeu cau, cho giang vien duyet; false: da vao lop (duoc moi san). */
  joinClassroom: (code: string) => postJson<{ id: string; name: string; pending: boolean }>(`/api/classrooms/${enc(code)}/join`, {}),
  /** Cac yeu cau vao lop cua minh dang cho duyet. */
  classroomRequests: () => request<{ id: string; name: string; teacherName: string }[]>("/api/classroom-requests"),
  cancelJoinRequest: (id: string) => del(`/api/classrooms/${enc(id)}/request`),
  approveJoinRequest: (id: string, userId: string) => postJson<Ok>(`/api/classrooms/${enc(id)}/requests/${enc(userId)}/approve`, {}),
  rejectJoinRequest: (id: string, userId: string) => postJson<Ok>(`/api/classrooms/${enc(id)}/requests/${enc(userId)}/reject`, {}),
  addStudent: (id: string, username: string) => postJson<Ok>(`/api/classrooms/${enc(id)}/students`, { username }),
  removeStudent: (id: string, userId: string) => del(`/api/classrooms/${enc(id)}/students/${enc(userId)}`),
  addPracticeProblems: (id: string, problemIds: string[]) =>
    postJson<Ok>(`/api/classrooms/${enc(id)}/problems`, { problemIds }),
  removePracticeProblem: (id: string, problemId: string) =>
    del(`/api/classrooms/${enc(id)}/problems/${enc(problemId)}`),

  // --- khoa hoc, tai lieu, bai tap giao (Module 4, 36, 13) ---

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
  deleteMeeting: (id: string) => del(`/api/meetings/${enc(id)}`),

  // --- cuoc thi (Module 21, 22) ---
  contests: () => request<Contest[]>("/api/contests"),
  /** minute bo trong = bang xep hang cuoi cung; co minute = bang tai phut do (xem lai). */
  contest: (id: string, minute?: number) =>
    request<Contest>(`/api/contests/${enc(id)}${minute === undefined ? "" : `?minute=${minute}`}`),
  contestEvents: (id: string) => request<ContestEvent[]>(`/api/contests/${enc(id)}/events`),
  /** Trinh duyet thi sinh bao mot su kien giam sat. keepalive de gui duoc ca luc trang dang dong. */
  proctor: (id: string, body: { type: ProctorEventType; detail: string; problemId: string }) =>
    request<Ok>(`/api/contests/${enc(id)}/proctor`, { method: "POST", body: JSON.stringify(body), keepalive: true }),
  proctorLog: (id: string) => request<ProctorLog>(`/api/contests/${enc(id)}/proctor`),
  createContest: (body: { title: string; description: string; startAt: string; durationMin: number; problemIds: string[]; proctored: boolean; classroomId: string }) =>
    postJson<Contest>("/api/contests", body),
  /** Hoc sinh vao thi / nop bai som; nguoi tao ky thi bat, tat giam sat. */
  joinContest: (id: string) => postJson<Contest>(`/api/contests/${enc(id)}/join`, {}),
  finishContest: (id: string) => postJson<Contest>(`/api/contests/${enc(id)}/finish`, {}),
  /** Doi gio thi (truoc khi thi) hoac gia han thoi luong (khi dang thi). */
  rescheduleContest: (id: string, startAt: string, durationMin: number) =>
    putJson<Contest>(`/api/contests/${enc(id)}/schedule`, { startAt, durationMin }),
  setProctoring: (id: string, on: boolean) => putJson<Contest>(`/api/contests/${enc(id)}/proctoring`, { on }),
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

  // --- thong bao, chat, dien dan (Module 29, 30, 31) ---
  notifications: () => request<NotificationList>("/api/notifications"),
  markNotificationsRead: () => postJson<Ok>("/api/notifications/read", {}),
  chat: (channel: string, after: number) => request<ChatMessage[]>(`/api/chat/${enc(channel)}?after=${after}`),
  sendChat: (channel: string, text: string) => postJson<Ok>(`/api/chat/${enc(channel)}`, { text }),
  // --- binh luan duoi bai tap (may chu loc tu bay va ma nguon loi giai luc gui, khong qua nguoi duyet) ---
  comments: (problemId: string) => request<ProblemComment[]>(`/api/problems/${enc(problemId)}/comments`),
  addComment: (problemId: string, text: string) =>
    postJson<ProblemComment>(`/api/problems/${enc(problemId)}/comments`, { text }),
  deleteComment: (id: string) => del(`/api/comments/${enc(id)}`),
  /** Cac binh luan minh xoa duoc: quan tri vien thay het, giang vien thay binh luan duoi bai cua minh. */
  allComments: () => request<ProblemComment[]>("/api/admin/comments"),

  // --- thanh toan mo phong, he thong, nhat ky (Module 34, 39, 40) ---
  settings: () => request<Settings>("/api/settings"),
  updateSettings: (body: Partial<Settings>) => putJson<Settings>("/api/admin/settings", body),
  systemInfo: () => request<{ label: string; value: string }[]>("/api/admin/system"),

  // --- IDE, stress test, bo test (Module 10, 12, 7) ---
  run: (body: { language: string; code: string; input: string }) => postJson<RunResult>("/api/run", body),
  testcases: (problemId: string) => request<TestcaseItem[]>(`/api/admin/problems/${enc(problemId)}/tests`),
  addTestcase: (problemId: string, body: { name: string; input: string; output: string; sample: boolean }) =>
    postJson<TestcaseItem[]>(`/api/admin/problems/${enc(problemId)}/tests`, body),
  deleteTestcase: (problemId: string, name: string) =>
    del<TestcaseItem[]>(`/api/admin/problems/${enc(problemId)}/tests/${enc(name)}`),
};
