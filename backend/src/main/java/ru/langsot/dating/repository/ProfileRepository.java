package ru.langsot.dating.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.langsot.dating.domain.Profile;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    Optional<Profile> findByUserId(Long userId);

    /**
     * Подбор кандидатов для ленты с учетом взаимных параметров поиска.
     * Первыми идут пользователи, поставившие просматривающему суперлайк, затем — недавно активные.
     */
    @Query(value = """
            SELECT p.* FROM profiles p
              JOIN users u ON u.id = p.user_id
             WHERE u.status = 'ACTIVE'
               AND u.id <> :viewerId
               AND p.gender IN (:genders)
               AND p.birth_date <= :bornBefore
               AND p.birth_date >  :bornAfter
               AND p.preferred_gender IN (:viewerGender, 'ANY')
               AND p.min_age <= :viewerAge AND p.max_age >= :viewerAge
               AND EXISTS (SELECT 1 FROM photos ph WHERE ph.profile_id = p.id)
               AND NOT EXISTS (SELECT 1 FROM likes l WHERE l.from_user_id = :viewerId AND l.to_user_id = u.id)
             ORDER BY (EXISTS (SELECT 1 FROM likes s WHERE s.from_user_id = u.id
                                 AND s.to_user_id = :viewerId AND s.type = 'SUPER_LIKE')) DESC,
                      u.last_active_at DESC, p.id
             LIMIT :lim
            """, nativeQuery = true)
    List<Profile> findCandidates(@Param("viewerId") Long viewerId,
                                 @Param("genders") Collection<String> genders,
                                 @Param("bornBefore") LocalDate bornBefore,
                                 @Param("bornAfter") LocalDate bornAfter,
                                 @Param("viewerGender") String viewerGender,
                                 @Param("viewerAge") int viewerAge,
                                 @Param("lim") int limit);
}
