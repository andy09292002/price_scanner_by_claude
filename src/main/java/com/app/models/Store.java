package com.app.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "stores")
public class Store extends BaseEntity {

    private String name;

    private String code; // RCSS, WALMART, PRICESMART, TNT

    private String baseUrl;

    private boolean active;

    private Map<String, Object> scraperConfig;
}
