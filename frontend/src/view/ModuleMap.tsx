import { Btn, Page, Table, Tag } from "../shared/kit";
import { useNav, type PageKey } from "../shared/nav";

/** [so module, ten, trang chua no, muc do: "real" lam that / "sim" mo phong, ghi chu] */
const MODULES: [number, string, PageKey, "real" | "sim", string][] = [
  [1, "User & Account Management", "Account", "real", "Đăng ký, đăng nhập, 3 vai trò, đổi mật khẩu; quản trị tạo tài khoản"],
  [2, "Organization Management", "Organizations", "real", "Thêm, xoá tổ chức; lớp gắn vào tổ chức"],
  [3, "Classroom Management", "Classrooms", "real", "Tạo lớp, tham gia bằng mã, thêm/xoá học sinh"],
  [4, "Course Management", "Courses", "real", "Khóa học và giáo trình gồm các bài giảng"],
  [5, "Curriculum & Learning Path", "Problems", "real", "Hai lộ trình (Nhập môn, VNOI) theo từng chặng"],
  [6, "Problem Management", "Admin", "real", "Tạo bài, sửa chủ đề và độ khó"],
  [7, "Testcase Management", "Admin", "real", "Xem, thêm, xoá test của từng bài (nút Test)"],
  [8, "Judge Engine", "Problems", "real", "Biên dịch, chạy, so sánh output, 6 verdict"],
  [9, "Submission Management", "Submissions", "real", "Lịch sử nộp, chi tiết từng test, mã nguồn"],
  [10, "Online IDE", "Playground", "real", "Chạy thử mã nguồn với input tự nhập"],
  [11, "Advanced CP Tools", "CpTools", "real", "Sinh test, so sánh output, ước lượng độ phức tạp"],
  [12, "Stress Testing", "StressTest", "real", "So lời giải với vét cạn trên test ngẫu nhiên"],
  [13, "Assignment Management", "Assignments", "real", "Giao bài cho lớp, theo dõi tiến độ"],
  [14, "Virtual Classroom", "Meetings", "sim", "Phòng học đồng bộ bằng polling 1,5 giây"],
  [15, "Meeting Management", "Meetings", "real", "Lên lịch, bắt đầu, kết thúc buổi học"],
  [16, "Video & Audio Conference", "Meetings", "sim", "Chỉ bật camera/micro của chính mình, không truyền đi"],
  [17, "Live Coding", "Meetings", "sim", "Chủ phòng gõ mã, cả lớp thấy sau mỗi lần polling"],
  [18, "Collaborative Coding", "Meetings", "sim", "Cả lớp cùng sửa một file, người gửi sau ghi đè"],
  [19, "Whiteboard & Interactive Tools", "Meetings", "sim", "Bảng trắng dùng chung, giơ tay"],
  [20, "Attendance Management", "Meetings", "real", "Điểm danh tự động khi vào phòng, sửa tay được"],
  [21, "Contest Management", "Contests", "real", "Tạo kỳ thi, bảng xếp hạng kiểu ICPC"],
  [22, "Contest Replay", "Contests", "real", "Kéo thanh thời gian để xem lại bảng xếp hạng"],
  [23, "Student Analytics", "Analytics", "real", "Lượt nộp, tỉ lệ AC, hoạt động 14 ngày"],
  [24, "Skill Assessment", "Analytics", "real", "Điểm kỹ năng và mức theo từng chủ đề"],
  [25, "Weakness Detection", "Analytics", "real", "Chủ đề yếu và kiểu lỗi hay gặp"],
  [26, "Learning Recommendation", "Analytics", "real", "Gợi ý bài theo điểm yếu và lộ trình"],
  [27, "AI Teaching Assistant", "TeachingAssistant", "sim", "Nhận xét sinh theo luật, không gọi mô hình AI"],
  [28, "AI Coding Assistant", "Assistant", "sim", "Gợi ý sinh theo luật, không gọi mô hình AI"],
  [29, "Notification Management", "Notifications", "real", "Chuông thông báo; tự sinh khi có bài tập, buổi học mới"],
  [30, "Communication & Chat", "Chat", "real", "Kênh chung, kênh lớp, kênh phòng học (polling)"],
  [31, "Discussion Forum", "Forum", "real", "Đăng chủ đề, trả lời"],
  [32, "Parent Management", "Parents", "real", "Danh bạ phụ huynh, báo cáo học tập"],
  [33, "Reporting & Dashboard", "Dashboard", "real", "Số liệu toàn hệ thống"],
  [34, "Payment & Subscription", "Billing", "sim", "Cổng thanh toán giả, không có tiền thật"],
  [35, "Teacher Management", "Accounts", "real", "Tạo giảng viên, gán trợ giảng cho lớp"],
  [36, "Document Management", "Courses", "real", "Tài liệu dạng văn bản hoặc đường link"],
  [37, "Recording Management", "Meetings", "sim", "Ghi lại nội dung buổi học, không ghi hình"],
  [38, "Gamification", "Achievements", "real", "Điểm thưởng, hạng, huy hiệu"],
  [39, "System Administration", "System", "real", "Thông tin máy chủ, cấu hình"],
  [40, "Security & Audit Management", "Audit", "real", "Nhật ký mọi thao tác ghi và đăng nhập"],
];

/** Trang chi dan: 40 module trong de bai nam o dau tren giao dien, cai nao la mo phong. */
export default function ModuleMap({ allowed }: { allowed: (page: PageKey) => boolean }) {
  const { go } = useNav();
  return (
    <Page title="Sơ đồ 40 module" subtitle="Mỗi module trong đề bài ứng với trang nào. Dữ liệu của các module mới lưu trong RAM của máy chủ.">
      <Table head={["#", "Module", "Mức độ", "Làm được gì", ""]}
        rows={MODULES.map(([no, name, page, level, note]) => [
          no, <b>{name}</b>,
          <Tag color={level === "real" ? "green" : "orange"}>{level === "real" ? "Chạy thật" : "Mô phỏng"}</Tag>,
          note,
          allowed(page)
            ? <Btn small kind="ghost" onClick={() => go(page)}>Mở trang</Btn>
            : <span style={{ fontSize: 12, color: "#9E9E9E" }}>cần quyền cao hơn</span>,
        ])} />
    </Page>
  );
}
