/**
 * Chuan bi file minh chung giang vien chon o form dang ky truoc khi gui len may chu.
 *
 * May chu chi nhan PNG / JPG / WEBP / PDF, toi da 1 MB. Anh chup bang dien thoai thuong nang vai MB
 * nen anh duoc thu nho ngay trong trinh duyet (canh dai toi da 1600 px, luu lai dang JPG) truoc khi
 * gui; PDF thi gui nguyen ven.
 */

const MAX_BYTES = 1024 * 1024;
const MAX_SIDE = 1600;
const IMAGE_TYPES = ["image/png", "image/jpeg", "image/webp"];

/** Cac loai file o chon file cho phep. */
export const EVIDENCE_ACCEPT = [...IMAGE_TYPES, "application/pdf"].join(",");

/** Tra ve noi dung file dang base64 (khong kem tien to "data:..."). Nem Error kem ly do neu file khong dung duoc. */
export async function readEvidence(file: File): Promise<string> {
  let blob: Blob = file;
  if (IMAGE_TYPES.includes(file.type)) {
    // Thu nho khong duoc (trinh duyet cu, anh hong) thi gui anh goc, may chu se kiem tra lai.
    blob = await shrinkImage(file).catch(() => file);
  } else if (file.type !== "application/pdf") {
    throw new Error("Minh chứng phải là ảnh PNG, JPG, WEBP hoặc file PDF.");
  }
  if (blob.size > MAX_BYTES) throw new Error("File minh chứng quá lớn (tối đa 1 MB).");
  return base64Of(blob);
}

async function shrinkImage(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  // Anh von da nho va nhe thi giu nguyen, khong nen lai lam giam chat luong.
  if (scale === 1 && file.size <= MAX_BYTES / 2) {
    bitmap.close();
    return file;
  }
  const canvas = document.createElement("canvas");
  canvas.width = Math.max(1, Math.round(bitmap.width * scale));
  canvas.height = Math.max(1, Math.round(bitmap.height * scale));
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    bitmap.close();
    return file;
  }
  // JPG khong co nen trong suot: to nen trang truoc de anh PNG trong suot khong bi den.
  ctx.fillStyle = "#fff";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((out) => (out ? resolve(out) : reject(new Error("Không nén được ảnh"))), "image/jpeg", 0.85);
  });
}

function base64Of(blob: Blob): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result).split(",")[1] ?? "");
    reader.onerror = () => reject(new Error("Không đọc được file minh chứng."));
    reader.readAsDataURL(blob);
  });
}
