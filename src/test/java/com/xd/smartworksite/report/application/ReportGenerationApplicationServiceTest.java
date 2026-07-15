package com.xd.smartworksite.report.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xd.smartworksite.ai.domain.DataSourceRecord;
import com.xd.smartworksite.ai.domain.ExternalCallLog;
import com.xd.smartworksite.ai.infra.AiProviderResponse;
import com.xd.smartworksite.ai.infra.AiPythonServiceClient;
import com.xd.smartworksite.ai.infra.AiPythonServiceProperties;
import com.xd.smartworksite.ai.repository.AiRepository;
import com.xd.smartworksite.auth.domain.ProjectMember;
import com.xd.smartworksite.auth.mapper.ProjectMemberMapper;
import com.xd.smartworksite.common.exception.BusinessException;
import com.xd.smartworksite.common.result.ErrorCode;
import com.xd.smartworksite.common.security.UserPrincipal;
import com.xd.smartworksite.file.application.FileParseApplicationService;
import com.xd.smartworksite.file.application.FileParseWorker;
import com.xd.smartworksite.file.application.FileProperties;
import com.xd.smartworksite.file.domain.FileObject;
import com.xd.smartworksite.file.domain.FileParseRecord;
import com.xd.smartworksite.file.infra.StorageAdapter;
import com.xd.smartworksite.file.infra.StorageObject;
import com.xd.smartworksite.file.repository.FileObjectRepository;
import com.xd.smartworksite.file.repository.FileParseRecordRepository;
import com.xd.smartworksite.project.application.ProjectAccessApplicationService;
import com.xd.smartworksite.project.domain.Project;
import com.xd.smartworksite.project.repository.ProjectRepository;
import com.xd.smartworksite.report.domain.GenerateTask;
import com.xd.smartworksite.report.domain.Report;
import com.xd.smartworksite.report.domain.ReportConfig;
import com.xd.smartworksite.report.domain.ReportVersion;
import com.xd.smartworksite.report.dto.ReportCreateRequest;
import com.xd.smartworksite.report.dto.ReportCreateResponse;
import com.xd.smartworksite.report.repository.ReportRepository;
import com.xd.smartworksite.task.application.TaskOutboxApplicationService;
import com.xd.smartworksite.task.domain.TaskOutboxEvent;
import com.xd.smartworksite.task.domain.TaskStageLog;
import com.xd.smartworksite.task.domain.TaskStatusCount;
import com.xd.smartworksite.task.repository.TaskRepository;
import com.xd.smartworksite.template.domain.FileObjectRecord;
import com.xd.smartworksite.template.domain.Template;
import com.xd.smartworksite.template.domain.TemplateCategory;
import com.xd.smartworksite.template.domain.TemplateStatus;
import com.xd.smartworksite.template.repository.TemplateRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportGenerationApplicationServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUpSecurityContext() {
        UserPrincipal principal = new UserPrincipal(1L, "admin", List.of("PLATFORM_ADMIN"), List.of(), 1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createReportCreatesQueuedTaskWithoutCallingAi() {
        TestContext context = new TestContext();
        ReportCreateResponse response = context.service().createReport(createRequest());

        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(context.ai.lastPayload).isNull();
        assertThat(context.reports.tasks.get(0).getStatus()).isEqualTo("QUEUED");
        assertThat(context.reports.outbox.enqueuedTaskIds).containsExactly(1L);
        assertThat(context.reports.generatedFiles()).isEmpty();
    }

    @Test
    void executeReportTaskRendersDocxWithMixedPlaceholdersAndUserVariablePriority() throws Exception {
        TestContext context = new TestContext();
        context.storage.put("templates/report.docx", docx("项目：${项目名称}", "摘要：{{摘要}}", "结论：{{ 结论 }}"));
        context.storage.put("materials/source.txt", "项目材料：安全检查通过，风险较低。".getBytes());
        context.ai.answer = objectMapper.writeValueAsString(Map.of("摘要", "AI摘要", "结论", "AI结论"));
        ReportCreateRequest request = createRequest();
        request.setVariables(Map.of("项目名称", "用户项目"));

        ReportCreateResponse created = context.service().createReport(request);
        context.service().executeReportTask(created.getReportId(), created.getTaskId());

        assertThat(context.reports.reports.get(0).getStatus()).isEqualTo("COMPLETED");
        String rendered = readDocxText(context.storage.objects.get(context.reports.generatedFiles().get(0).getObjectName()));
        assertThat(rendered).contains("项目：用户项目", "摘要：AI摘要", "结论：AI结论");
    }

    @Test
    void executeReportTaskUsesClaimedTaskIdForWorkerStageUpdates() {
        TestContext context = new TestContext();
        ReportCreateResponse created = context.service().createReport(createRequest());
        Long claimedTaskId = created.getTaskId();
        context.reports.reports.get(0).setTaskId(9999L);

        context.service().executeReportTask(created.getReportId(), claimedTaskId);

        assertThat(context.reports.reports.get(0).getStatus()).isEqualTo("COMPLETED");
        assertThat(context.reports.tasks.get(0).getStatus()).isEqualTo("RUNNING");
        assertThat(context.reports.tasks.get(0).getCurrentStage()).isEqualTo("STORING_RESULT");
    }

    @Test
    void executeReportTaskFailsWhenAiReturnsNonJson() {
        TestContext context = new TestContext();
        context.ai.answer = "不是JSON";
        ReportCreateResponse created = context.service().createReport(createRequest());

        assertThatThrownBy(() -> context.service().executeReportTask(created.getReportId(), created.getTaskId()))
                .hasMessageContaining("不是严格JSON对象");
        assertThat(context.reports.reports.get(0).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void executeReportTaskFailsWhenAiMissesVariable() throws Exception {
        TestContext context = new TestContext();
        context.ai.answer = objectMapper.writeValueAsString(Map.of("其他", "值"));
        ReportCreateResponse created = context.service().createReport(createRequest());

        assertThatThrownBy(() -> context.service().executeReportTask(created.getReportId(), created.getTaskId()))
                .hasMessageContaining("未生成报告变量: 摘要");
        assertThat(context.reports.generatedFiles()).isEmpty();
    }

    @Test
    void executeReportTaskRejectsReferenceFileFromAnotherProject() {
        TestContext context = new TestContext();
        context.reports.fileObjects.add(file(99L, 2L, "foreign.txt", "foreign.txt", "text/plain"));
        ReportCreateRequest request = createRequest();
        request.setReferenceFileIds(List.of(99L));
        ReportCreateResponse created = context.service().createReport(request);

        assertThatThrownBy(() -> context.service().executeReportTask(created.getReportId(), created.getTaskId()))
                .hasMessageContaining("参考材料不属于当前项目");
        assertThat(context.ai.lastPayload).isNull();
    }

    @Test
    void executeReportTaskUsesSuccessfulParseContentForNonTextReference() throws Exception {
        TestContext context = new TestContext();
        context.reports.fileObjects.add(file(20L, 1L, "source.pdf", "materials/source.pdf", "application/pdf"));
        context.parses.records.add(parseRecord(1L, 20L, 1L, "SUCCESS", "parse/result.md"));
        context.storage.put("parse/result.md", "解析后的PDF材料".getBytes());
        context.ai.answer = objectMapper.writeValueAsString(Map.of("摘要", "PDF摘要"));
        ReportCreateRequest request = createRequest();
        request.setReferenceFileIds(List.of(20L));
        ReportCreateResponse created = context.service().createReport(request);

        context.service().executeReportTask(created.getReportId(), created.getTaskId());

        String rendered = readDocxText(context.storage.objects.get(context.reports.generatedFiles().get(0).getObjectName()));
        assertThat(rendered).contains("PDF摘要");
    }

    @Test
    void executeReportTaskRejectsNonTextReferenceWithoutSuccessfulParse() {
        TestContext context = new TestContext();
        context.reports.fileObjects.add(file(20L, 1L, "source.pdf", "materials/source.pdf", "application/pdf"));
        context.parses.records.add(parseRecord(1L, 20L, 1L, "FAILED", "parse/result.md"));
        ReportCreateRequest request = createRequest();
        request.setReferenceFileIds(List.of(20L));
        ReportCreateResponse created = context.service().createReport(request);

        assertThatThrownBy(() -> context.service().executeReportTask(created.getReportId(), created.getTaskId()))
                .hasMessageContaining("参考材料解析未成功");
    }

    @Test
    void executeReportTaskRejectsNonDocxTemplate() {
        TestContext context = new TestContext();
        context.templateFile.setFileName("report.txt");
        ReportCreateResponse created = context.service().createReport(createRequest());

        assertThatThrownBy(() -> context.service().executeReportTask(created.getReportId(), created.getTaskId()))
                .hasMessageContaining("仅支持DOCX模板");
    }

    @Test
    void executeReportTaskRejectsDisabledProjectBeforeAiCall() {
        TestContext context = new TestContext();
        ReportCreateResponse created = context.service().createReport(createRequest());
        context.projects.status = "DISABLED";

        assertThatThrownBy(() -> context.service().executeReportTask(created.getReportId(), created.getTaskId()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode()))
                .hasMessageContaining("project is not enabled");
        assertThat(context.ai.lastPayload).isNull();
        assertThat(context.reports.reports.get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void executeReportTaskFailsFastWhenSuccessStateCannotBePersisted() throws Exception {
        TestContext context = new TestContext();
        context.ai.answer = objectMapper.writeValueAsString(Map.of("摘要", "AI摘要"));
        context.reports.failReportSuccessUpdate = true;
        ReportCreateResponse created = context.service().createReport(createRequest());

        assertThatThrownBy(() -> context.service().executeReportTask(created.getReportId(), created.getTaskId()))
                .isInstanceOfSatisfying(BusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo(ErrorCode.CONFLICT.getCode()))
                .hasMessageContaining("report success status update failed");
    }

    @Test
    void downloadReportRequiresCompletedReportAndExistingWordFile() throws Exception {
        TestContext context = new TestContext();
        context.ai.answer = objectMapper.writeValueAsString(Map.of("摘要", "AI摘要"));
        ReportCreateResponse created = context.service().createReport(createRequest());

        assertThatThrownBy(() -> context.service().createDownloadUrl(created.getReportId(), "WORD"))
                .hasMessageContaining("报告尚未生成成功");

        context.service().executeReportTask(created.getReportId(), created.getTaskId());
        assertThat(context.service().createDownloadUrl(created.getReportId(), "WORD"))
                .startsWith("http://127.0.0.1/reports/project-1/report-1/");
    }

    private ReportCreateRequest createRequest() {
        ReportCreateRequest request = new ReportCreateRequest();
        request.setProjectId(1L);
        request.setReportName("智慧工地报告");
        request.setReportType("SAFETY_REPORT");
        request.setTemplateId(1001L);
        request.setReferenceFileIds(List.of(10L));
        request.setVariables(Map.of());
        return request;
    }

    private byte[] docx(String... paragraphs) {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String text : paragraphs) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.createRun().setText(text);
            }
            document.write(output);
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String readDocxText(byte[] bytes) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder builder = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                builder.append(paragraph.getText()).append('\n');
            }
            return builder.toString();
        }
    }

    private static FileObjectRecord file(Long id, Long projectId, String fileName, String objectName, String contentType) {
        FileObjectRecord file = new FileObjectRecord();
        file.setId(id);
        file.setProjectId(projectId);
        file.setFileName(fileName);
        file.setObjectName(objectName);
        file.setContentType(contentType);
        file.setStatus("ACTIVE");
        return file;
    }

    private static FileParseRecord parseRecord(Long id, Long fileId, Long projectId, String status, String objectName) {
        FileParseRecord record = new FileParseRecord();
        record.setId(id);
        record.setFileId(fileId);
        record.setProjectId(projectId);
        record.setStatus(status);
        record.setResultFormat("MARKDOWN");
        record.setResultObjectName(objectName);
        return record;
    }

    private class TestContext {
        private final InMemoryReportRepository reports = new InMemoryReportRepository();
        private final MutableProjectRepository projects = new MutableProjectRepository();
        private final MemoryStorageAdapter storage = new MemoryStorageAdapter();
        private final EmptyFileObjectRepository files = new EmptyFileObjectRepository();
        private final InMemoryFileParseRecordRepository parses = new InMemoryFileParseRecordRepository();
        private final CapturingAiPythonServiceClient ai = new CapturingAiPythonServiceClient();
        private final FileObjectRecord templateFile = file(501L, 1L, "report.docx", "templates/report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        private final Template template = new Template();

        private TestContext() {
            template.setId(1001L);
            template.setProjectId(1L);
            template.setTemplateCategory(TemplateCategory.REPORT.name());
            template.setStatus(TemplateStatus.ENABLED.name());
            template.setFileId(templateFile.getId());
            reports.fileObjects.add(file(10L, 1L, "source.txt", "materials/source.txt", "text/plain"));
            storage.put("templates/report.docx", docx("摘要：{{摘要}}"));
            storage.put("materials/source.txt", "默认材料".getBytes());
        }

        private ReportGenerationApplicationService service() {
            ProjectAccessApplicationService access = new ProjectAccessApplicationService(projects, new EmptyProjectMemberMapper());
            FileParseApplicationService parseService = new FileParseApplicationService(
                    files, parses, new FileParseWorker(files, parses, null, null, storage, new FileProperties(), objectMapper),
                    storage, new FileProperties(), objectMapper, access);
            return new ReportGenerationApplicationService(
                    reports, access, templateRepository(), reports.outbox, storage, parseService,
                    ai, new AiPythonServiceProperties(), objectMapper);
        }

        private TemplateRepository templateRepository() {
            return new TemplateRepository() {
                @Override public FileObjectRecord saveFileObject(FileObjectRecord fileObject) { return fileObject; }
                @Override public Optional<FileObjectRecord> findFileObjectById(Long fileId) { return fileId.equals(templateFile.getId()) ? Optional.of(templateFile) : Optional.empty(); }
                @Override public int updateFileBizId(Long fileId, Long bizId) { return 1; }
                @Override public Template save(Template template) { return template; }
                @Override public Optional<Template> findById(Long templateId) { return templateId.equals(template.getId()) ? Optional.of(template) : Optional.empty(); }
                @Override public List<Template> findPage(Long projectId, List<Long> accessibleProjectIds, String templateCategory, String templateType, String status, String keyword) { return List.of(); }
                @Override public int update(Template template) { return 1; }
                @Override public int updateStatus(Long templateId, String status) { return 1; }
                @Override public int delete(Long templateId) { return 1; }
            };
        }
    }

    private static class CapturingAiPythonServiceClient extends AiPythonServiceClient {
        private String answer = "{\"摘要\":\"AI摘要\"}";
        private Map<String, Object> lastPayload;

        CapturingAiPythonServiceClient() {
            super(new AiPythonServiceProperties(), new ObjectMapper(), new NoopAiRepository());
        }

        @Override
        public AiProviderResponse post(String path, String callType, Long projectId, Object payload) {
            this.lastPayload = new ObjectMapper().convertValue(payload, new TypeReference<>() {});
            AiProviderResponse response = new AiProviderResponse();
            response.setSuccess(true);
            response.setTraceId("trace-report");
            response.setData(Map.of("answer", answer));
            return response;
        }
    }

    private static class MemoryStorageAdapter implements StorageAdapter {
        private final Map<String, byte[]> objects = new LinkedHashMap<>();

        void put(String objectName, byte[] bytes) {
            objects.put(objectName, bytes);
        }

        @Override
        public StorageObject upload(String objectName, InputStream inputStream, long size, String contentType) {
            try {
                objects.put(objectName, inputStream.readAllBytes());
                return new StorageObject(objectName, "test-bucket", contentType, objects.get(objectName).length);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public InputStream openObject(String objectName) {
            byte[] bytes = objects.get(objectName);
            if (bytes == null) {
                throw new IllegalStateException("object not found: " + objectName);
            }
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public String createAccessUrl(String objectName, Duration expire) {
            return "http://127.0.0.1/" + objectName;
        }

        @Override
        public void delete(String objectName) {
            objects.remove(objectName);
        }
    }

    private class RecordingTaskOutboxService extends TaskOutboxApplicationService {
        private final List<Long> enqueuedTaskIds = new ArrayList<>();

        RecordingTaskOutboxService() {
            super(new NoopTaskRepository(), null, null);
        }

        @Override
        public void enqueueTask(com.xd.smartworksite.task.domain.GenerateTask task, String reason) {
            enqueuedTaskIds.add(task.getId());
        }
    }

    private class InMemoryReportRepository implements ReportRepository {
        private long nextConfigId = 1L;
        private long nextReportId = 1L;
        private long nextTaskId = 1L;
        private long nextFileId = 100L;
        private long nextVersionId = 1L;
        private final List<ReportConfig> configs = new ArrayList<>();
        private final List<Report> reports = new ArrayList<>();
        private final List<GenerateTask> tasks = new ArrayList<>();
        private final List<FileObjectRecord> fileObjects = new ArrayList<>();
        private final List<ReportVersion> versions = new ArrayList<>();
        private final RecordingTaskOutboxService outbox = new RecordingTaskOutboxService();
        private boolean failReportSuccessUpdate;

        @Override public ReportConfig saveConfig(ReportConfig config) { config.setId(nextConfigId++); configs.add(config); return config; }
        @Override public Report saveReport(Report report) { report.setId(nextReportId++); reports.add(report); return report; }
        @Override public GenerateTask saveTask(GenerateTask task) { task.setId(nextTaskId++); tasks.add(task); return task; }
        @Override public int updateReportTask(Long reportId, Long taskId) { findReport(reportId).setTaskId(taskId); return 1; }
        @Override public int updateTaskBizId(Long taskId, Long bizId) { return 1; }
        @Override public int updateReportProcessing(Long reportId, String status, int progress, String currentStage) { Report report = findReport(reportId); report.setStatus(status); report.setProgress(progress); return 1; }
        @Override public int updateReportSuccess(Long reportId, Long versionId, String status, int progress, String previewUrl) { if (failReportSuccessUpdate) return 0; Report report = findReport(reportId); report.setCurrentVersionId(versionId); report.setStatus(status); report.setProgress(progress); report.setPreviewUrl(previewUrl); return 1; }
        @Override public int updateReportFailed(Long reportId, String status, String errorMessage) { Report report = findReport(reportId); report.setStatus(status); report.setErrorMessage(errorMessage); return 1; }
        @Override public int updateTaskStatus(Long taskId, String status, String currentStage, String errorMessage) { return tasks.stream().filter(task -> taskId.equals(task.getId())).findFirst().map(task -> { task.setStatus(status); task.setCurrentStage(currentStage); return 1; }).orElse(0); }
        @Override public Optional<FileObjectRecord> findFileObjectById(Long fileId) { return fileObjects.stream().filter(file -> fileId.equals(file.getId())).findFirst(); }
        @Override public FileObjectRecord saveFileObject(FileObjectRecord fileObject) { fileObject.setId(nextFileId++); fileObjects.add(fileObject); return fileObject; }
        @Override public ReportVersion saveVersion(ReportVersion version) { version.setId(nextVersionId++); versions.add(version); return version; }
        @Override public int updateVersionWordFile(Long versionId, Long wordFileId, String contentHash) { return 1; }
        @Override public Optional<ReportConfig> findConfigById(Long configId) { return configs.stream().filter(config -> configId.equals(config.getId())).findFirst(); }
        @Override public Optional<Long> findCurrentWordFileId(Long reportId) { return versions.stream().filter(version -> reportId.equals(version.getReportId())).findFirst().map(ReportVersion::getWordFileId); }
        @Override public Optional<Report> findReportById(Long reportId) { return reports.stream().filter(report -> reportId.equals(report.getId())).findFirst(); }
        @Override public List<Report> findReportPage(Long projectId, List<Long> accessibleProjectIds, String reportType, String status, String keyword) { return reports; }

        private List<FileObjectRecord> generatedFiles() {
            return fileObjects.stream().filter(file -> "REPORT_OUTPUT".equals(file.getBizType())).toList();
        }

        private Report findReport(Long reportId) {
            return findReportById(reportId).orElseThrow();
        }
    }

    private class EmptyFileObjectRepository implements FileObjectRepository {
        @Override public FileObject insert(FileObject fileObject) { return fileObject; }
        @Override public Optional<FileObject> findById(Long id) { return Optional.empty(); }
        @Override public List<FileObject> findPage(com.xd.smartworksite.file.dto.FileQueryRequest request) { return List.of(); }
        @Override public int markDeleted(Long fileId, String status) { return 0; }
    }

    private class InMemoryFileParseRecordRepository implements FileParseRecordRepository {
        private final List<FileParseRecord> records = new ArrayList<>();

        @Override public FileParseRecord insert(FileParseRecord record) { records.add(record); return record; }
        @Override public Optional<FileParseRecord> findById(Long id) { return records.stream().filter(record -> id.equals(record.getId())).findFirst(); }
        @Override public Optional<FileParseRecord> findLatestByFileId(Long projectId, Long fileId) { return records.stream().filter(record -> projectId.equals(record.getProjectId()) && fileId.equals(record.getFileId())).findFirst(); }
        @Override public Optional<FileParseRecord> findReusable(Long projectId, Long fileId, String sourceFileHash, String resultFormat) { return Optional.empty(); }
        @Override public List<FileParseRecord> findByFileId(Long projectId, Long fileId) { return List.of(); }
        @Override public int updateRunning(Long id, String currentStage, int progress) { return 1; }
        @Override public int updateSucceeded(FileParseRecord record) { return 1; }
        @Override public int updateFailed(Long id, String currentStage, String errorMessage) { return 1; }
    }

    private static class EmptyProjectMemberMapper implements ProjectMemberMapper {
        @Override public List<ProjectMember> selectByProjectId(Long projectId) { return List.of(); }
        @Override public ProjectMember selectByProjectIdAndUserId(Long projectId, Long userId) { return null; }
        @Override public int countActiveMember(Long projectId, Long userId) { return 0; }
        @Override public int insert(ProjectMember member) { return 1; }
        @Override public int update(ProjectMember member) { return 1; }
        @Override public int deleteByProjectIdAndUserId(Long projectId, Long userId, Long operatorId) { return 1; }
        @Override public List<Long> selectProjectIdsByUserId(Long userId) { return List.of(); }
        @Override public List<ProjectMember> selectEnabledByUserId(Long userId) { return List.of(); }
    }

    private static class MutableProjectRepository implements ProjectRepository {
        private String status = "ENABLED";

        @Override public List<Project> findPage(String keyword, String status) { return List.of(); }
        @Override public List<Project> findPageByProjectIds(String keyword, String status, List<Long> projectIds) { return List.of(); }
        @Override public Optional<Project> findById(Long projectId) { Project project = new Project(); project.setId(projectId); project.setStatus(status); return Optional.of(project); }
        @Override public Optional<Project> findByProjectCode(String projectCode) { return Optional.empty(); }
        @Override public Project insert(Project project) { return project; }
        @Override public int update(Project project) { return 1; }
        @Override public int softDelete(Long projectId, Long updatedBy) { return 1; }
        @Override public int updateStatus(Long projectId, String status, Long updatedBy) { return 1; }
        @Override public int updateSettings(Long projectId, String settings, Long updatedBy) { return 1; }
        @Override public long countActiveMembers(Long projectId) { return 0; }
        @Override public long countKnowledgeBases(Long projectId) { return 0; }
        @Override public long countReports(Long projectId) { return 0; }
        @Override public long countDataSources(Long projectId) { return 0; }
        @Override public long countQaMessages(Long projectId) { return 0; }
        @Override public long countReviewRecords(Long projectId) { return 0; }
        @Override public long countOcrRecords(Long projectId) { return 0; }
        @Override public long sumFileStorageBytes(Long projectId) { return 0; }
    }

    private static class NoopTaskRepository implements TaskRepository {
        @Override public com.xd.smartworksite.task.domain.GenerateTask insertTask(com.xd.smartworksite.task.domain.GenerateTask task) { return task; }
        @Override public Optional<com.xd.smartworksite.task.domain.GenerateTask> findById(Long taskId) { return Optional.empty(); }
        @Override public List<com.xd.smartworksite.task.domain.GenerateTask> findPage(Long projectId, List<Long> accessibleProjectIds, String taskType, String status, LocalDateTime createdFrom, LocalDateTime createdTo) { return List.of(); }
        @Override public List<TaskStageLog> findStages(Long taskId) { return List.of(); }
        @Override public List<TaskStatusCount> countByStatus(Long projectId, List<Long> accessibleProjectIds) { return List.of(); }
        @Override public int markRetrying(Long taskId, String nextStatus, String currentStage, Long updatedBy) { return 0; }
        @Override public int cancelWaiting(Long taskId, Long updatedBy) { return 0; }
        @Override public int requestRunningCancel(Long taskId, Long updatedBy) { return 0; }
        @Override public int claimQueuedTask(Long taskId, String workerId, long leaseSeconds, String currentStage) { return 0; }
        @Override public int heartbeat(Long taskId, String workerId, long leaseSeconds) { return 0; }
        @Override public int completeSuccess(Long taskId, String workerId, String currentStage) { return 0; }
        @Override public int completeFailure(Long taskId, String workerId, String currentStage, String errorMessage) { return 0; }
        @Override public int completeCanceled(Long taskId, String workerId, String currentStage, String errorMessage) { return 0; }
        @Override public int insertStage(TaskStageLog log) { return 1; }
        @Override public int insertOutboxEvent(TaskOutboxEvent event) { return 1; }
        @Override public Optional<TaskOutboxEvent> findOutboxEvent(Long taskId, String eventType) { return Optional.empty(); }
        @Override public List<TaskOutboxEvent> findDueOutboxEvents(int limit) { return List.of(); }
        @Override public int markOutboxDelivered(Long eventId) { return 0; }
        @Override public int markOutboxFailed(Long eventId, String status, String errorMessage, long nextDeliverySeconds) { return 0; }
    }

    private static class NoopAiRepository implements AiRepository {
        @Override public int saveExternalCallLog(ExternalCallLog log) { return 1; }
        @Override public List<ExternalCallLog> queryExternalCallLogs(Long projectId, List<Long> accessibleProjectIds, String serviceName, String callType, String status) { return List.of(); }
        @Override public DataSourceRecord findEnabledDataSource(Long projectId, Long dataSourceId) { return null; }
    }
}
