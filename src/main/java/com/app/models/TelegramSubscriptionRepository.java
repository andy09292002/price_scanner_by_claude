package com.app.models;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramSubscriptionRepository extends MongoRepository<TelegramSubscription, String> {

    Optional<TelegramSubscription> findByChatId(Long chatId);

    List<TelegramSubscription> findByActiveTrue();

    boolean existsByChatId(Long chatId);

    void deleteByChatId(Long chatId);
}
