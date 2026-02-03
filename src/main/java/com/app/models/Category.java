package com.app.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "categories")
@org.springframework.data.mongodb.core.index.CompoundIndex(
    name = "store_code_idx",
    def = "{'storeId': 1, 'code': 1}",
    unique = true
)
public class Category extends BaseEntity {

    @Indexed
    private String name;

    @Indexed
    private String code;

    @Indexed
    private String storeId;

    private String parentCategoryId;
}
