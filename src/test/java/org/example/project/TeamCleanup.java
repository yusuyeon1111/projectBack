package org.example.project;

import org.example.project.member.entity.Member;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collection;
import java.util.stream.Collectors;

// 승인하면 팀·팀원·신청 이력·업무·활동 기록이 회원을 FK로 가리킨다. 회원을 지우기 전에 부른다
final class TeamCleanup {

    static void deleteFor(JdbcTemplate jdbc, Collection<Member> members) {
        if (members.isEmpty()) return;
        String ids = members.stream().map(m -> String.valueOf(m.getId())).collect(Collectors.joining(","));
        String teams = "SELECT id FROM team WHERE leader_member_id IN (" + ids + ")";
        jdbc.update("DELETE FROM team_activity WHERE actor_member_id IN (" + ids + ") OR team_id IN (" + teams + ")");
        jdbc.update("DELETE FROM chat_message WHERE sender_member_id IN (" + ids + ") OR team_id IN (" + teams + ")");
        jdbc.update("DELETE FROM task_comment WHERE author_member_id IN (" + ids + ")");
        jdbc.update("DELETE FROM task WHERE creator_member_id IN (" + ids + ") OR assignee_member_id IN (" + ids + ") OR team_id IN (" + teams + ")");
        jdbc.update("DELETE FROM application_event WHERE actor_member_id IN (" + ids + ")");
        jdbc.update("DELETE FROM team_member WHERE member_id IN (" + ids + ") OR team_id IN (SELECT id FROM team WHERE leader_member_id IN (" + ids + "))");
        jdbc.update("DELETE FROM team WHERE leader_member_id IN (" + ids + ")");
    }

    private TeamCleanup() {}
}
