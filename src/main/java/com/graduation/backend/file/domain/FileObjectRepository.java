package com.graduation.backend.file.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileObjectRepository extends JpaRepository<FileObject, Long> {

    Optional<FileObject> findByIdAndOwnerId(Long id, Long ownerId);
}
