package com.mine.safety.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.mine.safety.config.MinIOConfig;
import com.mine.safety.domain.ReportRecord;
import com.mine.safety.domain.ReportTemplate;
import com.mine.safety.domain.Sensor;
import com.mine.safety.dto.*;
import com.mine.safety.repository.ReportRecordRepository;
import com.mine.safety.repository.ReportTemplateRepository;
import com.mine.safety.repository.SensorRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportTemplateRepository templateRepository;
    private final ReportRecordRepository recordRepository;
    private final SensorRepository sensorRepository;
    private final HistoryAnalysisService historyAnalysisService;
    private final MinIOService minIOService;
    private final JavaMailSender mailSender;

    @Value("${app.report.mine-name:某某煤矿}")
    private String mineName;

    @Value("${app.report.email-from:noreply@mine-safety.com}")
    private String emailFrom;

    @Value("${app.report.email-enabled:false}")
    private boolean emailEnabled;

    private static final AtomicInteger REPORT_SEQ = new AtomicInteger(0);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter REPORT_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public List<ReportTemplateDTO> getReportTemplates(String templateType) {
        LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
        if (templateType != null) wrapper.eq(ReportTemplate::getTemplateType, templateType);
        wrapper.eq(ReportTemplate::getEnabled, true);
        return templateRepository.selectList(wrapper).stream()
                .map(this::convertTemplateToDTO).collect(Collectors.toList());
    }

    @Transactional
    public ReportRecordDTO generateReport(String templateCode, String startDate, String endDate,
                                           String zoneCode, String fileFormat, String generatedBy) {
        ReportTemplate template = templateRepository.selectOne(
                new LambdaQueryWrapper<ReportTemplate>().eq(ReportTemplate::getTemplateCode, templateCode));
        if (template == null) throw new RuntimeException("报表模板不存在: " + templateCode);

        ReportRecord record = new ReportRecord();
        record.setReportNo(generateReportNo());
        record.setTemplateId(template.getId());
        record.setTemplateCode(templateCode);
        record.setReportName(template.getTemplateName() + " " + startDate + "~" + endDate);
        record.setReportType(template.getTemplateType());
        record.setStartDate(LocalDate.parse(startDate, DATE_FMT));
        record.setEndDate(LocalDate.parse(endDate, DATE_FMT));
        record.setTimeDimension(template.getTimeDimension());
        record.setSensorTypes(template.getSensorTypes());
        record.setZoneCode(zoneCode);
        record.setFileFormat(fileFormat != null ? fileFormat : template.getFileFormat());
        record.setGeneratedBy(generatedBy);
        record.setGenerationSource("MANUAL");
        record.setStatus(0);
        record.setEmailSent(0);
        recordRepository.insert(record);

        try {
            ReportDataDTO reportData = collectReportData(template, startDate, endDate, zoneCode);
            record.setReportData(JSON.toJSONString(reportData));

            byte[] fileBytes;
            String contentType;
            String extension;
            if ("EXCEL".equals(record.getFileFormat())) {
                fileBytes = generateExcelReport(reportData, template);
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                extension = ".xlsx";
            } else {
                fileBytes = generatePdfReport(reportData, template);
                contentType = "application/pdf";
                extension = ".pdf";
            }

            String objectName = "reports/" + templateCode + "/" + record.getReportNo() + extension;
            String fileUrl = minIOService.uploadFile(objectName,
                    new ByteArrayInputStream(fileBytes), fileBytes.length, contentType);

            record.setFilePath(objectName);
            record.setFileSize((long) fileBytes.length);
            record.setFileUrl(fileUrl);
            record.setStatus(1);
            recordRepository.updateById(record);

            log.info("报表生成完成 - 编号: {}, 文件: {}", record.getReportNo(), objectName);
        } catch (Exception e) {
            log.error("报表生成失败 - 编号: {}", record.getReportNo(), e);
            record.setStatus(2);
            record.setErrorMessage(e.getMessage());
            recordRepository.updateById(record);
        }

        return convertRecordToDTO(record);
    }

    private ReportDataDTO collectReportData(ReportTemplate template, String startDate, String endDate, String zoneCode) {
        ReportDataDTO data = new ReportDataDTO();
        data.setStartDate(startDate);
        data.setEndDate(endDate);
        data.setTimeDimension(template.getTimeDimension());

        String[] sensorTypes = template.getSensorTypes().split(",");
        List<ReportDataDTO.SensorReportItem> items = new ArrayList<>();

        for (String sensorType : sensorTypes) {
            LambdaQueryWrapper<Sensor> wrapper = new LambdaQueryWrapper<Sensor>()
                    .eq(Sensor::getType, sensorType.trim());
            if (zoneCode != null) wrapper.eq(Sensor::getZoneCode, zoneCode);

            List<Sensor> sensors = sensorRepository.selectList(wrapper);
            for (Sensor sensor : sensors) {
                try {
                    HistoryStatisticsDTO stats = historyAnalysisService.getHistoryStatistics(
                            sensor.getSensorId(), startDate, endDate, template.getTimeDimension());

                    ReportDataDTO.SensorReportItem item = new ReportDataDTO.SensorReportItem();
                    item.setSensorId(sensor.getSensorId());
                    item.setSensorName(sensor.getName());
                    item.setLocation(sensor.getLocation());
                    item.setMaxValue(stats.getMaxValue());
                    item.setAvgValue(stats.getAvgValue());
                    item.setOverWarningCount(stats.getOverWarningCount());
                    item.setOverAlarmCount(stats.getOverAlarmCount());
                    item.setOverPowerOffCount(stats.getOverPowerOffCount());
                    item.setOverThresholdDurationMinutes(stats.getOverThresholdDurationMinutes());
                    item.setHourlyData(stats.getTimeSeries());
                    items.add(item);
                } catch (Exception e) {
                    log.warn("采集传感器报表数据失败 - 传感器: {}, 错误: {}", sensor.getSensorId(), e.getMessage());
                }
            }
        }

        data.setSensorItems(items);
        data.setTotalAlertCount(items.stream()
                .mapToLong(i -> i.getOverWarningCount() != null ? i.getOverWarningCount() : 0)
                .sum());
        data.setTotalOverThresholdDuration(items.stream()
                .map(i -> i.getOverThresholdDurationMinutes() != null ? i.getOverThresholdDurationMinutes() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        data.setReportName(template.getTemplateName());

        return data;
    }

    private byte[] generatePdfReport(ReportDataDTO data, ReportTemplate template) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        PdfFont font = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H", false);
        document.setFont(font);

        document.add(new Paragraph(mineName)
                .setFontSize(20).setBold());
        document.add(new Paragraph(template.getTemplateName())
                .setFontSize(16));
        document.add(new Paragraph("统计周期: " + data.getStartDate() + " ~ " + data.getEndDate())
                .setFontSize(10));
        document.add(new Paragraph("\n"));

        if (data.getSensorItems() != null && !data.getSensorItems().isEmpty()) {
            float[] colWidths = {1, 2, 2, 1, 1, 1, 1, 1};
            Table table = new Table(colWidths);
            table.addHeaderCell(new Cell().add(new Paragraph("传感器ID")));
            table.addHeaderCell(new Cell().add(new Paragraph("名称")));
            table.addHeaderCell(new Cell().add(new Paragraph("位置")));
            table.addHeaderCell(new Cell().add(new Paragraph("最大值")));
            table.addHeaderCell(new Cell().add(new Paragraph("平均值")));
            table.addHeaderCell(new Cell().add(new Paragraph("预警次数")));
            table.addHeaderCell(new Cell().add(new Paragraph("报警次数")));
            table.addHeaderCell(new Cell().add(new Paragraph("超标时长(min)")));

            for (ReportDataDTO.SensorReportItem item : data.getSensorItems()) {
                table.addCell(new Cell().add(new Paragraph(item.getSensorId())));
                table.addCell(new Cell().add(new Paragraph(item.getSensorName() != null ? item.getSensorName() : "")));
                table.addCell(new Cell().add(new Paragraph(item.getLocation() != null ? item.getLocation() : "")));
                table.addCell(new Cell().add(new Paragraph(item.getMaxValue() != null ? item.getMaxValue().toString() : "-")));
                table.addCell(new Cell().add(new Paragraph(item.getAvgValue() != null ? item.getAvgValue().toString() : "-")));
                table.addCell(new Cell().add(new Paragraph(item.getOverWarningCount() != null ? item.getOverWarningCount().toString() : "0")));
                table.addCell(new Cell().add(new Paragraph(item.getOverAlarmCount() != null ? item.getOverAlarmCount().toString() : "0")));
                table.addCell(new Cell().add(new Paragraph(item.getOverThresholdDurationMinutes() != null ? item.getOverThresholdDurationMinutes().toString() : "0")));
            }
            document.add(table);
        }

        document.add(new Paragraph("\n"));
        document.add(new Paragraph("报警总数: " + data.getTotalAlertCount()));
        document.add(new Paragraph("总超标时长: " + data.getTotalOverThresholdDuration() + " 分钟"));

        document.close();
        return baos.toByteArray();
    }

    private byte[] generateExcelReport(ReportDataDTO data, ReportTemplate template) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        List<List<String>> head = new ArrayList<>();
        head.add(Collections.singletonList("传感器ID"));
        head.add(Collections.singletonList("名称"));
        head.add(Collections.singletonList("位置"));
        head.add(Collections.singletonList("最大值"));
        head.add(Collections.singletonList("平均值"));
        head.add(Collections.singletonList("预警次数"));
        head.add(Collections.singletonList("报警次数"));
        head.add(Collections.singletonList("断电次数"));
        head.add(Collections.singletonList("超标时长(min)"));

        List<List<Object>> rows = new ArrayList<>();
        if (data.getSensorItems() != null) {
            for (ReportDataDTO.SensorReportItem item : data.getSensorItems()) {
                List<Object> row = new ArrayList<>();
                row.add(item.getSensorId());
                row.add(item.getSensorName());
                row.add(item.getLocation());
                row.add(item.getMaxValue() != null ? item.getMaxValue().toString() : "-");
                row.add(item.getAvgValue() != null ? item.getAvgValue().toString() : "-");
                row.add(item.getOverWarningCount() != null ? item.getOverWarningCount() : 0);
                row.add(item.getOverAlarmCount() != null ? item.getOverAlarmCount() : 0);
                row.add(item.getOverPowerOffCount() != null ? item.getOverPowerOffCount() : 0);
                row.add(item.getOverThresholdDurationMinutes() != null ? item.getOverThresholdDurationMinutes().toString() : "0");
                rows.add(row);
            }
        }

        EasyExcel.write(baos).head(head).sheet(template.getTemplateName()).doWrite(rows);
        return baos.toByteArray();
    }

    @Transactional
    public void sendReportByEmail(Long reportId, String recipients) {
        ReportRecord record = recordRepository.selectById(reportId);
        if (record == null) throw new RuntimeException("报表记录不存在: " + reportId);
        if (record.getStatus() != 1) throw new RuntimeException("报表未生成完成，无法推送");
        if (!emailEnabled) {
            log.info("邮件推送未启用，跳过 - 报表: {}", record.getReportNo());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(emailFrom);
            helper.setTo(recipients.split(","));
            helper.setSubject(record.getReportName());
            helper.setText("请查收附件中的监测报表。\n\n统计周期: " + record.getStartDate() + " ~ " + record.getEndDate());

            if (record.getFilePath() != null) {
                byte[] fileBytes;
                try (var is = minIOService.downloadFile(record.getFilePath())) {
                    fileBytes = is.readAllBytes();
                }
                String extension = "PDF".equals(record.getFileFormat()) ? ".pdf" : ".xlsx";
                helper.addAttachment(record.getReportNo() + extension,
                        new org.springframework.core.io.ByteArrayResource(fileBytes));
            }

            mailSender.send(message);
            record.setEmailSent(1);
            record.setEmailSentTime(LocalDateTime.now());
            record.setEmailRecipients(recipients);
            recordRepository.updateById(record);
            log.info("报表邮件推送成功 - 报表: {}, 接收人: {}", record.getReportNo(), recipients);
        } catch (Exception e) {
            log.error("报表邮件推送失败 - 报表: {}", record.getReportNo(), e);
            record.setEmailSent(2);
            recordRepository.updateById(record);
        }
    }

    public List<ReportRecordDTO> getReportRecords(String reportType, Integer status, String startDate, String endDate) {
        LambdaQueryWrapper<ReportRecord> wrapper = new LambdaQueryWrapper<>();
        if (reportType != null) wrapper.eq(ReportRecord::getReportType, reportType);
        if (status != null) wrapper.eq(ReportRecord::getStatus, status);
        if (startDate != null) wrapper.ge(ReportRecord::getStartDate, LocalDate.parse(startDate, DATE_FMT));
        if (endDate != null) wrapper.le(ReportRecord::getEndDate, LocalDate.parse(endDate, DATE_FMT));
        wrapper.orderByDesc(ReportRecord::getCreatedAt);
        return recordRepository.selectList(wrapper).stream()
                .map(this::convertRecordToDTO).collect(Collectors.toList());
    }

    public ReportRecordDTO getReportRecord(String reportNo) {
        ReportRecord record = recordRepository.selectOne(
                new LambdaQueryWrapper<ReportRecord>().eq(ReportRecord::getReportNo, reportNo));
        if (record == null) throw new RuntimeException("报表不存在: " + reportNo);
        return convertRecordToDTO(record);
    }

    public void generateScheduledReports() {
        List<ReportTemplate> templates = templateRepository.selectList(
                new LambdaQueryWrapper<ReportTemplate>().eq(ReportTemplate::getEnabled, true));

        LocalDate today = LocalDate.now();
        for (ReportTemplate template : templates) {
            try {
                String startDate;
                String endDate = today.minusDays(1).format(DATE_FMT);

                startDate = switch (template.getTimeDimension()) {
                    case "DAY" -> today.minusDays(1).format(DATE_FMT);
                    case "WEEK" -> today.minusDays(7).format(DATE_FMT);
                    case "MONTH" -> today.minusMonths(1).format(DATE_FMT);
                    default -> today.minusDays(1).format(DATE_FMT);
                };

                generateReport(template.getTemplateCode(), startDate, endDate, null, template.getFileFormat(), "SYSTEM");
            } catch (Exception e) {
                log.error("定时报表生成失败 - 模板: {}, 错误: {}", template.getTemplateCode(), e.getMessage());
            }
        }
    }

    private String generateReportNo() {
        return "RPT-" + LocalDateTime.now().format(REPORT_DATE_FMT)
                + "-" + String.format("%04d", REPORT_SEQ.incrementAndGet() % 10000);
    }

    private ReportTemplateDTO convertTemplateToDTO(ReportTemplate t) {
        ReportTemplateDTO dto = new ReportTemplateDTO();
        dto.setId(t.getId());
        dto.setTemplateCode(t.getTemplateCode());
        dto.setTemplateName(t.getTemplateName());
        dto.setTemplateType(t.getTemplateType());
        dto.setDescription(t.getDescription());
        dto.setSensorTypes(t.getSensorTypes());
        dto.setTimeDimension(t.getTimeDimension());
        dto.setContentTemplate(t.getContentTemplate());
        dto.setFileFormat(t.getFileFormat());
        dto.setEnabled(t.getEnabled());
        return dto;
    }

    private ReportRecordDTO convertRecordToDTO(ReportRecord r) {
        ReportRecordDTO dto = new ReportRecordDTO();
        dto.setId(r.getId());
        dto.setReportNo(r.getReportNo());
        dto.setTemplateId(r.getTemplateId());
        dto.setTemplateCode(r.getTemplateCode());
        dto.setReportName(r.getReportName());
        dto.setReportType(r.getReportType());
        dto.setStartDate(r.getStartDate() != null ? r.getStartDate().toString() : null);
        dto.setEndDate(r.getEndDate() != null ? r.getEndDate().toString() : null);
        dto.setTimeDimension(r.getTimeDimension());
        dto.setSensorTypes(r.getSensorTypes());
        dto.setZoneCode(r.getZoneCode());
        dto.setFileFormat(r.getFileFormat());
        dto.setFilePath(r.getFilePath());
        dto.setFileSize(r.getFileSize());
        dto.setFileUrl(r.getFileUrl());
        dto.setGeneratedBy(r.getGeneratedBy());
        dto.setGenerationSource(r.getGenerationSource());
        dto.setStatus(r.getStatus());
        dto.setErrorMessage(r.getErrorMessage());
        dto.setEmailSent(r.getEmailSent());
        dto.setEmailSentTime(r.getEmailSentTime() != null ? r.getEmailSentTime().toString() : null);
        dto.setEmailRecipients(r.getEmailRecipients());
        dto.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        return dto;
    }
}
