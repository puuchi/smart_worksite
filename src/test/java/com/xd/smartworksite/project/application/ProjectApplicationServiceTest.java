package com.xd.smartworksite.project.application;

import com.xd.smartworksite.common.exception.BusinessException;
import com.xd.smartworksite.common.result.ErrorCode;
import com.xd.smartworksite.project.domain.Project;
import com.xd.smartworksite.project.dto.ProjectCreateRequest;
import com.xd.smartworksite.project.dto.ProjectResponse;
import com.xd.smartworksite.project.dto.ProjectUpdateRequest;
import com.xd.smartworksite.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectApplicationServiceTest {

    @Test
    void createProjectSucceeds() {
        InMemoryProjectRepository repository = new InMemoryProjectRepository();
        ProjectApplicationService service = new ProjectApplicationService(repository);

        ProjectResponse response = service.createProject(createRequest("??????", "site-001"));

        assertThat(response.getProjectId()).isEqualTo(1L);
        assertThat(response.getProjectName()).isEqualTo("??????");
        assertThat(response.getProjectCode()).isEqualTo("SITE-001");
        assertThat(response.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    void createProjectRejectsDuplicateProjectCode() {
        InMemoryProjectRepository repository = new InMemoryProjectRepository();
        ProjectApplicationService service = new ProjectApplicationService(repository);
        service.createProject(createRequest("??????", "SITE-001"));

        assertThatThrownBy(() -> service.createProject(createRequest("??????", "site-001")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode()));
    }

    @Test
    void updateProjectSucceeds() {
        InMemoryProjectRepository repository = new InMemoryProjectRepository();
        ProjectApplicationService service = new ProjectApplicationService(repository);
        ProjectResponse created = service.createProject(createRequest("??????", "SITE-001"));
        ProjectUpdateRequest request = updateRequest("????????", "SITE-002");

        ProjectResponse response = service.updateProject(created.getProjectId(), request);

        assertThat(response.getProjectName()).isEqualTo("????????");
        assertThat(response.getProjectCode()).isEqualTo("SITE-002");
    }

    @Test
    void updateProjectRejectsMissingProject() {
        ProjectApplicationService service = new ProjectApplicationService(new InMemoryProjectRepository());

        assertThatThrownBy(() -> service.updateProject(404L, updateRequest("?????", "NO-PROJECT")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode()));
    }

    @Test
    void updateProjectRejectsProjectCodeOwnedByAnotherProject() {
        InMemoryProjectRepository repository = new InMemoryProjectRepository();
        ProjectApplicationService service = new ProjectApplicationService(repository);
        ProjectResponse first = service.createProject(createRequest("??????", "SITE-001"));
        service.createProject(createRequest("??????", "SITE-002"));

        assertThatThrownBy(() -> service.updateProject(first.getProjectId(), updateRequest("??????", "SITE-002")))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode()));
    }

    private ProjectCreateRequest createRequest(String projectName, String projectCode) {
        ProjectCreateRequest request = new ProjectCreateRequest();
        request.setProjectName(projectName);
        request.setProjectCode(projectCode);
        request.setLocation("??");
        request.setDescription("????");
        return request;
    }

    private ProjectUpdateRequest updateRequest(String projectName, String projectCode) {
        ProjectUpdateRequest request = new ProjectUpdateRequest();
        request.setProjectName(projectName);
        request.setProjectCode(projectCode);
        request.setLocation("??");
        request.setDescription("????????");
        return request;
    }

    private static class InMemoryProjectRepository implements ProjectRepository {
        private long nextId = 1L;
        private final List<Project> projects = new ArrayList<>();

        @Override
        public List<Project> findPage(String keyword) {
            return projects.stream()
                    .filter(project -> keyword == null || keyword.isBlank()
                            || project.getProjectName().contains(keyword)
                            || project.getProjectCode().contains(keyword))
                    .toList();
        }

        @Override
        public Optional<Project> findById(Long projectId) {
            return projects.stream().filter(project -> projectId.equals(project.getId())).findFirst();
        }

        @Override
        public Optional<Project> findByProjectCode(String projectCode) {
            return projects.stream().filter(project -> projectCode.equals(project.getProjectCode())).findFirst();
        }

        @Override
        public Project insert(Project project) {
            project.setId(nextId++);
            project.setCreatedAt(LocalDateTime.now());
            project.setUpdatedAt(project.getCreatedAt());
            projects.add(project);
            return project;
        }

        @Override
        public void update(Project project) {
            Project current = findById(project.getId()).orElseThrow();
            current.setProjectName(project.getProjectName());
            current.setProjectCode(project.getProjectCode());
            current.setLocation(project.getLocation());
            current.setDescription(project.getDescription());
            current.setUpdatedAt(LocalDateTime.now());
        }
    }
}
