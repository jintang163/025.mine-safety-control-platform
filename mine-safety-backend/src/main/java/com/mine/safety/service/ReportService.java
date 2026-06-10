package com.mine.safety.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.pentaho.reporting.engine.classic.core.*;
import org.pentaho.reporting.engine.classic.core.modules.output.pageable.pdf.PdfReportUtil;
import org.pentaho.reporting.engine.classic.core.modules.output.table.xls.ExcelReportUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.*;
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

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final AtomicInteger REPORT_SEQ = new AtomicInteger(0);

    public List<ReportTemplateDTO> getReportTemplates(String templateType) {
        LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
        if (templateType != null && !templateType.isBlank()) {
            wrapper.eq(ReportTemplate::getTemplateType, templateType);
        }
        wrapper.eq(ReportTemplate::getEnabled, true).orderByAsc(ReportTemplate::getTemplateCode);
        return templateRepository.selectList(wrapper).stream()
                .map(this::convertTemplateToDTO).collect(Collectors.toList());
    }

    @Transactional
    public ReportRecordDTO generateReport(String templateCode, String startDate, String endDate,
                                          String zoneCode, String fileFormat, String generatedBy) {
        ReportTemplate template = templateRepository.selectOne(
                new LambdaQueryWrapper<ReportTemplate>().eq(ReportTemplate::getTemplateCode, templateCode));
        if (template == null) {
            throw new RuntimeException("报表模板不存在: " + templateCode);
        }

        ReportRecord record = initReportRecord(template, startDate, endDate, zoneCode, fileFormat, generatedBy);
        record.setStatus(0);
        recordRepository.insert(record);

        try {
            ReportDataDTO data = buildReportData(template, startDate, endDate, zoneCode);
            record.setReportData(JSON.toJSONString(data));

            byte[] fileBytes = generateReportByFormat(data, template, fileFormat);
            String fileName = buildFileName(template, startDate, endDate, fileFormat);

            String fileKey = "reports/" + LocalDate.now().format(DATE_FMT) + "/" + fileName;
            String fileUrl = minIOService.uploadFile(fileKey,
                    new ByteArrayInputStream(fileBytes), fileBytes.length,
                    "PDF".equalsIgnoreCase(fileFormat) ? "application/pdf" : "application/vnd.ms-excel");

            record.setFilePath(fileKey);
            record.setFileSize((long) fileBytes.length);
            record.setFileUrl(fileUrl);
            record.setStatus(1);
            recordRepository.updateById(record);

            log.info("报表生成成功 - 编号: {}, 模板: {}, 文件: {}", record.getReportNo(), templateCode, fileName);
            return convertRecordToDTO(record);
        } catch (Exception e) {
            log.error("报表生成失败 - 模板: {}, 错误: {}", templateCode, e.getMessage(), e);
            record.setStatus(2);
            record.setErrorMessage(e.getMessage());
            recordRepository.updateById(record);
            throw new RuntimeException("报表生成失败: " + e.getMessage(), e);
        }
    }

    private byte[] generateReportByFormat(ReportDataDTO data, ReportTemplate template, String format) throws Exception {
        TableModel tableModel = buildTableModel(data, template);
        Map<String, Object> params = buildReportParams(data, template);

        String templatePath = resolveTemplatePath(template);
        org.pentaho.reporting.engine.classic.core.MasterReport report =
                new org.pentaho.reporting.engine.classic.core.MasterReport();
        report.setName(template.getTemplateName());
        report.setPageDefinition(new org.pentaho.reporting.libraries.formatting.FastDecimalFormat("A4") != null
                ? org.pentaho.reporting.engine.classic.core.PageSize.A4 : org.pentaho.reporting.engine.classic.core.PageSize.A4);

        report.setDataFactory(new TableDataFactory("default", tableModel));
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            report.getParameterValues().put(entry.getKey(), entry.getValue());
        }
        report.getParameterValues().putAll(params);

        org.pentaho.reporting.engine.classic.core.Band reportHeader = new org.pentaho.reporting.engine.classic.core.Band();
        reportHeader.setName("report-header");
        org.pentaho.reporting.engine.classic.core.LabelElement title = new org.pentaho.reporting.engine.classic.core.LabelElement();
        title.setText(template.getTemplateName());
        title.setName("title-label");
        reportHeader.addElement(title);
        report.setReportHeader(reportHeader);

        org.pentaho.reporting.engine.classic.core.ItemBand itemBand = new org.pentaho.reporting.engine.classic.core.ItemBand();
        itemBand.setName("item-band");
        report.setItemBand(itemBand);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if ("EXCEL".equalsIgnoreCase(format)) {
                org.pentaho.reporting.engine.classic.core.modules.output.table.xls.ExcelReportUtil.createXLS(report, out);
            } else {
                org.pentaho.reporting.engine.classic.core.modules.output.pageable.pdf.PdfReportUtil.createPDF(report, out);
            }
        } catch (Exception e) {
            log.warn("JFreeReport渲染失败，使用备用方案生成: {}", e.getMessage());
            return generateFallbackReport(data, template, format);
        }
        if (out.size() < 100) {
            return generateFallbackReport(data, template, format);
        }
        return out.toByteArray();
    }

    private byte[] generateFallbackReport(ReportDataDTO data, ReportTemplate template, String format) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if ("EXCEL".equalsIgnoreCase(format)) {
            org.apache.poi.ss.usermodel.Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet(template.getTemplateName());

            int rowNum = 0;
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowNum++);
            org.apache.poi.ss.usermodel.Cell titleCell = headerRow.createCell(0);
            titleCell.setCellValue(template.getTemplateName() + " " + mineName);
            org.apache.poi.ss.usermodel.CellStyle titleStyle = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleStyle.setFont(titleFont);
            titleCell.setCellStyle(titleStyle);

            rowNum++;
            org.apache.poi.ss.usermodel.Row infoRow1 = sheet.createRow(rowNum++);
            infoRow1.createCell(0).setCellValue("报表编号: " + data.getReportNo());
            infoRow1.createCell(3).setCellValue("统计周期: " + data.getStartDate() + " 至 " + data.getEndDate());

            rowNum++;
            org.apache.poi.ss.usermodel.Row colHeader = sheet.createRow(rowNum++);
            String[] cols = {"区域", "传感器编号", "传感器名称", "平均值", "最大值", "最小值",
                    "预警次数", "报警次数", "断电次数", "超标分钟", "数据条数"};
            for (int i = 0; i < cols.length; i++) {
                org.apache.poi.ss.usermodel.Cell c = colHeader.createCell(i);
                c.setCellValue(cols[i]);
                org.apache.poi.ss.usermodel.CellStyle s = wb.createCellStyle();
                org.apache.poi.ss.usermodel.Font f = wb.createFont();
                f.setBold(true);
                s.setFont(f);
                c.setCellStyle(s);
            }

            if (data.getSensorItems() != null) {
                for (ReportDataDTO.SensorReportItem item : data.getSensorItems()) {
                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(item.getZoneCode() != null ? item.getZoneCode() : "");
                    row.createCell(1).setCellValue(item.getSensorId() != null ? item.getSensorId() : "");
                    row.createCell(2).setCellValue(item.getSensorName() != null ? item.getSensorName() : "");
                    row.createCell(3).setCellValue(item.getAvgValue() != null ? item.getAvgValue().doubleValue() : 0);
                    row.createCell(4).setCellValue(item.getMaxValue() != null ? item.getMaxValue().doubleValue() : 0);
                    row.createCell(5).setCellValue(item.getMinValue() != null ? item.getMinValue().doubleValue() : 0);
                    row.createCell(6).setCellValue(item.getOverWarningCount() != null ? item.getOverWarningCount() : 0L);
                    row.createCell(7).setCellValue(item.getOverAlarmCount() != null ? item.getOverAlarmCount() : 0L);
                    row.createCell(8).setCellValue(item.getOverPowerOffCount() != null ? item.getOverPowerOffCount() : 0L);
                    row.createCell(9).setCellValue(item.getOverThresholdDurationMinutes() != null ? item.getOverThresholdDurationMinutes().doubleValue() : 0);
                    row.createCell(10).setCellValue(item.getDataCount() != null ? item.getDataCount() : 0L);
                }
            }

            rowNum++;
            org.apache.poi.ss.usermodel.Row sumRow = sheet.createRow(rowNum++);
            sumRow.createCell(0).setCellValue("汇总");
            sumRow.createCell(3).setCellValue(data.getAvgValueAll() != null ? data.getAvgValueAll().doubleValue() : 0);
            sumRow.createCell(4).setCellValue(data.getMaxValueAll() != null ? data.getMaxValueAll().doubleValue() : 0);
            sumRow.createCell(6).setCellValue(data.getAlertTotal() != null ? data.getAlertTotal() : 0L);
            sumRow.createCell(9).setCellValue(data.getOverThresholdMinutesTotal() != null ? data.getOverThresholdMinutesTotal().doubleValue() : 0);

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }

            wb.write(out);
            wb.close();
        } else {
            com.itextpdf.kernel.pdf.PdfWriter writer = new com.itextpdf.kernel.pdf.PdfWriter(out);
            com.itextpdf.kernel.pdf.PdfDocument pdfDoc = new com.itextpdf.kernel.pdf.PdfDocument(writer);
            com.itextpdf.layout.Document doc = new com.itextpdf.layout.Document(pdfDoc);

            com.itextpdf.kernel.font.PdfFont font = com.itextpdf.kernel.font.PdfFontFactory.createFont(
                    "STSong-Light", "UniGB-UCS2-H", com.itextpdf.kernel.font.PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
            doc.setFont(font);

            com.itextpdf.layout.element.Paragraph title = new com.itextpdf.layout.element.Paragraph(
                    mineName + " - " + template.getTemplateName())
                    .setFont(font).setFontSize(18).setBold()
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER);
            doc.add(title);

            doc.add(new com.itextpdf.layout.element.Paragraph("报表编号: " + data.getReportNo()).setFont(font).setFontSize(10));
            doc.add(new com.itextpdf.layout.element.Paragraph("统计周期: " + data.getStartDate() + " 至 " + data.getEndDate()
                    + "    生成时间: " + data.getGeneratedAt()).setFont(font).setFontSize(10));

            float[] colWidths = {60, 80, 100, 60, 60, 60, 60, 60, 60, 60, 60};
            com.itextpdf.layout.element.Table table = new com.itextpdf.layout.element.Table(
                    com.itextpdf.layout.properties.UnitValue.createPercentArray(colWidths)).useAllAvailableWidth();
            table.setFont(font).setFontSize(9);

            String[] colHeaders = {"区域", "编号", "名称", "平均值", "最大值", "最小值",
                    "预警次", "报警次", "断电次", "超标分", "数据量"};
            for (String h : colHeaders) {
                com.itextpdf.layout.element.Cell cell = new com.itextpdf.layout.element.Cell()
                        .add(new com.itextpdf.layout.element.Paragraph(h).setBold()).setFont(font);
                table.addHeaderCell(cell);
            }

            if (data.getSensorItems() != null) {
                for (ReportDataDTO.SensorReportItem item : data.getSensorItems()) {
                    table.addCell(item.getZoneCode() != null ? item.getZoneCode() : "");
                    table.addCell(item.getSensorId() != null ? item.getSensorId() : "");
                    table.addCell(item.getSensorName() != null ? item.getSensorName() : "");
                    table.addCell(item.getAvgValue() != null ? item.getAvgValue().toPlainString() : "0");
                    table.addCell(item.getMaxValue() != null ? item.getMaxValue().toPlainString() : "0");
                    table.addCell(item.getMinValue() != null ? item.getMinValue().toPlainString() : "0");
                    table.addCell(String.valueOf(item.getOverWarningCount() != null ? item.getOverWarningCount() : 0L));
                    table.addCell(String.valueOf(item.getOverAlarmCount() != null ? item.getOverAlarmCount() : 0L));
                    table.addCell(String.valueOf(item.getOverPowerOffCount() != null ? item.getOverPowerOffCount() : 0L));
                    table.addCell(item.getOverThresholdDurationMinutes() != null ? item.getOverThresholdDurationMinutes().toPlainString() : "0");
                    table.addCell(String.valueOf(item.getDataCount() != null ? item.getDataCount() : 0L));
                }
            }

            com.itextpdf.layout.element.Cell sumCell = new com.itextpdf.layout.element.Cell(1, 3)
                    .add(new com.itextpdf.layout.element.Paragraph("汇总").setBold()).setFont(font);
            table.addCell(sumCell);
            table.addCell(data.getAvgValueAll() != null ? data.getAvgValueAll().toPlainString() : "0");
            table.addCell(data.getMaxValueAll() != null ? data.getMaxValueAll().toPlainString() : "0");
            table.addCell("");
            table.addCell(String.valueOf(data.getAlertTotal() != null ? data.getAlertTotal() : 0L));
            table.addCell("");
            table.addCell("");
            table.addCell(data.getOverThresholdMinutesTotal() != null ? data.getOverThresholdMinutesTotal().toPlainString() : "0");
            table.addCell("");

            doc.add(table);
            doc.add(new com.itextpdf.layout.element.Paragraph("\n本报表由系统自动生成，如有疑问请联系安全管理部门。")
                    .setFont(font).setFontSize(8)
                    .setTextAlignment(com.itextpdf.layout.properties.TextAlignment.CENTER)
                    .setItalic());
            doc.close();
        }
        return out.toByteArray();
    }

    private String resolveTemplatePath(ReportTemplate template) {
        return switch (template.getTemplateCode()) {
            case "GAS_DAILY" -> "reports/gas-daily-report.prpt";
            case "DUST_WEEKLY" -> "reports/dust-weekly-report.prpt";
            case "MONTHLY_SUMMARY" -> "reports/monthly-summary-report.prpt";
            default -> "reports/gas-daily-report.prpt";
        };
    }

    private TableModel buildTableModel(ReportDataDTO data, ReportTemplate template) {
        TypedTableModel model = new TypedTableModel();
        model.addColumn("sensorId", String.class);
        model.addColumn("sensorName", String.class);
        model.addColumn("location", String.class);
        model.addColumn("zoneCode", String.class);
        model.addColumn("avgValue", BigDecimal.class);
        model.addColumn("maxValue", BigDecimal.class);
        model.addColumn("minValue", BigDecimal.class);
        model.addColumn("overWarningCount", Long.class);
        model.addColumn("overAlarmCount", Long.class);
        model.addColumn("overPowerOffCount", Long.class);
        model.addColumn("overThresholdDurationMinutes", BigDecimal.class);
        model.addColumn("dataCount", Long.class);

        if (data.getSensorItems() != null) {
            for (ReportDataDTO.SensorReportItem item : data.getSensorItems()) {
                model.addRow(
                        item.getSensorId(),
                        item.getSensorName(),
                        item.getLocation(),
                        item.getZoneCode(),
                        item.getAvgValue(),
                        item.getMaxValue(),
                        item.getMinValue(),
                        item.getOverWarningCount(),
                        item.getOverAlarmCount(),
                        item.getOverPowerOffCount(),
                        item.getOverThresholdDurationMinutes(),
                        item.getDataCount()
                );
            }
        }
        return model;
    }

    private Map<String, Object> buildReportParams(ReportDataDTO data, ReportTemplate template) {
        Map<String, Object> params = new HashMap<>();
        params.put("mineName", mineName);
        params.put("reportName", data.getReportName());
        params.put("reportNo", data.getReportNo());
        params.put("startDate", data.getStartDate());
        params.put("endDate", data.getEndDate());
        params.put("generatedAt", LocalDateTime.now().format(DATETIME_FMT));
        params.put("zoneCode", data.getZoneCode() != null ? data.getZoneCode() : "全矿");

        params.put("sensorTotal", data.getSensorTotal());
        params.put("alertTotal", data.getAlertTotal());
        params.put("avgValueAll", data.getAvgValueAll());
        params.put("maxValueAll", data.getMaxValueAll());
        params.put("overThresholdMinutesTotal", data.getOverThresholdMinutesTotal());

        try {
            params.put("trendChart", generateTrendChartImage(data));
        } catch (Exception e) {
            log.warn("生成趋势图表失败: {}", e.getMessage());
        }
        return params;
    }

    private byte[] generateTrendChartImage(ReportDataDTO data) throws IOException {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        if (data.getSensorItems() != null) {
            int limit = Math.min(data.getSensorItems().size(), 10);
            for (int i = 0; i < limit; i++) {
                ReportDataDTO.SensorReportItem item = data.getSensorItems().get(i);
                if (item.getAvgValue() != null) {
                    dataset.addValue(item.getAvgValue(), "平均值", item.getSensorName());
                }
                if (item.getMaxValue() != null) {
                    dataset.addValue(item.getMaxValue(), "最大值", item.getSensorName());
                }
            }
        }
        JFreeChart chart = ChartFactory.createBarChart(
                "浓度趋势对比", "传感器", "浓度",
                dataset, PlotOrientation.VERTICAL, true, true, false);
        BufferedImage image = chart.createBufferedImage(800, 400);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ChartUtils.writeBufferedImageAsPNG(out, image);
        return out.toByteArray();
    }

    private ReportRecord initReportRecord(ReportTemplate template, String startDate, String endDate,
                                          String zoneCode, String fileFormat, String generatedBy) {
        ReportRecord record = new ReportRecord();
        record.setReportNo(generateReportNo());
        record.setTemplateId(template.getId());
        record.setTemplateCode(template.getTemplateCode());
        record.setReportName(template.getTemplateName() + "-" + startDate + "至" + endDate);
        record.setReportType(template.getTemplateType());
        record.setStartDate(LocalDate.parse(startDate, DATE_FMT));
        record.setEndDate(LocalDate.parse(endDate, DATE_FMT));
        record.setTimeDimension(template.getTimeDimension());
        record.setSensorTypes(template.getSensorTypes());
        record.setZoneCode(zoneCode);
        record.setFileFormat(fileFormat != null ? fileFormat : template.getFileFormat());
        record.setGeneratedBy(generatedBy != null ? generatedBy : "SYSTEM");
        record.setGenerationSource("MANUAL");
        record.setStatus(0);
        record.setEmailSent(0);
        return record;
    }

    private ReportDataDTO buildReportData(ReportTemplate template, String startDate, String endDate, String zoneCode) {
        ReportDataDTO dto = new ReportDataDTO();
        dto.setReportNo(generateReportNo());
        dto.setReportName(template.getTemplateName());
        dto.setStartDate(startDate);
        dto.setEndDate(endDate);
        dto.setZoneCode(zoneCode);
        dto.setTemplateCode(template.getTemplateCode());
        dto.setTemplateType(template.getTemplateType());
        dto.setMineName(mineName);
        dto.setGeneratedAt(LocalDateTime.now().format(DATETIME_FMT));

        List<String> sensorTypes = Arrays.asList(template.getSensorTypes().split(","));
        List<ReportDataDTO.SensorReportItem> items = new ArrayList<>();

        for (String sensorType : sensorTypes) {
            LambdaQueryWrapper<Sensor> wrapper = new LambdaQueryWrapper<Sensor>()
                    .eq(Sensor::getType, sensorType.trim());
            if (zoneCode != null && !zoneCode.isBlank()) {
                wrapper.eq(Sensor::getZoneCode, zoneCode);
            }
            List<Sensor> sensors = sensorRepository.selectList(wrapper);
            for (Sensor sensor : sensors) {
                try {
                    HistoryStatisticsDTO stat = historyAnalysisService.getHistoryStatistics(
                            sensor.getSensorId(), startDate, endDate, template.getTimeDimension());
                    items.add(convertToSensorItem(stat));
                } catch (Exception e) {
                    log.warn("获取传感器统计数据失败 - sensorId: {}", sensor.getSensorId());
                }
            }
        }

        dto.setSensorItems(items);
        dto.setSensorTotal(items.size());
        dto.setAlertTotal(items.stream()
                .mapToLong(i -> i.getOverWarningCount() != null ? i.getOverWarningCount() : 0L).sum());
        BigDecimal sumAvg = items.stream()
                .map(ReportDataDTO.SensorReportItem::getAvgValue)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setAvgValueAll(items.isEmpty() ? BigDecimal.ZERO :
                sumAvg.divide(BigDecimal.valueOf(items.size()), 4, BigDecimal.ROUND_HALF_UP));
        dto.setMaxValueAll(items.stream()
                .map(ReportDataDTO.SensorReportItem::getMaxValue)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
        dto.setOverThresholdMinutesTotal(items.stream()
                .map(ReportDataDTO.SensorReportItem::getOverThresholdDurationMinutes)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return dto;
    }

    private ReportDataDTO.SensorReportItem convertToSensorItem(HistoryStatisticsDTO stat) {
        ReportDataDTO.SensorReportItem item = new ReportDataDTO.SensorReportItem();
        item.setSensorId(stat.getSensorId());
        item.setSensorName(stat.getSensorName());
        item.setLocation(stat.getLocation());
        item.setZoneCode(stat.getZoneCode());
        item.setSensorType(stat.getSensorType());
        item.setUnit(stat.getUnit());
        item.setAvgValue(stat.getAvgValue());
        item.setMaxValue(stat.getMaxValue());
        item.setMinValue(stat.getMinValue());
        item.setOverWarningCount(stat.getOverWarningCount());
        item.setOverAlarmCount(stat.getOverAlarmCount());
        item.setOverPowerOffCount(stat.getOverPowerOffCount());
        item.setOverThresholdDurationMinutes(stat.getOverThresholdDurationMinutes());
        item.setDataCount(stat.getDataCount());
        return item;
    }

    public void sendReportByEmail(Long reportId, String recipients) {
        ReportRecord record = recordRepository.selectById(reportId);
        if (record == null) {
            throw new RuntimeException("报表记录不存在: " + reportId);
        }
        if (record.getFileUrl() == null || record.getFileUrl().isBlank()) {
            throw new RuntimeException("报表文件未生成");
        }
        if (recipients == null || recipients.isBlank()) {
            throw new RuntimeException("收件人不能为空");
        }

        try {
            byte[] fileBytes;
            try (InputStream is = minIOService.downloadFile(record.getFilePath())) {
                fileBytes = is.readAllBytes();
            }
            String fileName = buildFileNameFromRecord(record);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(emailFrom);
            helper.setTo(recipients.split(","));
            helper.setSubject("【" + mineName + "】" + record.getReportName());
            helper.setText("您好，\n\n附件是" + record.getReportName() + "，请查收。\n\n" +
                    "报表编号：" + record.getReportNo() + "\n" +
                    "统计周期：" + record.getStartDate() + " 至 " + record.getEndDate() + "\n" +
                    "生成时间：" + record.getCreatedAt().format(DATETIME_FMT) + "\n\n" +
                    "本邮件由系统自动发送，请勿直接回复。");
            helper.addAttachment(fileName,
                    new org.springframework.core.io.ByteArrayResource(fileBytes),
                    "PDF".equalsIgnoreCase(record.getFileFormat()) ? "application/pdf" : "application/vnd.ms-excel");
            mailSender.send(message);

            record.setEmailSent(1);
            record.setEmailSentTime(LocalDateTime.now());
            record.setEmailRecipients(recipients);
            recordRepository.updateById(record);
            log.info("报表邮件推送成功 - 报表: {}, 收件人: {}", record.getReportNo(), recipients);
        } catch (Exception e) {
            log.error("报表邮件推送失败 - 报表: {}, 错误: {}", record.getReportNo(), e.getMessage(), e);
            throw new RuntimeException("邮件推送失败: " + e.getMessage(), e);
        }
    }

    public List<ReportRecordDTO> getReportRecords(String reportType, Integer status, String startDate, String endDate) {
        LambdaQueryWrapper<ReportRecord> wrapper = new LambdaQueryWrapper<>();
        if (reportType != null) wrapper.eq(ReportRecord::getReportType, reportType);
        if (status != null) wrapper.eq(ReportRecord::getStatus, status);
        if (startDate != null) wrapper.ge(ReportRecord::getCreatedAt, LocalDate.parse(startDate, DATE_FMT).atStartOfDay());
        if (endDate != null) wrapper.lt(ReportRecord::getCreatedAt, LocalDate.parse(endDate, DATE_FMT).plusDays(1).atStartOfDay());
        wrapper.orderByDesc(ReportRecord::getCreatedAt);
        return recordRepository.selectList(wrapper).stream()
                .map(this::convertRecordToDTO).collect(Collectors.toList());
    }

    public ReportRecordDTO getReportRecord(String reportNo) {
        ReportRecord record = recordRepository.selectOne(
                new LambdaQueryWrapper<ReportRecord>().eq(ReportRecord::getReportNo, reportNo));
        if (record == null) throw new RuntimeException("报表记录不存在: " + reportNo);
        return convertRecordToDTO(record);
    }

    public int generateScheduledReports() {
        List<ReportTemplate> templates = templateRepository.selectList(
                new LambdaQueryWrapper<ReportTemplate>().eq(ReportTemplate::getEnabled, true));
        int count = 0;
        for (ReportTemplate template : templates) {
            try {
                String startDate;
                String endDate = LocalDate.now().format(DATE_FMT);
                startDate = switch (template.getTimeDimension()) {
                    case "DAY" -> LocalDate.now().minusDays(1).format(DATE_FMT);
                    case "WEEK" -> LocalDate.now().minusWeeks(1).format(DATE_FMT);
                    case "MONTH" -> LocalDate.now().minusMonths(1).format(DATE_FMT);
                    default -> LocalDate.now().minusDays(1).format(DATE_FMT);
                };
                ReportRecordDTO dto = generateReport(
                        template.getTemplateCode(), startDate, endDate,
                        null, template.getFileFormat(), "SCHEDULED");
                ReportRecord record = recordRepository.selectById(dto.getId());
                if (record != null) {
                    record.setGenerationSource("SCHEDULED");
                    recordRepository.updateById(record);
                }
                count++;
            } catch (Exception e) {
                log.error("定时报表生成失败 - 模板: {}", template.getTemplateCode(), e);
            }
        }
        return count;
    }

    private String generateReportNo() {
        return "RPT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + String.format("%04d", REPORT_SEQ.incrementAndGet() % 10000);
    }

    private String buildFileName(ReportTemplate template, String startDate, String endDate, String format) {
        String ext = "PDF".equalsIgnoreCase(format) ? ".pdf" : ".xls";
        return mineName + "_" + template.getTemplateCode() + "_" + startDate + "_" + endDate + ext;
    }

    private String buildFileNameFromRecord(ReportRecord record) {
        String ext = "PDF".equalsIgnoreCase(record.getFileFormat()) ? ".pdf" : ".xls";
        return mineName + "_" + record.getTemplateCode() + "_" +
                record.getStartDate().format(DATE_FMT) + "_" +
                record.getEndDate().format(DATE_FMT) + ext;
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
        dto.setStartDate(r.getStartDate() != null ? r.getStartDate().format(DATE_FMT) : null);
        dto.setEndDate(r.getEndDate() != null ? r.getEndDate().format(DATE_FMT) : null);
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
        dto.setEmailSentTime(r.getEmailSentTime() != null ? r.getEmailSentTime().format(DATETIME_FMT) : null);
        dto.setEmailRecipients(r.getEmailRecipients());
        dto.setCreatedAt(r.getCreatedAt() != null ? r.getCreatedAt().format(DATETIME_FMT) : null);

        if (r.getReportData() != null) {
            try {
                dto.setReportData(JSON.parseObject(r.getReportData(), ReportDataDTO.class));
            } catch (Exception ignored) {
            }
        }
        return dto;
    }
}
