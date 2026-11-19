package com.socialhub.userservice.service;

import com.socialhub.userservice.dto.ProfileResponse;
import com.socialhub.userservice.exception.ApiException;
import com.socialhub.userservice.mapper.ProfileMapper;
import com.socialhub.userservice.repository.ProfileRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProfileCache {

    private final ProfileRepository profiles;

    public ProfileCache(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    // No @Transactional here: the transaction interceptor could wrap the cache
    // interceptor and open a DB transaction even on a cache hit. findById already
    // runs in Spring Data's own read-only transaction, which only happens on a miss.
    @Cacheable(cacheNames = "profile", key = "#id")
    public ProfileResponse get(Long id) {
        return profiles.findById(id)
                .map(p -> ProfileMapper.toResponse(p, false))
                .orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    @CacheEvict(cacheNames = "profile", key = "#id")
    public void evict(Long id) {}
}
