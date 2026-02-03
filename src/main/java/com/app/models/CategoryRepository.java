package com.app.models;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {

    Optional<Category> findByCode(String code);

    Optional<Category> findByStoreIdAndCode(String storeId, String code);

    List<Category> findByStoreId(String storeId);

    List<Category> findByParentCategoryId(String parentCategoryId);

    List<Category> findByParentCategoryIdIsNull();

    List<Category> findByStoreIdAndParentCategoryIdIsNull(String storeId);

    boolean existsByCode(String code);

    boolean existsByStoreIdAndCode(String storeId, String code);
}
