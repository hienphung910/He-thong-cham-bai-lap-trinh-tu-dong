package com.ptit.oj.controller;

import com.ptit.oj.dao.IdGenerator;
import com.ptit.oj.dao.InMemoryRepository;
import com.ptit.oj.dao.Repository;
import com.ptit.oj.model.Payment;
import com.ptit.oj.model.Plan;
import com.ptit.oj.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Goi dich vu va thanh toan (Module 34) - BAN MO PHONG.
 *
 * Khong ket noi cong thanh toan nao va khong nhan thong tin the: bam "thanh toan"
 * la giao dich duoc ghi nhan thanh cong ngay, goi co hieu luc 30 ngay.
 */
public class BillingService {

    private final Repository<Payment> payments = new InMemoryRepository<>();

    /** Thanh toan (mo phong) cho mot goi tra phi. */
    public Payment pay(User user, String planCode) {
        Plan plan;
        try {
            plan = Plan.valueOf(planCode == null ? "" : planCode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Không có gói " + planCode);
        }
        if (plan == Plan.FREE) throw ApiException.badRequest("Gói miễn phí không cần thanh toán");

        Payment payment = new Payment(IdGenerator.next("PAY"), user.getId(), plan);
        payments.save(payment);
        return payment;
    }

    /** Lich su giao dich cua mot nguoi, moi nhat truoc. */
    public List<Payment> paymentsOf(String userId) {
        List<Payment> mine = new ArrayList<>();
        for (Payment p : getPayments()) {
            if (p.getUserId().equals(userId)) mine.add(p);
        }
        return mine;
    }

    /** Toan bo giao dich, moi nhat truoc. */
    public List<Payment> getPayments() {
        List<Payment> all = payments.findAll();
        Collections.reverse(all);
        return all;
    }

    /** Goi dang dung = giao dich gan nhat con han; khong co thi la goi mien phi. */
    public Plan currentPlan(String userId) {
        for (Payment p : paymentsOf(userId)) {
            if (p.isActive()) return p.getPlan();
        }
        return Plan.FREE;
    }

    public long totalRevenue() {
        long total = 0;
        for (Payment p : payments.findAll()) total += p.getAmount();
        return total;
    }
}
