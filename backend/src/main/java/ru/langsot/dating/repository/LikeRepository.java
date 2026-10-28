package ru.langsot.dating.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.langsot.dating.domain.Like;

import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    Optional<Like> findByFromIdAndToId(Long fromId, Long toId);

    /** Есть ли положительная оценка (LIKE или SUPER_LIKE) от from к to. */
    @Query("""
            select count(l) > 0 from Like l
             where l.from.id = :fromId and l.to.id = :toId
               and l.type <> ru.langsot.dating.domain.LikeType.DISLIKE
            """)
    boolean existsPositive(@Param("fromId") Long fromId, @Param("toId") Long toId);

    long countByFromId(Long fromId);

    /** Кто из указанных пользователей поставил суперлайк пользователю toId (один запрос на всю пачку ленты). */
    @Query("""
            select l.from.id from Like l
             where l.to.id = :toId and l.from.id in :fromIds
               and l.type = ru.langsot.dating.domain.LikeType.SUPER_LIKE
            """)
    java.util.Set<Long> findSuperLikers(@Param("toId") Long toId, @Param("fromIds") java.util.Collection<Long> fromIds);
}
