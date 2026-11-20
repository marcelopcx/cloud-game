package api.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import api.model.HomeSection;

public interface HomeSectionRepository extends JpaRepository<HomeSection, Integer> {

    List<HomeSection> findAllByOrderBySortOrderAscIdAsc();
}
