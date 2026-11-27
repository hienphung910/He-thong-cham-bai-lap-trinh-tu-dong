# =====================================================================
#  CodeHub - image de chay tren Railway (hoac bat ky noi nao chay Docker)
# =====================================================================
#  Image gom: may chu Java, giao dien da build, va toolchain de cham bai
#  (javac, g++, python3, node). MySQL nam ngoai (vi du Aiven), cau hinh qua
#  bien moi truong OJ_DB_URL / OJ_DB_USER / OJ_DB_PASSWORD.
#
#  Chay thu tren may:
#      docker build -t codehub .
#      docker run --rm -p 8080:8080 codehub --no-db
# =====================================================================

# Image nen duoc GHIM theo digest: tag co the bi tro sang noi dung khac, digest thi khong.
# Doi lai khoang moi thang mot lan de nhan ban va bao mat cua he dieu hanh, lay digest moi bang:
#     docker buildx imagetools inspect node:22-bookworm-slim
#     docker buildx imagetools inspect eclipse-temurin:17-jdk-jammy

# ---------- 1. Build giao dien web ----------
FROM node:22-bookworm-slim@sha256:be979e5523c76fa4d5ca4ebd3099078f220aa47ec39c48ad5b5df9314debd6eb AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---------- 2. Bien dich backend va tai MySQL Connector/J ----------
FROM eclipse-temurin:17-jdk-jammy@sha256:96ac8197b2cbc2bb0f9fbcbf91dbb0015f43b3c76a1cb351892da12e7bb91bed AS backend
WORKDIR /build
COPY src ./src
RUN javac -encoding UTF-8 -d out $(find src -name "*.java")
ADD https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar lib/
# Kiem tra file tai ve dung la ban da biet (SHA-256 cua mysql-connector-j 8.4.0 tren Maven Central):
# sai mot byte la build dung lai. ADD tu URL dat quyen 600; mo quyen doc de tai khoan khong phai root dung duoc.
RUN echo "d77962877d010777cff997015da90ee689f0f4bb76848340e1488f2b83332af5  lib/mysql-connector-j-8.4.0.jar" | sha256sum -c - \
 && chmod 644 lib/*.jar

# ---------- 3. Image chay ----------
# Dung ban JDK (khong phai JRE) vi bo cham can javac de bien dich bai nop Java.
FROM eclipse-temurin:17-jdk-jammy@sha256:96ac8197b2cbc2bb0f9fbcbf91dbb0015f43b3c76a1cb351892da12e7bb91bed

# Hai tai khoan he thong:
#   codehub  - chay may chu, so huu /app/data (de bai, test an, file minh chung).
#   ojrunner, ojrunner2..4 - CHI de chay trinh bien dich va chuong trinh thi sinh nop len (xem Sandbox.java).
#              Moi tai khoan la mot "o cham": 4 bai cham song song, khong dung vao tien trinh cua nhau.
# May chu goi "sudo -u ojrunnerN ..." cho tung lan chay; file sudoers duoi day chi cho phep dung
# mot huong do (codehub -> cac tai khoan ojrunner), khong cho len root.
RUN apt-get update \
 && apt-get install -y --no-install-recommends g++ python3 sudo \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --create-home --home-dir /home/codehub codehub \
 && for u in ojrunner ojrunner2 ojrunner3 ojrunner4; do \
      useradd --system --create-home --home-dir /home/$u --shell /usr/sbin/nologin $u; \
    done \
 && chmod 750 /home/codehub \
 && printf '%s\n' \
      'Defaults:codehub !requiretty, !syslog, !use_pty, env_reset' \
      'Defaults:codehub secure_path="/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"' \
      'Defaults:codehub env_keep += "LANG LC_ALL LANGUAGE"' \
      'codehub ALL=(ojrunner,ojrunner2,ojrunner3,ojrunner4) NOPASSWD: ALL' \
      > /etc/sudoers.d/codehub \
 && chmod 440 /etc/sudoers.d/codehub \
 && visudo -cf /etc/sudoers.d/codehub

# Node de cham bai JavaScript: lay luon file chay tu image build giao dien.
COPY --from=web /usr/local/bin/node /usr/local/bin/node

WORKDIR /app
COPY --from=backend /build/out ./out
COPY --from=backend /build/lib ./lib
COPY --from=web /web/dist ./frontend/dist
# De bai goc nam o data-seed; luc khoi dong moi chep sang data/ (xem docker-entrypoint.sh).
COPY data ./data-seed
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
# Bo ky tu xuong dong kieu Windows neu file bi checkout thanh CRLF.
# data-seed chua test an cua moi bai: chi root (script khoi dong) doc duoc, tai khoan chay bai nop thi khong.
RUN sed -i 's/\r$//' /usr/local/bin/docker-entrypoint.sh \
 && chmod +x /usr/local/bin/docker-entrypoint.sh \
 && chmod 700 /app/data-seed

# Trong container phai nghe tren moi dia chi thi nen tang moi chuyen request vao duoc.
# OJ_SANDBOX_USER(S): chay bai nop bang cac tai khoan ojrunner (may chu tu kiem tra luc khoi dong va in ket qua);
#                     so bai cham cung luc = min(so tai khoan, so nhan CPU), dat OJ_JUDGE_WORKERS de doi.
# OJ_TRUST_PROXY : image nay chay sau proxy cua nen tang (Railway...), lay dia chi nguoi dung tu X-Forwarded-For.
#                  Dat false neu container nhan ket noi truc tiep tu Internet.
# OJ_ALLOW_REGISTER khong dat o day: mac dinh mo khi viec co lap bai nop bat duoc, dong khi khong bat duoc.
ENV OJ_BIND_ADDRESS=0.0.0.0 \
    OJ_SANDBOX_USER=ojrunner \
    OJ_SANDBOX_USERS=ojrunner,ojrunner2,ojrunner3,ojrunner4 \
    OJ_TRUST_PROXY=true \
    TZ=Asia/Ho_Chi_Minh \
    JAVA_OPTS="-Xmx256m"

EXPOSE 8080
ENTRYPOINT ["docker-entrypoint.sh"]
