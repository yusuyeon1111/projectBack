package org.example.project.member.repository;

import io.lettuce.core.dynamic.annotation.Param;
import jakarta.validation.constraints.NotBlank;
import org.example.project.member.dto.MemberResponseDto;
import org.example.project.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
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

    @Query("SELECT FUNCTION('MONTH', m.createdAt) as month, COUNT(m) as count " +
            "FROM Member m " +
            "WHERE FUNCTION('YEAR', m.createdAt) = :year " +
            "GROUP BY FUNCTION('MONTH', m.createdAt) " +
            "ORDER BY FUNCTION('MONTH', m.createdAt)")
    List<Object[]> countMemberPerMonth(@Param("year") int year);

    @Query("""
    SELECT FUNCTION('MONTH', m.createdAt) as month, p.preferredPosition as category, count(p) as count
    FROM Member m
    JOIN m.memberProfile p
    WHERE FUNCTION('YEAR', m.createdAt) = :year
    AND p.preferredPosition IS NOT NULL
    GROUP BY FUNCTION('MONTH', m.createdAt), p.preferredPosition
    ORDER BY FUNCTION('MONTH', m.createdAt)
    """)
    List<Object[]> countMemberByPosition(@Param("year") int year);
}
