package ru.langsot.dating.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.langsot.dating.domain.Match;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("""
            select m from Match m join fetch m.userLow join fetch m.userHigh
             where (m.userLow.id = :userId or m.userHigh.id = :userId)
               and m.status = ru.langsot.dating.domain.MatchStatus.ACTIVE
             order by m.matchedAt desc
            """)
    List<Match> findActiveByUserId(@Param("userId") Long userId);

    @Query("select m from Match m where m.userLow.id = :low and m.userHigh.id = :high")
    Optional<Match> findByPair(@Param("low") Long low, @Param("high") Long high);
}
