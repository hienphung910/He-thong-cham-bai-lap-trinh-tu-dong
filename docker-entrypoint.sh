#!/bin/sh
# Khoi dong CodeHub trong container.
#
# 1. Chep de bai goc vao /app/data. Neu /app/data la mot volume (Railway Volume)
#    thi lan dau no trong; "cp -n" khong ghi de nen bai tao tren web va cac chinh
#    sua bo test tu nhung lan chay truoc duoc giu nguyen.
# 2. Chay may chu bang tai khoan thuong "codehub" thay vi root, de code thi sinh
#    nop len khong co quyen root trong container.
# 3. Nghe tren cong $PORT do nen tang cap (Railway), mac dinh 8080.
#
# Tham so them vao lenh "docker run" duoc chuyen cho chuong trinh, vi du --no-db.
set -e

mkdir -p /app/data
cp -rn /app/data-seed/. /app/data/
chown -R codehub:codehub /app/data

export HOME=/home/codehub
cd /app
# JAVA_OPTS co y khong dat trong ngoac kep de tach duoc thanh nhieu tham so.
exec setpriv --reuid=codehub --regid=codehub --init-groups \
    java $JAVA_OPTS -Dfile.encoding=UTF-8 -cp "out:lib/*" \
    com.ptit.oj.Main --serve="${PORT:-8080}" "$@"
