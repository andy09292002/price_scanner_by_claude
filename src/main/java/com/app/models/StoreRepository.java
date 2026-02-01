package com.app.models;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoreRepository extends MongoRepository<Store, String> {

    Optional<Store> findByCode(String code);

    List<Store> findByActiveTrue();

    boolean existsByCode(String code);
}
