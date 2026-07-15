package com.xd.smartworksite.report.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.xd.smartworksite.ai.infra.AiProviderResponse;
import com.xd.smartworksite.ai.infra.AiPythonServiceClient;
import com.xd.smartworksite.ai.infra.AiPythonServiceProperties;
import com.xd.smartworksite.common.exception.BusinessException;
import com.xd.smartworksite.common.result.ErrorCode;
import com.xd.smartworksite.common.result.PageResult;
import com.xd.smartworksite.common.security.SecurityUtils;
import com.xd.smartworksite.file.application.FileParseApplicationService;
import com.xd.smartworksite.file.dto.FileParseContentResponse;
import com.xd.smartworksite.file.dto.FileParseRecordResponse;
import com.xd.smartworksite.file.infra.StorageAdapter;
import com.xd.smartworksite.file.infra.StorageObject;
import com.xd.smartworksite.project.application.ProjectAccessApplicationService;
import com.xd.smartworksite.report.domain.GenerateTask;
import com.xd.smartworksite.report.domain.Report;
import com.xd.smartworksite.report.domain.ReportConfig;
import com.xd.smartworksite.report.domain.ReportEngineType;
import com.xd.smartworksite.report.domain.ReportStatus;
import com.xd.smartworksite.report.domain.ReportVersion;
import com.xd.smartworksite.report.dto.ReportCreateRequest;
import com.xd.smartworksite.report.dto.ReportCreateResponse;
import com.xd.smartworksite.report.dto.ReportQueryRequest;
import com.xd.smartworksite.report.dto.ReportResponse;
import com.xd.smartworksite.report.infra.ReferenceDocumentPayload;
import com.xd.smartworksite.report.repository.ReportRepository;
import com.xd.smartworksite.task.application.TaskOutboxApplicationService;
import com.xd.smartworksite.template.domain.FileObjectRecord;
import com.xd.smartworksite.template.domain.Template;
import com.xd.smartworksite.template.domain.TemplateCategory;
import com.xd.smartworksite.template.domain.TemplateStatus;
import com.xd.smartworksite.template.repository.TemplateRepository;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ReportGenerationApplicationService {

    private static final String TASK_TYPE_REPORT_GENERATION = "REPORT_GENERATION";
    private static final String BIZ_TYPE_REPORT = "REPORT";
    private static final String TASK_STATUS_PENDING = "PENDING";
    private static final String TASK_STATUS_QUEUED = "QUEUED";
    private static final String TASK_STATUS_PROCESSING = "RUNNING";
    private static final String TASK_STAGE_CONFIG_VALIDATE = "CONFIG_VALIDATE";
    private static final String TASK_STAGE_MATERIAL_LOADING = "MATERIAL_LOADING";
    private static final String TASK_STAGE_AI_CONTENT_GENERATION = "AI_CONTENT_GENERATION";
    private static final String TASK_STAGE_TEMPLATE_RENDERING = "TEMPLATE_RENDERING";
    private static final String TASK_STAGE_STORING_RESULT = "STORING_RESULT";
    private static final String FILE_STATUS_ACTIVE = "ACTIVE";
    private static final int AI_PROMPT_MAX_CHARS = 60000;
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{\\s*([^}]+?)\\s*}|\\{\\{\\s*([^}]+?)\\s*}}");

    private final ReportRepository reportRepository;
    private final ProjectAccessApplicationService projectAccessApplicationService;
    private final TemplateRepository templateRepository;
    private final TaskOutboxApplicationService taskOutboxApplicationService;
    private final StorageAdapter storageAdapter;
    private final FileParseApplicationService fileParseApplicationService;
    private final AiPythonServiceClient aiPythonServiceClient;
    private final AiPythonServiceProperties aiPythonServiceProperties;
    private final ObjectMapper objectMapper;

    public ReportGenerationApplicationService(ReportRepository reportRepository,
                                              ProjectAccessApplicationService projectAccessApplicationService,
                                              TemplateRepository templateRepository,
                                              TaskOutboxApplicationService taskOutboxApplicationService,
                                              StorageAdapter storageAdapter,
                                              FileParseApplicationService fileParseApplicationService,
                                              AiPythonServiceClient aiPythonServiceClient,
                                              AiPythonServiceProperties aiPythonServiceProperties,
                                              ObjectMapper objectMapper) {
        this.reportRepository = reportRepository;
        this.projectAccessApplicationService = projectAccessApplicationService;
        this.templateRepository = templateRepository;
        this.taskOutboxApplicationService = taskOutboxApplicationService;
        this.storageAdapter = storageAdapter;
        this.fileParseApplicationService = fileParseApplicationService;
        this.aiPythonServiceClient = aiPythonServiceClient;
        this.aiPythonServiceProperties = aiPythonServiceProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReportCreateResponse createReport(ReportCreateRequest request) {
        projectAccessApplicationService.requireProjectWritableAccess(request.getProjectId());
        String reportType = normalizeRequired(request.getReportType(), "报告类型不能为空");
        Long templateId = request.getTemplateId();
        validateReportTemplate(templateId, request.getProjectId());

        String reportName = normalizeRequired(request.getReportName(), "报告名称不能为空");

        ReportConfig config = saveConfig(request, reportType, reportName, templateId);
        Report report = saveReport(request, reportType, reportName, templateId, config.getId());
        GenerateTask task = saveTask(request.getProjectId(), report.getId());
        requireUpdated(reportRepository.updateReportTask(report.getId(), task.getId()), "report task link update failed");
        requireUpdated(reportRepository.updateTaskBizId(task.getId(), report.getId()), "task biz id update failed");
        requireUpdated(reportRepository.updateTaskStatus(task.getId(), TASK_STATUS_QUEUED, TASK_STAGE_CONFIG_VALIDATE, null), "task queued status update failed");
        task.setStatus("QUEUED");
        taskOutboxApplicationService.enqueueTask(toSharedTask(task), "report created");

        return new ReportCreateResponse(report.getId(), task.getId(), ReportStatus.PENDING.name());
    }

    public PageResult<ReportResponse> queryReports(ReportQueryRequest request) {
        if (request.getProjectId() != null) {
            projectAccessApplicationService.requireProjectAccess(request.getProjectId());
        }
        List<Long> accessibleProjectIds = request.getProjectId() == null && !SecurityUtils.isPlatformAdmin()
                ? projectAccessApplicationService.currentUserAccessibleProjectIds()
                : null;
        if (request.getProjectId() == null && accessibleProjectIds != null && accessibleProjectIds.isEmpty()) {
            return new PageResult<>(request.getPageNo(), request.getPageSize(), 0, List.of());
        }
        Page<Report> page = PageHelper.startPage(request.getPageNo(), request.getPageSize())
                .doSelectPage(() -> reportRepository.findReportPage(
                        request.getProjectId(),
                        accessibleProjectIds,
                        trimToNull(request.getReportType()),
                        normalizeOptional(request.getStatus()),
                        trimToNull(request.getKeyword())
                ));
        List<ReportResponse> records = page.getResult().stream().map(this::toResponse).toList();
        return new PageResult<>(request.getPageNo(), request.getPageSize(), page.getTotal(), records);
    }

    public ReportResponse getReport(Long reportId) {
        Report report = reportRepository.findReportById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告不存在"));
        projectAccessApplicationService.requireProjectAccess(report.getProjectId());
        return toResponse(report);
    }

    public ReportCreateResponse regenerateReport(Long reportId) {
        Report report = reportRepository.findReportById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告不存在"));
        projectAccessApplicationService.requireProjectWritableAccess(report.getProjectId());
        ReportConfig config = reportRepository.findConfigById(report.getConfigId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告生成配置不存在"));
        ReportCreateRequest request = new ReportCreateRequest();
        request.setProjectId(report.getProjectId());
        request.setReportName(report.getReportName());
        request.setReportType(report.getReportType());
        request.setTemplateId(report.getTemplateId());
        request.setReferenceFileIds(parseLongList(config.getReferenceFileIds()));
        request.setKnowledgeBaseIds(parseLongList(config.getKnowledgeBaseIds()));
        request.setDataSourceIds(parseLongList(config.getDataSourceIds()));
        request.setVariables(parseObjectMap(config.getGenerationParams()));
        return createReport(request);
    }

    public void executeReportTask(Long reportId, Long taskId) {
        Report report = reportRepository.findReportById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告不存在"));
        projectAccessApplicationService.requireProjectWritableForSystem(report.getProjectId());
        ReportConfig config = reportRepository.findConfigById(report.getConfigId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告生成配置不存在"));
        GenerateTask task = new GenerateTask();
        task.setId(taskId);
        task.setProjectId(report.getProjectId());
        task.setBizType(BIZ_TYPE_REPORT);
        task.setBizId(reportId);
        try {
            executeJavaTemplateGeneration(report, config, task);
        } catch (BusinessException ex) {
            if (ErrorCode.CONFLICT.getCode() == ex.getCode()) {
                throw ex;
            }
            markReportFailed(report.getId(), ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            markReportFailed(report.getId(), ex.getMessage());
            throw ex;
        }
    }

    public String createDownloadUrl(Long reportId, String format) {
        Report report = reportRepository.findReportById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告不存在"));
        projectAccessApplicationService.requireProjectAccess(report.getProjectId());
        String normalizedFormat = format == null || format.isBlank() ? "WORD" : format.trim().toUpperCase(Locale.ROOT);
        if (!"WORD".equals(normalizedFormat)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "当前版本尚未生成PDF报告");
        }
        if (!ReportStatus.COMPLETED.name().equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "报告尚未生成成功");
        }
        return reportRepository.findCurrentWordFileId(reportId)
                .flatMap(reportRepository::findFileObjectById)
                .map(file -> storageAdapter.createAccessUrl(file.getObjectName(), Duration.ofMinutes(10)))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告文件不存在"));
    }

    private ReportConfig saveConfig(ReportCreateRequest request, String reportType, String reportName, Long templateId) {
        ReportConfig config = new ReportConfig();
        config.setProjectId(request.getProjectId());
        config.setConfigName(reportName);
        config.setReportType(reportType);
        config.setTemplateId(templateId);
        config.setReferenceFileIds(toJsonArray(request.getReferenceFileIds()));
        config.setKnowledgeBaseIds(toJsonArray(request.getKnowledgeBaseIds()));
        config.setDataSourceIds(toJsonArray(request.getDataSourceIds()));
        config.setGenerationParams(toJsonObject(request.getVariables()));
        config.setStatus("SUBMITTED");
        return reportRepository.saveConfig(config);
    }

    private Report saveReport(ReportCreateRequest request, String reportType, String reportName, Long templateId, Long configId) {
        Report report = new Report();
        report.setProjectId(request.getProjectId());
        report.setConfigId(configId);
        report.setReportName(reportName);
        report.setReportType(reportType);
        report.setTemplateId(templateId);
        report.setEngineType(ReportEngineType.JAVA_TEMPLATE_AI.name());
        report.setStatus(ReportStatus.PENDING.name());
        report.setProgress(0);
        return reportRepository.saveReport(report);
    }

    private GenerateTask saveTask(Long projectId, Long reportId) {
        GenerateTask task = new GenerateTask();
        task.setProjectId(projectId);
        task.setTaskType(TASK_TYPE_REPORT_GENERATION);
        task.setBizType(BIZ_TYPE_REPORT);
        task.setBizId(reportId);
        task.setStatus(TASK_STATUS_PENDING);
        task.setCurrentStage(TASK_STAGE_CONFIG_VALIDATE);
        task.setMaxRetryCount(3);
        return reportRepository.saveTask(task);
    }

    private com.xd.smartworksite.task.domain.GenerateTask toSharedTask(GenerateTask task) {
        com.xd.smartworksite.task.domain.GenerateTask sharedTask = new com.xd.smartworksite.task.domain.GenerateTask();
        sharedTask.setId(task.getId());
        sharedTask.setProjectId(task.getProjectId());
        sharedTask.setTaskType(task.getTaskType());
        sharedTask.setBizType(task.getBizType());
        sharedTask.setBizId(task.getBizId());
        sharedTask.setStatus(task.getStatus());
        return sharedTask;
    }

    private void executeJavaTemplateGeneration(Report report, ReportConfig config, GenerateTask task) {
        requireUpdated(reportRepository.updateReportProcessing(report.getId(), ReportStatus.PROCESSING.name(), 20, TASK_STAGE_MATERIAL_LOADING), "report processing status update failed");
        requireUpdated(reportRepository.updateTaskStatus(task.getId(), TASK_STATUS_PROCESSING, TASK_STAGE_MATERIAL_LOADING, null), "task processing status update failed");

        Template template = templateRepository.findById(report.getTemplateId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告模板不存在"));
        FileObjectRecord templateFile = templateRepository.findFileObjectById(template.getFileId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告模板文件不存在"));
        validateTemplateFile(report.getProjectId(), template, templateFile);

        List<ReferenceDocumentPayload> referenceDocuments = buildReferenceDocuments(config);
        validateReferenceDocuments(referenceDocuments);
        byte[] reportBytes = renderDocx(report, config, task.getId(), templateFile, referenceDocuments);

        requireUpdated(reportRepository.updateTaskStatus(task.getId(), TASK_STATUS_PROCESSING, TASK_STAGE_STORING_RESULT, null), "task storing status update failed");
        Long wordFileId = saveGeneratedWord(report, reportBytes);
        ReportVersion version = saveVersion(report, config, wordFileId, reportBytes);
        requireUpdated(reportRepository.updateReportSuccess(report.getId(), version.getId(), ReportStatus.COMPLETED.name(), 100, null), "report success status update failed");
    }

    private byte[] renderDocx(Report report, ReportConfig config, Long taskId, FileObjectRecord templateFile, List<ReferenceDocumentPayload> referenceDocuments) {
        requireUpdated(reportRepository.updateTaskStatus(taskId, TASK_STATUS_PROCESSING, TASK_STAGE_TEMPLATE_RENDERING, null), "task rendering status update failed");
        try (InputStream inputStream = storageAdapter.openObject(templateFile.getObjectName());
             XWPFDocument document = new XWPFDocument(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Set<String> variableNames = extractVariables(document);
            if (variableNames.isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "报告模板未包含占位符");
            }
            Map<String, String> variables = resolveVariables(report, config, variableNames, taskId, referenceDocuments, readDocumentText(document));
            replaceVariables(document, variables);
            document.write(outputStream);
            byte[] bytes = outputStream.toByteArray();
            if (bytes.length == 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成报告文件为空");
            }
            return bytes;
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报告模板渲染失败: " + ex.getMessage());
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报告模板文件读取失败: " + ex.getMessage());
        }
    }

    private Map<String, String> resolveVariables(Report report, ReportConfig config, Set<String> variableNames,
                                                 Long taskId,
                                                 List<ReferenceDocumentPayload> referenceDocuments,
                                                 String templateText) {
        Map<String, Object> configured = parseObjectMap(config.getGenerationParams());
        Map<String, String> result = new LinkedHashMap<>();
        for (String variableName : variableNames) {
            Object value = configured.get(variableName);
            if (value != null && !String.valueOf(value).isBlank() && !"referenceDocuments".equals(variableName)) {
                result.put(variableName, String.valueOf(value).trim());
            }
        }
        List<String> missing = variableNames.stream().filter(name -> !result.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            requireUpdated(reportRepository.updateTaskStatus(taskId, TASK_STATUS_PROCESSING, TASK_STAGE_AI_CONTENT_GENERATION, null), "task ai generation status update failed");
            Map<String, String> generated = generateMissingVariables(report, missing, referenceDocuments, templateText);
            result.putAll(generated);
        }
        List<String> stillMissing = variableNames.stream()
                .filter(name -> result.get(name) == null || result.get(name).isBlank())
                .toList();
        if (!stillMissing.isEmpty()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "报告变量生成失败，缺失变量: " + String.join(",", stillMissing));
        }
        return result;
    }

    private Map<String, String> generateMissingVariables(Report report, List<String> missing,
                                                         List<ReferenceDocumentPayload> referenceDocuments,
                                                         String templateText) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("projectId", report.getProjectId());
        payload.put("prompt", buildVariablePrompt(report, missing, referenceDocuments, templateText));
        payload.put("systemPrompt", "你是智慧工地报告生成助手。只能输出一个严格JSON对象，键为变量名，值为根据材料生成的中文报告内容。不要输出Markdown代码块或解释。");
        payload.put("parameters", Map.of("temperature", 0.2));
        AiProviderResponse response = aiPythonServiceClient.post(
                aiPythonServiceProperties.getPaths().getModelInvoke(),
                "REPORT_VARIABLE_GENERATION",
                report.getProjectId(),
                payload);
        Object answer = response.getData() == null ? null : response.getData().get("answer");
        if (answer == null || String.valueOf(answer).isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Python智能服务未返回报告变量内容");
        }
        Map<String, Object> raw;
        try {
            raw = objectMapper.readValue(String.valueOf(answer), new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Python智能服务返回的报告变量不是严格JSON对象");
        }
        Map<String, String> generated = new LinkedHashMap<>();
        for (String name : missing) {
            Object value = raw.get(name);
            if (value == null || String.valueOf(value).isBlank()) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "Python智能服务未生成报告变量: " + name);
            }
            generated.put(name, String.valueOf(value).trim());
        }
        return generated;
    }

    private String buildVariablePrompt(Report report, List<String> missing, List<ReferenceDocumentPayload> documents, String templateText) {
        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("reportName", report.getReportName());
        prompt.put("reportType", report.getReportType());
        prompt.put("missingVariables", missing);
        prompt.put("templateText", limit(templateText, 8000));
        prompt.put("referenceDocuments", documents.stream()
                .map(document -> Map.of(
                        "fileId", document.getFileId(),
                        "fileName", document.getFileName(),
                        "content", limit(document.getContent(), 18000)))
                .toList());
        String json = toJson(prompt);
        if (json.length() > AI_PROMPT_MAX_CHARS) {
            json = json.substring(0, AI_PROMPT_MAX_CHARS);
        }
        return "请根据以下报告模板变量和材料生成缺失变量。只返回严格JSON对象，不要输出其他文字。\n" + json;
    }

    private Set<String> extractVariables(XWPFDocument document) {
        Set<String> variables = new LinkedHashSet<>();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            collectVariables(paragraph.getText(), variables);
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    collectVariables(cell.getText(), variables);
                }
            }
        }
        return variables;
    }

    private void collectVariables(String text, Set<String> variables) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            if (value != null && !value.isBlank()) {
                variables.add(value.trim());
            }
        }
    }

    private String readDocumentText(XWPFDocument document) {
        StringBuilder builder = new StringBuilder();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            builder.append(paragraph.getText()).append('\n');
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    builder.append(cell.getText()).append('\n');
                }
            }
        }
        return builder.toString();
    }

    private void replaceVariables(XWPFDocument document, Map<String, String> variables) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            replaceParagraph(paragraph, variables);
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        replaceParagraph(paragraph, variables);
                    }
                }
            }
        }
    }

    private void replaceParagraph(XWPFParagraph paragraph, Map<String, String> variables) {
        String text = paragraph.getText();
        if (text == null || !VARIABLE_PATTERN.matcher(text).find()) {
            return;
        }
        String replaced = replaceText(text, variables);
        int runCount = paragraph.getRuns().size();
        for (int i = runCount - 1; i >= 0; i--) {
            paragraph.removeRun(i);
        }
        XWPFRun run = paragraph.createRun();
        run.setText(replaced);
    }

    private String replaceText(String text, Map<String, String> variables) {
        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1) == null ? matcher.group(2) : matcher.group(1);
            String value = variables.get(key.trim());
            if (value == null || value.isBlank()) {
                throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "报告变量未生成: " + key.trim());
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private void validateTemplateFile(Long projectId, Template template, FileObjectRecord fileObject) {
        if (!projectId.equals(template.getProjectId()) || !projectId.equals(fileObject.getProjectId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "报告模板不属于当前项目");
        }
        if (!TemplateCategory.REPORT.name().equals(template.getTemplateCategory())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择报告模板");
        }
        if (!TemplateStatus.ENABLED.name().equals(template.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "报告模板未启用");
        }
        String filename = fileObject.getFileName() == null ? "" : fileObject.getFileName().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".docx")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "报告生成仅支持DOCX模板");
        }
    }

    private List<ReferenceDocumentPayload> buildReferenceDocuments(ReportConfig config) {
        List<Long> fileIds = parseLongList(config.getReferenceFileIds());
        if (fileIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "报告生成至少需要一个参考材料文件");
        }
        return fileIds.stream()
                .map(fileId -> loadReferenceDocument(fileId, config.getProjectId()))
                .toList();
    }

    private void validateReferenceDocuments(List<ReferenceDocumentPayload> referenceDocuments) {
        if (referenceDocuments == null || referenceDocuments.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "报告生成至少需要一个参考材料文件");
        }
        for (ReferenceDocumentPayload document : referenceDocuments) {
            if (document.getFileName() == null || document.getFileName().isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "参考材料fileName不能为空");
            }
            if (document.getContent() == null || document.getContent().isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "参考材料内容为空: " + document.getFileName());
            }
        }
    }

    private ReferenceDocumentPayload loadReferenceDocument(Long fileId, Long projectId) {
        FileObjectRecord file = reportRepository.findFileObjectById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "参考材料不存在: " + fileId));
        if (!projectId.equals(file.getProjectId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "参考材料不属于当前项目: " + fileId);
        }
        if (!FILE_STATUS_ACTIVE.equals(file.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "参考材料不是可用状态: " + file.getFileName());
        }
        String content = isTextFile(file) ? readTextFile(file) : readLatestParseContent(file);
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "参考材料内容为空: " + file.getFileName());
        }
        return new ReferenceDocumentPayload(String.valueOf(file.getId()), file.getFileName(), content);
    }

    private String readTextFile(FileObjectRecord file) {
        try (InputStream inputStream = storageAdapter.openObject(file.getObjectName())) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "读取参考材料失败: " + file.getFileName());
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "读取参考材料对象失败: " + file.getFileName());
        }
    }

    private String readLatestParseContent(FileObjectRecord file) {
        FileParseRecordResponse record = fileParseApplicationService.getLatestFileParseRecordForSystem(file.getId(), file.getProjectId());
        if (!"SUCCESS".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "参考材料解析未成功: " + file.getFileName());
        }
        FileParseContentResponse content = fileParseApplicationService.getParseContentForSystem(record.getRecordId());
        if (content.getContent() == null || content.getContent().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "参考材料解析内容为空: " + file.getFileName());
        }
        return content.getContent();
    }

    private boolean isTextFile(FileObjectRecord file) {
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/")) {
            return true;
        }
        String name = file.getFileName() == null ? "" : file.getFileName().toLowerCase(Locale.ROOT);
        return name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".json") || name.endsWith(".csv");
    }

    private Long saveGeneratedWord(Report report, byte[] bytes) {
        String objectName = "reports/project-" + report.getProjectId() + "/report-" + report.getId() + "/"
                + LocalDate.now() + "/" + UUID.randomUUID() + ".docx";
        StorageObject object = storageAdapter.upload(objectName, new ByteArrayInputStream(bytes), bytes.length,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        FileObjectRecord fileObject = new FileObjectRecord();
        fileObject.setProjectId(report.getProjectId());
        fileObject.setBizType("REPORT_OUTPUT");
        fileObject.setBizId(report.getId());
        fileObject.setFileName(report.getReportName() + ".docx");
        fileObject.setObjectName(object.getObjectName());
        fileObject.setContentType(object.getContentType());
        fileObject.setFileSize(object.getSize());
        fileObject.setStatus("ACTIVE");
        fileObject.setMetadata("{}");
        reportRepository.saveFileObject(fileObject);
        if (fileObject.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "report file object id was not generated");
        }
        return fileObject.getId();
    }

    private ReportVersion saveVersion(Report report, ReportConfig config, Long wordFileId, byte[] reportBytes) {
        ReportVersion version = new ReportVersion();
        version.setProjectId(report.getProjectId());
        version.setReportId(report.getId());
        version.setVersionNo(1);
        version.setWordFileId(wordFileId);
        Map<String, Object> sourceSnapshot = new LinkedHashMap<>();
        sourceSnapshot.put("templateId", config.getTemplateId());
        sourceSnapshot.put("templateEngine", ReportEngineType.JAVA_TEMPLATE_AI.name());
        sourceSnapshot.put("referenceFileIds", parseLongList(config.getReferenceFileIds()));
        sourceSnapshot.put("engineType", ReportEngineType.JAVA_TEMPLATE_AI.name());
        version.setSourceSnapshot(toJson(sourceSnapshot));
        version.setEngineResponse("{}");
        version.setContentHash(sha256(reportBytes));
        version.setStatus("SUCCESS");
        reportRepository.saveVersion(version);
        if (version.getId() == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "report version id was not generated");
        }
        return version;
    }

    private void markReportFailed(Long reportId, String message) {
        String errorMessage = message == null || message.isBlank() ? "报告生成失败" : message;
        requireUpdated(reportRepository.updateReportFailed(reportId, ReportStatus.FAILED.name(), errorMessage), "report failed status update failed");
    }

    private void requireUpdated(int updated, String message) {
        if (updated <= 0) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }

    private void validateReportTemplate(Long templateId, Long projectId) {
        if (templateId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "报告模板不能为空");
        }
        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "报告模板不存在"));
        if (!projectId.equals(template.getProjectId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "报告模板不属于当前项目");
        }
        if (!TemplateCategory.REPORT.name().equals(template.getTemplateCategory())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择报告模板");
        }
        if (!TemplateStatus.ENABLED.name().equals(template.getStatus())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "报告模板未启用");
        }
    }

    private List<Long> parseLongList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报告配置解析失败");
        }
    }

    private Map<String, Object> parseObjectMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "报告配置解析失败");
        }
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            return ReportStatus.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "status must be DRAFT, PENDING, PROCESSING, COMPLETED, FAILED, ARCHIVED or DELETED");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String toJsonArray(List<Long> value) {
        return toJson(value == null ? List.of() : value);
    }

    private String toJsonObject(Object value) {
        return toJson(value == null ? java.util.Map.of() : value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "JSON序列化失败");
        }
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "SHA-256算法不可用");
        }
    }

    private String limit(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private ReportResponse toResponse(Report report) {
        ReportResponse response = new ReportResponse();
        response.setId(report.getId());
        response.setReportId(report.getId());
        response.setProjectId(report.getProjectId());
        response.setTaskId(report.getTaskId());
        response.setReportName(report.getReportName());
        response.setReportType(report.getReportType());
        response.setTemplateId(report.getTemplateId());
        response.setEngineType(report.getEngineType());
        response.setVersion(report.getCurrentVersionId() == null ? "v0" : "v" + report.getCurrentVersionId());
        response.setStatus(report.getStatus());
        response.setProgress(report.getProgress());
        response.setPreviewUrl(report.getPreviewUrl());
        response.setErrorMessage(report.getErrorMessage());
        response.setCreatedBy("admin");
        response.setCreatedAt(report.getCreatedAt());
        response.setUpdatedAt(report.getUpdatedAt());
        return response;
    }
}
