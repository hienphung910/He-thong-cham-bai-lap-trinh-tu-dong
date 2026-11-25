package com.ptit.oj.dao;

import com.ptit.oj.model.Credentials;
import com.ptit.oj.model.User;

import java.util.Map;
import java.util.Optional;

/**
 * Kho tai khoan. Mo rong Repository<User> them phan xac thuc va thong ke,
 * de tang service khong phai biet du lieu nam trong RAM hay trong MySQL.
 */
public interface UserRepository extends Repository<User> {

    /** Tim theo ten dang nhap, KHONG phan biet hoa thuong. */
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Tao/cap nhat tai khoan kem thong tin bam mat khau. */
    void saveWithCredentials(User user, Credentials credentials);

    Optional<Credentials> findCredentials(String userId);

    /**
     * Kho co luu duoc trang thai "cho duyet" cua tai khoan khong. Chi false khi bang users cua
     * MySQL thieu cot approved va khong tu them duoc; khi do khong nhan giang vien tu dang ky.
     */
    default boolean supportsApproval() { return true; }

    /** Tim theo email khoi phuc, KHONG phan biet hoa thuong; rong neu khong tai khoan nao dung email do. */
    Optional<User> findByEmail(String email);

    /**
     * Kho co luu duoc email cua tai khoan khong. Chi false khi bang users cua MySQL thieu cot email
     * va khong tu them duoc; khi do khong dat lai mat khau qua email duoc.
     */
    default boolean supportsEmail() { return true; }

    /** Bang solvedCount cho tat ca tai khoan: userId -> so bai da giai. */
    Map<String, Integer> solvedCountByUser();
}
