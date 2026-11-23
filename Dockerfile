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

# ---------- 1. Build giao dien web ----------
FROM node:22-bookworm-slim AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---------- 2. Bien dich backend va tai MySQL Connector/J ----------
FROM eclipse-temurin:17-jdk-jammy AS backend
WORKDIR /build
COPY src ./src
RUN javac -encoding UTF-8 -d out $(find src -name "*.java")
ADD https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar lib/
# ADD tu URL dat quyen 600; mo quyen doc de tai khoan khong phai root dung duoc.
RUN chmod 644 lib/*.jar

# ---------- 3. Image chay ----------
# Dung ban JDK (khong phai JRE) vi bo cham can javac de bien dich bai nop Java.
FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update \
 && apt-get install -y --no-install-recommends g++ python3 \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --create-home --home-dir /home/codehub codehub

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
RUN sed -i 's/\r$//' /usr/local/bin/docker-entrypoint.sh \
 && chmod +x /usr/local/bin/docker-entrypoint.sh

# Trong container phai nghe tren moi dia chi thi nen tang moi chuyen request vao duoc.
ENV OJ_BIND_ADDRESS=0.0.0.0 \
    TZ=Asia/Ho_Chi_Minh \
    JAVA_OPTS="-Xmx256m"

EXPOSE 8080
ENTRYPOINT ["docker-entrypoint.sh"]
