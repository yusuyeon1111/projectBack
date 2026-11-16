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
}
