package com.ptit.oj.controller;

import com.ptit.oj.model.Assignment;
import com.ptit.oj.model.Classroom;
import com.ptit.oj.model.Contest;
import com.ptit.oj.model.Problem;
import com.ptit.oj.model.User;

import java.util.HashSet;
import java.util.Set;

/**
 * Mot nguoi dung mo duoc nhung bai nao (xem de, nop bai, binh luan). Tinh mot lan cho moi request
 * roi hoi canOpen() cho tung bai, de danh sach vai tram bai khong phai duyet lai lop va ky thi.
 *
 * Quy tac:
 *  - Quan tri vien, va nguoi sua duoc bai (chu bai kho rieng): luon mo duoc.
 *  - Bai kho chung: ai cung mo duoc. Bai kho rieng: chi mo duoc qua LOP cua minh - bai luyen tap
 *    cua lop, bai tap giao cho lop, ky thi cua lop. Hoc sinh lop khac khong thay.
 *  - Ky thi chua bat dau: hoc sinh chua thay de. Giang vien phu trach lop thi thay.
 *  - Ky thi DANG DIEN RA hoac da ket thuc: hoc sinh cua lop mo duoc bai thi o trang luyen tap nhu
 *    bai thuong. Chi bai nop SAU luc bam "Vao thi" (va truoc luc nop som) moi tinh vao ky thi.
 *  - Dang o trong bai thi: CHI mo duoc bai cua ky thi do, moi bai khac bi khoa cho toi khi thi xong.
 */
public final class ProblemAccess {

    private final User viewer;
    private final boolean admin;
    /** Ky thi nguoi nay dang lam bai; null neu khong dang thi. */
    private Contest sitting;
    /** Bai kho rieng duoc mo qua lop cua nguoi nay. */
    private final Set<String> granted = new HashSet<>();

    private ProblemAccess(User viewer) {
        this.viewer = viewer;
        this.admin = User.ROLE_ADMIN.equals(viewer.getRoleCode());
    }

    public static ProblemAccess of(Platform platform, User viewer) {
        ProblemAccess access = new ProblemAccess(viewer);
        if (access.admin) return access;
        String me = viewer.getId();
        for (Classroom c : platform.getSchool().getClassrooms()) {
            if (!c.hasMember(me)) continue;
            boolean staff = c.isTaughtBy(me);
            access.granted.addAll(c.getPracticeProblemIds());
            for (Assignment a : platform.getAssignments().getAll()) {
                if (c.getId().equals(a.getClassroomId())) access.granted.addAll(a.getProblemIds());
            }
            for (Contest k : platform.getContests().getContests()) {
                if (!c.getId().equals(k.getClassroomId())) continue;
                String status = k.getStatus();
                if (!staff && "UPCOMING".equals(status)) continue;   // chua cong bo de
                if (k.isSitting(me) && access.sitting == null) access.sitting = k;
                access.granted.addAll(k.getProblemIds());
            }
        }
        return access;
    }

    /** Ky thi nguoi nay dang lam bai, null neu khong dang thi. */
    public Contest getSitting() { return sitting; }

    public boolean canOpen(Problem problem) {
        if (admin || viewer.canEditProblem(problem)) return true;
        String id = problem.getId();
        if (sitting != null) return sitting.getProblemIds().contains(id);
        return problem.isShared() || granted.contains(id);
    }

    /** Thong bao giai thich vi sao bai bi khoa, de bao cho nguoi dung thay vi chi noi "khong co bai". */
    public String lockReason(Problem problem) {
        if (sitting != null) {
            return "Bạn đang trong bài thi \"" + sitting.getTitle() + "\": chỉ làm được các bài của kỳ thi này cho tới khi nộp bài hoặc hết giờ";
        }
        return null;
    }
}
