package com.ptit.oj.dao;

import com.ptit.oj.model.Credentials;
import com.ptit.oj.model.User;

import java.util.Map;

/**
 * Kho tai khoan. Mo rong Repository<User> them phan xac thuc va thong ke,
 * de tang service khong phai biet du lieu nam trong RAM hay trong MySQL.
 */
public interface UserRepository extends Repository<User> {

    /** Tim theo ten dang nhap, KHONG phan biet hoa thuong. Tra ve null neu khong co. */
    User findByUsername(String username);

    boolean existsByUsername(String username);

    /** Tao/cap nhat tai khoan kem thong tin bam mat khau. */
    void saveWithCredentials(User user, Credentials credentials);

    /** Tra ve null neu tai khoan chua co thong tin xac thuc. */
    Credentials findCredentials(String userId);

    /** Bang solvedCount cho tat ca tai khoan: userId -> so bai da giai. */
    Map<String, Integer> solvedCountByUser();
}
