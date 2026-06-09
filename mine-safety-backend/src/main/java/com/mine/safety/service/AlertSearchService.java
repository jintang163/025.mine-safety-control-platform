package com.mine.safety.service;

import com.mine.safety.domain.Alert;
import com.mine.safety.repository.AlertRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.elasticsearch.enabled", havingValue = "true")
public class AlertSearchService {

    private final AlertRepository alertRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    private static final String INDEX_NAME = "mine-safety-alerts";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    public AlertSearchService(AlertRepository alertRepository, ElasticsearchOperations elasticsearchOperations) {
        this.alertRepository = alertRepository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public void indexAlert(Alert alert) {
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("alertNo", alert.getAlertNo());
            doc.put("sensorId", alert.getSensorId());
            doc.put("sensorName", alert.getSensorName());
            doc.put("sensorType", alert.getSensorType());
            doc.put("location", alert.getLocation());
            doc.put("tunnel", alert.getTunnel());
            doc.put("alertValue", alert.getAlertValue());
            doc.put("thresholdValue", alert.getThresholdValue());
            doc.put("thresholdType", alert.getThresholdType());
            doc.put("level", alert.getLevel());
            doc.put("status", alert.getStatus());
            doc.put("escalationLevel", alert.getEscalationLevel());
            doc.put("ruleName", alert.getRuleName());
            doc.put("description", alert.getDescription());
            doc.put("firstAlertTime", alert.getFirstAlertTime() != null ? alert.getFirstAlertTime().format(FORMATTER) : null);
            doc.put("createdAt", alert.getCreatedAt() != null ? alert.getCreatedAt().format(FORMATTER) : null);

            IndexQuery indexQuery = new IndexQueryBuilder()
                    .withId(alert.getAlertNo())
                    .withObject(doc)
                    .build();

            elasticsearchOperations.index(indexQuery, IndexCoordinates.of(INDEX_NAME));
            log.debug("ES索引报警 - 编号: {}", alert.getAlertNo());
        } catch (Exception e) {
            log.warn("ES索引报警失败 - 编号: {}, 错误: {}", alert.getAlertNo(), e.getMessage());
        }
    }

    public Page<Map<String, Object>> searchAlerts(String keyword, String level, String tunnel,
                                                    String sensorType, String status,
                                                    LocalDateTime startTime, LocalDateTime endTime,
                                                    Pageable pageable) {
        try {
            var boolQuery = co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.of(b -> {
                if (keyword != null && !keyword.isEmpty()) {
                    b.should(s -> s.multiMatch(m -> m
                            .query(keyword)
                            .fields("alertNo", "sensorName", "location", "description", "ruleName")
                    ));
                }
                if (level != null) {
                    b.filter(f -> f.term(t -> t.field("level").value(level)));
                }
                if (tunnel != null) {
                    b.filter(f -> f.term(t -> t.field("tunnel").value(tunnel)));
                }
                if (sensorType != null) {
                    b.filter(f -> f.term(t -> t.field("sensorType").value(sensorType)));
                }
                if (status != null) {
                    b.filter(f -> f.term(t -> t.field("status").value(status)));
                }
                if (startTime != null) {
                    b.filter(f -> f.range(r -> r
                            .field("firstAlertTime")
                            .gte(co.elastic.clients.elasticsearch._types.FieldValue.of(startTime.format(FORMATTER)))
                    ));
                }
                if (endTime != null) {
                    b.filter(f -> f.range(r -> r
                            .field("firstAlertTime")
                            .lte(co.elastic.clients.elasticsearch._types.FieldValue.of(endTime.format(FORMATTER)))
                    ));
                }
                return b;
            });

            NativeQuery searchQuery = NativeQuery.builder()
                    .withQuery(q -> q.bool(boolQuery._toQuery()))
                    .withPageable(pageable)
                    .build();

            SearchHits<Map> searchHits = elasticsearchOperations.search(searchQuery, Map.class, IndexCoordinates.of(INDEX_NAME));

            List<Map<String, Object>> content = searchHits.getSearchHits().stream()
                    .map(hit -> {
                        Map<String, Object> source = hit.getContent();
                        source.put("_id", hit.getId());
                        source.put("_score", hit.getScore());
                        return source;
                    })
                    .collect(Collectors.toList());

            return new PageImpl<>(content, pageable, searchHits.getTotalHits());
        } catch (Exception e) {
            log.error("ES搜索报警失败: {}", e.getMessage(), e);
            return Page.empty(pageable);
        }
    }

    public void reindexAll() {
        try {
            List<Alert> allAlerts = alertRepository.findAll();
            for (Alert alert : allAlerts) {
                indexAlert(alert);
            }
            log.info("ES全量重建索引完成 - 总数: {}", allAlerts.size());
        } catch (Exception e) {
            log.error("ES全量重建索引失败: {}", e.getMessage(), e);
        }
    }
}
