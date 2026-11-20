package com.socialhub.videoservice.repository;

import com.socialhub.videoservice.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByOwnerId(Long ownerId);

    Optional<Channel> findByHandle(String handle);

    boolean existsByHandle(String handle);

    List<Channel> findByIdIn(Collection<Long> ids);

    /** Atomic counter update: no read-modify-write race between concurrent subscribers. */
    @Modifying
    @Query("UPDATE Channel c SET c.subscriberCount = c.subscriberCount + :d WHERE c.id = :id")
    int adjustSubscribers(@Param("id") Long id, @Param("d") int delta);

    @Modifying
    @Query("UPDATE Channel c SET c.videoCount = c.videoCount + :d WHERE c.id = :id")
    int adjustVideos(@Param("id") Long id, @Param("d") int delta);
}
