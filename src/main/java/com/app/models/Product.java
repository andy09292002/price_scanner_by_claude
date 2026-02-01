package com.app.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "products")
public class Product extends BaseEntity {

    @Indexed
    private String name;

    @Indexed
    private String normalizedName;

    private String brand;

    private String size;

    private String unit;

    private String categoryId;

    private String imageUrl;

    // Map: storeCode -> store's product ID
    private Map<String, String> storeProductIds;
}
