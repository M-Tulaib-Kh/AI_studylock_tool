package com.studylock.repository;

import com.studylock.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public interface GameStateRepository extends JpaRepository<GameState, Long> {
    Optional<GameState> findByUserId(Long userId);
}
