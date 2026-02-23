package com.app.models;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<Product> findByCategoryId(String categoryId);

    List<Product> findByNormalizedName(String normalizedName);

    List<Product> findByNormalizedNameAndSizeAndUnit(String normalizedName, String size, String unit);

    @Query("{'storeProductIds.?0': ?1}")
    Optional<Product> findByStoreCodeAndStoreProductId(String storeCode, String storeProductId);

    Page<Product> findByNameContainingIgnoreCaseOrBrandContainingIgnoreCase(
            String name, String brand, Pageable pageable);
}
