package api.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import api.model.Image;

public interface ImageRepository extends JpaRepository<Image, Integer> {
}
