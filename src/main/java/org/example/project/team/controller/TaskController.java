package org.example.project.team.controller;

import lombok.RequiredArgsConstructor;
import org.example.project.team.dto.ActivityDto;
import org.example.project.team.dto.TaskDto;
import org.example.project.team.dto.TaskRequest;
import org.example.project.team.service.TaskService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/teams/{teamId}")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/tasks")
    public List<TaskDto> tasks(@PathVariable Long teamId, Principal principal) {
        return taskService.findTasks(teamId, principal.getName());
    }

    @PostMapping("/tasks")
    public TaskDto create(@PathVariable Long teamId, @RequestBody TaskRequest request, Principal principal) {
        return taskService.create(teamId, principal.getName(), request);
    }

    @GetMapping("/tasks/{taskId}")
    public TaskDto task(@PathVariable Long teamId, @PathVariable Long taskId, Principal principal) {
        return taskService.findTask(teamId, taskId, principal.getName());
    }

    @PatchMapping("/tasks/{taskId}")
    public TaskDto update(@PathVariable Long teamId, @PathVariable Long taskId, @RequestBody TaskRequest request, Principal principal) {
        return taskService.update(teamId, taskId, principal.getName(), request);
    }

    public record StatusRequest(String from, String to) {}

    @PatchMapping("/tasks/{taskId}/status")
    public TaskDto changeStatus(@PathVariable Long teamId, @PathVariable Long taskId, @RequestBody StatusRequest request, Principal principal) {
        return taskService.changeStatus(teamId, taskId, principal.getName(), request.from(), request.to());
    }

    @DeleteMapping("/tasks/{taskId}")
    public void delete(@PathVariable Long teamId, @PathVariable Long taskId, Principal principal) {
        taskService.delete(teamId, taskId, principal.getName());
    }

    @GetMapping("/activities")
    public List<ActivityDto> activities(@PathVariable Long teamId, @RequestParam(required = false) Long cursor,
                                        @RequestParam(required = false) Long taskId, Principal principal) {
        return taskService.findActivities(teamId, principal.getName(), cursor, taskId);
    }
}
