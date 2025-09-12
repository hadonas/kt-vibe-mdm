package com.company.app.catalog.repository;

import com.company.app.catalog.entity.Counter;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CounterRepository extends MongoRepository<Counter, String> {
    Optional<Counter> findBySubCode(String subCode);
}
