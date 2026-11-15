package com.socialhub.userservice.service;

import com.socialhub.userservice.entity.Profile;
import com.socialhub.userservice.exception.ApiException;
import com.socialhub.userservice.repository.FollowRepository;
import com.socialhub.userservice.repository.ProfileRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository profileRepository;

    @Mock
    private FollowRepository followRepository;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void cannotFollowSelf() {

        ApiException exception = assertThrows(
                ApiException.class,
                () -> profileService.follow(1L, "jagar", 1L)
        );

        assertEquals("You cannot follow yourself", exception.getMessage());

        verifyNoInteractions(profileRepository, followRepository);
    }

    @Test
    void followIsIdempotent() {

        Profile profile = new Profile();
        profile.setUserId(1L);
        profile.setUsername("jagar");

        when(profileRepository.findById(1L))
                .thenReturn(Optional.of(profile));

        when(profileRepository.existsById(2L))
                .thenReturn(true);

        when(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L))
                .thenReturn(true);

        assertDoesNotThrow(
                () -> profileService.follow(1L, "jagar", 2L)
        );

        // Existing relationship must not be saved again
        verify(followRepository, never()).save(any());

        // Counts must not change again
        verify(profileRepository, never()).adjustFollowing(any(), anyInt());
        verify(profileRepository, never()).adjustFollowers(any(), anyInt());
    }
}