# CodeHub

CodeHub là hệ thống dạy và luyện lập trình có chấm bài tự động. Học sinh làm bài ngay trên trình duyệt và xem kết quả từng test sau khi nộp. Giảng viên quản lý lớp, giao bài, tổ chức kỳ thi và theo dõi tiến độ của từng học sinh.

![Java](https://img.shields.io/badge/Java-11+-ED8B00?logo=openjdk&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8-4479A1?logo=mysql&logoColor=white)

## Tính năng

### Chấm bài tự động

Bài nộp được biên dịch, chạy trên bộ test với giới hạn thời gian và bộ nhớ, rồi so với đáp án. Mỗi test nhận một trong sáu kết quả AC, WA, TLE, MLE, RE, CE, và điểm được tính theo số test đúng. Hệ thống chấm Java, C++, Python và JavaScript. Test ẩn không bao giờ được gửi xuống trình duyệt, và thông báo lỗi ở test ẩn chỉ ghi loại lỗi nên học sinh không dò ra được dữ liệu test.

### Kho bài và lộ trình học

CodeHub có sẵn 600 bài, chia cho hai lớp học:

- Lớp Nhập môn: 10 chủ đề từ nhập/xuất đến mảng, xâu và sắp xếp, gồm 300 bài từ `K001` đến `K300`.
- Lớp Lộ trình nâng cao: 26 chặng theo [lộ trình của VNOI Wiki](https://wiki.vnoi.info/roadmap/general-roadmap), từ đệ quy, đồ thị đến luồng và quy hoạch động nâng cao, gồm 300 bài từ `V001` đến `V300`.

Bài trong mỗi chặng xếp từ dễ đến khó. Học sinh xem được tiến độ từng chặng, lọc bài theo độ khó và tìm bài theo mã hoặc tên.

### Lớp học

Giảng viên tạo lớp và gửi mã tham gia cho học sinh. Học sinh nhập mã để xin vào lớp, giảng viên duyệt thì mới thành thành viên. Giảng viên cũng có thể mời học sinh theo tên đăng nhập, học sinh đồng ý là vào lớp.

Mỗi lớp có bài luyện tập do giảng viên chọn, kênh chat riêng và bài tập giao kèm hạn nộp. Với mỗi bài tập giao, giảng viên xem được tiến độ của cả lớp. Bài AC sau hạn được ghi là nộp muộn và không tính vào tiến độ. Giảng viên bấm vào tên một học sinh để xem phân tích học tập của em đó.

Ở mục Lịch học, giảng viên lên lịch buổi học và dán link phòng họp (Google Meet, Zoom...). Từ 10 phút trước giờ học, học sinh thấy nút Tham gia phòng học.

### Kỳ thi

Mỗi kỳ thi dành cho một lớp và chỉ học sinh lớp đó vào thi được. Khi học sinh bấm Vào thi, màn hình chỉ còn danh sách bài, ô nộp bài, bảng xếp hạng và đồng hồ đếm ngược. Máy chủ khóa chat, bình luận và các bài ngoài kỳ thi cho tới khi hết giờ hoặc học sinh nộp bài sớm. Bảng xếp hạng tính theo số bài giải được; bằng bài thì ai giải xong bài cuối sớm hơn đứng trước.

Người tạo kỳ thi có thể bật giám sát. Khi bật, hệ thống ghi lại lúc thí sinh rời tab, chuyển cửa sổ, sao chép hoặc dán. Người tạo kỳ thi xem nhật ký này ngay trên trang kỳ thi và nhận thông báo khi một thí sinh vi phạm.

### Kho bài riêng của giảng viên

Giảng viên tự soạn bài, đặt giới hạn thời gian và bộ nhớ, tải bộ test lên theo từng file hoặc cả thư mục. Bài trong kho riêng chỉ hiện với học sinh khi giảng viên dùng nó cho lớp của mình: làm bài luyện tập, bài tập giao hoặc đề thi.

### Công cụ

IDE trực tuyến chạy thử mã nguồn với input tự nhập, không tính là nộp bài. Mục Công cụ CP sinh test ngẫu nhiên (mảng, hoán vị, cây, đồ thị, xâu), so sánh hai output và ước lượng độ phức tạp theo giới hạn của đề.

### Theo dõi kết quả

Trang Phân tích học tập hiện số lượt nộp, tỉ lệ AC, số lượt nộp 14 ngày gần đây và điểm kỹ năng theo từng chủ đề. Ngoài ra có bảng xếp hạng chung, lịch sử nộp bài và chuỗi ngày luyện tập trên thanh tiêu đề. Giảng viên có trang Tổng quan với số liệu chung của hệ thống.

### Bình luận, chat và thông báo

Mỗi bài có phần bình luận ở cuối trang. Bình luận và tin nhắn chat hiện ngay sau khi gửi nhưng phải qua bộ kiểm duyệt. Bộ kiểm duyệt chặn từ ngữ thô tục, kể cả khi viết lách (không dấu, teencode, chèn ký tự, số thay chữ). Học sinh không được đăng nguyên lời giải (chỉ được trích tối đa 3 dòng mã để hỏi) và chỉ được gửi link tới các trang tài liệu. Tin nhắn spam bị chặn. Ai vi phạm 3 lần trong 10 phút thì bị khóa chat và bình luận một thời gian. Thông báo tự gửi tới học sinh khi có bài tập, buổi học hoặc kỳ thi mới.

## Vai trò

| Vai trò | Cách có tài khoản | Làm được gì |
|---|---|---|
| Học sinh | Tự đăng ký, dùng được ngay | Làm bài, vào lớp, thi, xem phân tích của mình |
| Giảng viên | Tự đăng ký kèm file minh chứng, chờ quản trị viên duyệt | Quản lý lớp, giao bài, tạo kỳ thi, soạn bài trong kho riêng, xem bài nộp của học sinh lớp mình |
| Quản trị viên | Tài khoản `admin` tạo sẵn; thêm người khác bằng cách đổi vai trò một tài khoản đã có | Quản lý kho bài chung và bộ test, duyệt và khóa tài khoản, quản lý tổ chức, cấu hình hệ thống |

## Bảo mật

Mật khẩu được băm bằng PBKDF2-HMAC-SHA256 với salt riêng cho từng tài khoản. Phiên đăng nhập hết hạn sau 8 giờ, và đổi mật khẩu sẽ đăng xuất các phiên khác. Đăng nhập sai nhiều lần thì bị tạm chặn. Khi cài đặt có thể bật thêm CAPTCHA Cloudflare Turnstile cho form đăng nhập và đăng ký, cùng chức năng quên mật khẩu qua email, bằng các biến trong file `.env`. Image Docker có sẵn một tài khoản hệ thống riêng để chạy bài nộp; khi máy chủ báo `Cô lập bài nộp: Bật`, bài nộp không đọc được thư mục chứa test ẩn. Mỗi ngôn ngữ còn bị giới hạn riêng về số tiến trình, số luồng và bộ nhớ. Bài Java chạy dưới Security Manager, bài JavaScript chạy ở chế độ phân quyền của Node, nên không gọi được chương trình khác hay đọc file ngoài thư mục bài.

## Công nghệ

| Thành phần | Công nghệ |
|---|---|
| Máy chủ | Java 11, máy chủ HTTP của JDK, JDBC |
| Cơ sở dữ liệu | MySQL 8 |
| Giao diện | React 19, TypeScript 5, Vite 7, Tailwind CSS 4 |

## Cài đặt

Cần JDK 11 trở lên, Node.js 20.19 trở lên và MySQL 8. Muốn chấm C++, Python, JavaScript thì cài thêm g++, Python 3 và Node.js trên máy chủ.

1. Tải mã nguồn:

   ```bash
   git clone https://github.com/hienphung910/Online-Judge.git
   cd Online-Judge
   ```

2. Sửa mật khẩu `THAY_MAT_KHAU_NAY` trong `database/mysql/01_create_database.sql` rồi chạy file này để tạo CSDL `online_judge` và tài khoản `oj_app`:

   ```bash
   mysql -u root -p < database/mysql/01_create_database.sql
   ```

3. Chép file cấu hình mẫu thành `.env`. Trong `.env`, đặt `OJ_DB_PASSWORD` trùng mật khẩu vừa đặt ở bước 2, đặt mật khẩu cho tài khoản `admin` ở `OJ_ADMIN_PASSWORD`, và xóa giá trị của `OJ_SEED_PASSWORD` để hệ thống không tạo tài khoản mẫu:

   ```bash
   cp .env.example .env    # PowerShell: Copy-Item .env.example .env
   ```

4. Tải MySQL Connector/J 8.4.0 vào thư mục `lib/` (hoặc chép file `mysql-connector-j-8.4.0.jar` từ Maven Central vào đó):

   ```bash
   mvn -q dependency:copy-dependencies -DoutputDirectory=lib -DincludeScope=runtime
   ```

5. Biên dịch máy chủ và build giao diện:

   ```bash
   javac -encoding UTF-8 -d out $(find src -name "*.java")
   cd frontend
   npm install
   npm run build
   cd ..
   ```

   Trên PowerShell, lệnh biên dịch là:

   ```powershell
   javac -encoding UTF-8 -d out (Get-ChildItem -Recurse -Filter *.java src).FullName
   ```

6. Khởi động từ thư mục gốc của project (Linux, macOS dùng `:` thay cho `;`):

   ```bash
   java "-Dfile.encoding=UTF-8" -cp "out;lib/*" com.ptit.oj.Main --serve
   ```

Mở http://localhost:8080 và đăng nhập bằng tài khoản `admin`. Bảng trong CSDL được tạo tự động ở lần chạy đầu. Muốn đổi cổng thì dùng `--serve=9000`. Máy chủ mặc định chỉ nhận kết nối từ chính máy đó; muốn mở cho máy khác thì đặt `OJ_BIND_ADDRESS=0.0.0.0` trong `.env`. Các tùy chọn khác (CAPTCHA, email, từ cấm) có ghi chú sẵn trong `.env.example`.

Project có kèm `Dockerfile` đóng gói máy chủ, giao diện và trình biên dịch các ngôn ngữ vào một image. MySQL đặt ngoài image.

## Nhóm phát triển

Bài tập lớn môn Lập trình hướng đối tượng, Học viện Công nghệ Bưu chính Viễn thông (PTIT).

Thành viên: hienphung910, longnd1303, tm079, tienkhoi235
