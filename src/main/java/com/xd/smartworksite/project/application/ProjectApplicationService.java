package com.xd.smartworksite.project.application;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.xd.smartworksite.common.exception.BusinessException;
import com.xd.smartworksite.common.result.ErrorCode;
import com.xd.smartworksite.common.result.PageResult;
import com.xd.smartworksite.project.domain.Project;
import com.xd.smartworksite.project.dto.ProjectCreateRequest;
import com.xd.smartworksite.project.dto.ProjectQueryRequest;
import com.xd.smartworksite.project.dto.ProjectResponse;
import com.xd.smartworksite.project.dto.ProjectUpdateRequest;
import com.xd.smartworksite.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class ProjectApplicationService {

    private static final String PROJECT_STATUS_ENABLED = "ENABLED";

    private final ProjectRepository projectRepository;

    public ProjectApplicationService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public PageResult<ProjectResponse> queryProjects(ProjectQueryRequest request) {
        Page<Project> page = PageHelper.startPage(request.getPageNo(), request.getPageSize())
                .doSelectPage(() -> projectRepository.findPage(request.getKeyword()));
        List<ProjectResponse> records = page.getResult().stream().map(this::toResponse).toList();
        return new PageResult<>(request.getPageNo(), request.getPageSize(), page.getTotal(), records);
    }

    public ProjectResponse getProject(Long projectId) {
        return toResponse(requireProject(projectId));
    }

    @Transactional
    public ProjectResponse createProject(ProjectCreateRequest request) {
        String projectCode = normalizeProjectCode(request.getProjectCode());
        ensureProjectCodeAvailable(projectCode, null);

        Project project = new Project();
        project.setProjectName(normalizeRequiredText(request.getProjectName(), "projectName is required"));
        project.setProjectCode(projectCode);
        project.setLocation(trimToNull(request.getLocation()));
        project.setDescription(trimToNull(request.getDescription()));
        project.setStatus(PROJECT_STATUS_ENABLED);
        projectRepository.insert(project);
        return getProject(project.getId());
    }

    @Transactional
    public ProjectResponse updateProject(Long projectId, ProjectUpdateRequest request) {
        Project project = requireProject(projectId);
        String projectCode = normalizeProjectCode(request.getProjectCode());
        ensureProjectCodeAvailable(projectCode, projectId);

        project.setProjectName(normalizeRequiredText(request.getProjectName(), "projectName is required"));
        project.setProjectCode(projectCode);
        project.setLocation(trimToNull(request.getLocation()));
        project.setDescription(trimToNull(request.getDescription()));
        projectRepository.update(project);
        return getProject(projectId);
    }

    private Project requireProject(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "projectId is required");
        }
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "project not found"));
    }

    private void ensureProjectCodeAvailable(String projectCode, Long currentProjectId) {
        projectRepository.findByProjectCode(projectCode)
                .filter(project -> currentProjectId == null || !currentProjectId.equals(project.getId()))
                .ifPresent(project -> {
                    throw new BusinessException(ErrorCode.CONFLICT, "projectCode already exists");
                });
    }

    private String normalizeProjectCode(String projectCode) {
        return normalizeRequiredText(projectCode, "projectCode is required").toUpperCase(Locale.ROOT);
    }

    private String normalizeRequiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ProjectResponse toResponse(Project project) {
        ProjectResponse response = new ProjectResponse();
        response.setProjectId(project.getId());
        response.setProjectName(project.getProjectName());
        response.setProjectCode(project.getProjectCode());
        response.setLocation(project.getLocation());
        response.setStatus(project.getStatus());
        response.setDescription(project.getDescription());
        response.setCreatedAt(project.getCreatedAt());
        response.setUpdatedAt(project.getUpdatedAt());
        return response;
    }
}
