package com.ptit.oj.controller.api;

import com.ptit.oj.controller.BillingService;
import com.ptit.oj.controller.Json;
import com.ptit.oj.controller.Platform;
import com.ptit.oj.model.Payment;
import com.ptit.oj.model.Plan;
import com.ptit.oj.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Module 34 (goi dich vu va thanh toan) - ban mo phong, khong co tien that:
 *   GET  /api/billing          - cac goi, goi dang dung, lich su giao dich cua minh
 *   POST /api/billing/pay      - "thanh toan" mot goi (ghi nhan thanh cong ngay)
 *   GET  /api/admin/payments   - toan bo giao dich va tong doanh thu (quan tri)
 */
public class BillingApi extends ApiModule {

    private final BillingService billing;

    public BillingApi(Platform platform) {
        super(platform);
        this.billing = platform.getBilling();
    }

    @Override
    public boolean handle(Request req) throws IOException {
        if (req.is("GET", "billing")) {
            User user = req.user();
            List<String> plans = new ArrayList<>();
            for (Plan p : Plan.values()) {
                plans.add(Json.obj().put("code", p.name()).put("name", p.getDisplayName())
                        .put("price", p.getPrice()).put("features", p.getFeatures()).build());
            }
            req.send(Json.obj()
                    .put("simulated", true)
                    .put("currentPlan", billing.currentPlan(user.getId()).name())
                    .putRaw("plans", Json.array(plans))
                    .putRaw("payments", paymentsJson(billing.paymentsOf(user.getId()), null))
                    .build());
            return true;
        }
        if (req.is("POST", "billing", "pay")) {
            User user = req.user();
            Payment payment = billing.pay(user, req.body().getString("plan"));
            platform.getCommunity().notifyUser(user.getId(), "Thanh toán thành công (mô phỏng)",
                    "Gói " + payment.getPlan().getDisplayName() + " có hiệu lực đến "
                            + time(payment.getExpiresAt()).replace('T', ' '));
            req.sendOk();
            return true;
        }
        if (req.is("GET", "admin", "payments")) {
            req.admin();
            req.send(Json.obj()
                    .put("revenue", billing.totalRevenue())
                    .putRaw("payments", paymentsJson(billing.getPayments(), usersById()))
                    .build());
            return true;
        }
        return false;
    }

    /** users khac null thi kem ten nguoi mua (cho man hinh quan tri). */
    private String paymentsJson(List<Payment> payments, Map<String, User> users) {
        List<String> items = new ArrayList<>();
        for (Payment p : payments) {
            Json.Obj o = Json.obj().put("id", p.getId()).put("planName", p.getPlan().getDisplayName())
                    .put("amount", p.getAmount()).put("createdAt", time(p.getCreatedAt()))
                    .put("expiresAt", time(p.getExpiresAt())).put("active", p.isActive());
            if (users != null) o.put("buyerName", nameOf(users, p.getUserId()));
            items.add(o.build());
        }
        return Json.array(items);
    }
}
