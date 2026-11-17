package org.example.project.team.controller;

import lombok.RequiredArgsConstructor;
import org.example.project.team.dto.TeamDetailDto;
import org.example.project.team.dto.TeamSummaryDto;
import org.example.project.team.service.TeamService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping("/my")
    public List<TeamSummaryDto> myTeams(Principal principal) {
        return teamService.findMyTeams(principal.getName());
    }

    @GetMapping("/{teamId}")
    public TeamDetailDto team(@PathVariable Long teamId, Principal principal) {
        return teamService.findTeam(teamId, principal.getName());
    }

    @PostMapping("/{teamId}/start")
    public void start(@PathVariable Long teamId, Principal principal) {
        teamService.start(teamId, principal.getName());
    }

    @PostMapping("/{teamId}/complete")
    public void complete(@PathVariable Long teamId, Principal principal) {
        teamService.complete(teamId, principal.getName());
    }

    @PostMapping("/{teamId}/disband")
    public void disband(@PathVariable Long teamId, Principal principal) {
        teamService.disband(teamId, principal.getName());
    }

    public record LeaderRequest(Long memberId) {}

    @PutMapping("/{teamId}/leader")
    public void changeLeader(@PathVariable Long teamId, @RequestBody LeaderRequest request, Principal principal) {
        teamService.changeLeader(teamId, principal.getName(), request.memberId());
    }

    @DeleteMapping("/{teamId}/members/me")
    public void leave(@PathVariable Long teamId, Principal principal) {
        teamService.leave(teamId, principal.getName());
    }

    @DeleteMapping("/{teamId}/members/{memberId}")
    public void kick(@PathVariable Long teamId, @PathVariable Long memberId, Principal principal) {
        teamService.kick(teamId, principal.getName(), memberId);
    }
}
