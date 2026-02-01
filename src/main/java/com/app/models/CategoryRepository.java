package com.app.models;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends MongoRepository<Category, String> {

    Optional<Category> findByCode(String code);

    List<Category> findByParentCategoryId(String parentCategoryId);

    List<Category> findByParentCategoryIdIsNull();

    boolean existsByCode(String code);
}
