package com.app.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "price_records")
@CompoundIndex(name = "product_store_idx", def = "{'productId': 1, 'storeId': 1}")
public class PriceRecord extends BaseEntity {

    @Indexed
    private String productId;

    @Indexed
    private String storeId;

    private BigDecimal regularPrice;

    private BigDecimal salePrice;

    private BigDecimal unitPrice;

    private boolean onSale;

    private String promoDescription;

    @Indexed
    private LocalDateTime scrapedAt;

    private boolean inStock;

    private String sourceUrl;
}
