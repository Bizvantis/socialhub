package com.socialhub.videoservice.repository;

import com.socialhub.videoservice.entity.Playlist;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    Page<Playlist> findByOwnerIdOrderByUpdatedAtDesc(Long ownerId, Pageable page);

    @Query("SELECT p FROM Playlist p WHERE p.ownerId = :ownerId AND p.visibility = 'PUBLIC' ORDER BY p.updatedAt DESC")
    Page<Playlist> findPublicByOwner(@Param("ownerId") Long ownerId, Pageable page);

    @Modifying
    @Query("UPDATE Playlist p SET p.videoCount = p.videoCount + :d WHERE p.id = :id")
    int adjustCount(@Param("id") Long id, @Param("d") int delta);
}
