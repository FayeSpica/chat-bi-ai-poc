package com.chatbi.repository;

import com.chatbi.model.DatabaseConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatabaseConnectionRepository extends JpaRepository<DatabaseConnection, String> {
    List<DatabaseConnection> findByIsActiveTrue();
    Optional<DatabaseConnection> findFirstByIsActiveTrue();
}