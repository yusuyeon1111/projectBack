package org.example.project.member.repository;

import io.lettuce.core.dynamic.annotation.Param;
import jakarta.validation.constraints.NotBlank;
import org.example.project.member.dto.MemberResponseDto;
import org.example.project.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<Member> findByEmail(String email);

    @Query("SELECT m FROM Member m " +
            "LEFT JOIN FETCH m.memberProfile p " +
            "LEFT JOIN FETCH p.preferredTechStacks " +
            "WHERE m.username = :username")
    Optional<Member> findWithProfileAndStacksByUsername(String username);

    @Query("SELECT m.nickname FROM Member m WHERE m.username = :username")
    Optional<String> findNicknameByUsername(@Param("username") String username);

    boolean existsByEmail(@NotBlank String email);
}
