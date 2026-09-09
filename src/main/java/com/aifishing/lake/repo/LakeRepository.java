package com.aifishing.lake.repo;

import com.aifishing.lake.domain.Lake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LakeRepository extends JpaRepository<Lake, UUID> {

    @Query("SELECT l FROM Lake l WHERE :query IS NULL OR :query = '' OR LOWER(l.name) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY l.name ASC")
    List<Lake> searchByName(@Param("query") String query);
}
