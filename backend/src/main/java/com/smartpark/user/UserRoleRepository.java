package com.smartpark.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    @Query("select r.roleName from UserRole ur join ur.role r where ur.user.id = :userId")
    List<String> findRoleNamesByUserId(UUID userId);

    boolean existsByUserIdAndRoleRoleName(UUID userId, String roleName);

    @Query("select ur.user from UserRole ur where ur.role.roleName = :roleName order by ur.user.createdAt desc")
    java.util.List<User> findUsersByRoleName(String roleName);
}
