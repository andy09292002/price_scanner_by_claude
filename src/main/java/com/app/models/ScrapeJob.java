package com.app.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "scrape_jobs")
public class ScrapeJob extends BaseEntity {

    @Indexed
    private String storeId;

    private String storeCode;

    @Indexed
    private JobStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private int totalProducts;

    private int successCount;

    private int errorCount;

    private List<String> errorMessages;

    public enum JobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
