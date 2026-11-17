package com.ptit.oj.view.problem;

import com.ptit.oj.model.Problem;
import com.ptit.oj.model.Topic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Cac ham tien ich cho lo trinh hoc. Thu tu hoc lay tu data/topics.txt: chu de dung
 * truoc hoc truoc. Trong mot chu de: bai co khai bao order xep truoc theo order, cac
 * bai con lai xep theo ma bai.
 */
public class LearningPath {

    /** Nhom cho bai chua gan chu de, hoac gan chu de khong co trong lo trinh. */
    public static final String OTHER_TOPIC = "__other__";

    /** Sap bai theo lo trinh: chu de theo thu tu hoc, trong chu de theo order roi ma bai, bai khac xep cuoi. */
    public static List<Problem> orderByPath(List<Problem> problems, List<Topic> topics) {
        List<Problem> sorted = new ArrayList<>(problems);
        Collections.sort(sorted, new Comparator<Problem>() {
            @Override
            public int compare(Problem a, Problem b) {
                int rankA = topicRank(a, topics);
                int rankB = topicRank(b, topics);
                if (rankA != rankB) {
                    return rankA - rankB;
                }
                int orderA = orderOf(a);
                int orderB = orderOf(b);
                if (orderA != orderB) {
                    return Integer.compare(orderA, orderB);
                }
                return a.getId().compareTo(b.getId());
            }
        });
        return sorted;
    }

    /** Vi tri chu de cua bai trong lo trinh; bai ngoai lo trinh dung sau cung. */
    private static int topicRank(Problem problem, List<Topic> topics) {
        for (int i = 0; i < topics.size(); i++) {
            if (topics.get(i).getId().equals(problem.getTopic())) return i;
        }
        return topics.size();
    }

    /** Thu tu cua bai trong chu de; bai khong khai bao order (0) dung sau cac bai co order. */
    private static int orderOf(Problem problem) {
        if (problem.getOrder() > 0) return problem.getOrder();
        return Integer.MAX_VALUE;
    }

    /** Ma chu de cua bai neu chu de do nam trong danh sach, khong thi la OTHER_TOPIC. */
    public static String topicKey(Problem problem, List<Topic> topics) {
        for (Topic t : topics) {
            if (t.getId().equals(problem.getTopic())) return t.getId();
        }
        return OTHER_TOPIC;
    }

    /** Ten chu de de hien thi; bai ngoai lo trinh hien la "Bai khac". */
    public static String topicName(Problem problem, List<Topic> topics) {
        for (Topic t : topics) {
            if (t.getId().equals(problem.getTopic())) return t.getName();
        }
        return "Bài khác";
    }
}
