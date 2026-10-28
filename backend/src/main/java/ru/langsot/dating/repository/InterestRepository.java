package ru.langsot.dating.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.langsot.dating.domain.Interest;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface InterestRepository extends JpaRepository<Interest, Long> {

    Set<Interest> findByNameIn(Collection<String> names);

    List<Interest> findAllByOrderByCategoryAscNameAsc();
}
