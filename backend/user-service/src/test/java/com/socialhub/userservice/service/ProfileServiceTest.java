
package com.socialhub.userservice.service;

import com.socialhub.userservice.dto.ProfileResponse;
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

    @Mock
    private ProfileCache cache;

    @Mock
    private FollowCache followCache;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void cannotFollowSelf() {

        ApiException exception = assertThrows(
                ApiException.class,
                () -> profileService.follow(1L, "jagar", 1L)
        );

        assertEquals("You cannot follow yourself", exception.getMessage());

        verifyNoInteractions(profileRepository, followRepository, cache, followCache);
    }

    private static ProfileResponse response(long id) {
        return new ProfileResponse(id, "u" + id, "U" + id, null, null, false, 0, 0, false);
    }

    @Test
    void getByIdUsesCachedProfileAndCachedFollowEdge() {
        when(cache.get(2L)).thenReturn(response(2L));
        when(followCache.isFollowing(1L, 2L)).thenReturn(true);

        ProfileResponse r = profileService.getById(1L, 2L);

        assertTrue(r.followedByMe());
        // The read path must not hit MySQL at all.
        verifyNoInteractions(profileRepository, followRepository);
    }

    @Test
    void viewingOwnProfileSkipsFollowCheck() {
        when(cache.get(1L)).thenReturn(response(1L));

        assertFalse(profileService.getById(1L, 1L).followedByMe());

        verifyNoInteractions(followCache, followRepository);
    }

    @Test
    void followEvictsBothProfilesAndTheFollowEdge() {
        Profile me = new Profile();
        me.setUserId(1L);
        Profile target = new Profile();
        target.setUserId(2L);
        when(profileRepository.findById(1L)).thenReturn(Optional.of(me));
        when(profileRepository.findById(2L)).thenReturn(Optional.of(target));
        when(followRepository.existsByFollowerIdAndFolloweeId(1L, 2L)).thenReturn(false);

        profileService.follow(1L, "jagar", 2L);

        verify(cache).evict(1L);
        verify(cache).evict(2L);
        verify(followCache).evict(1L, 2L);
    }

    @Test
    void unfollowEvictsFollowEdgeOnlyWhenSomethingWasRemoved() {
        when(followRepository.deleteEdge(1L, 2L)).thenReturn(1, 0);

        profileService.unfollow(1L, 2L);
        verify(followCache).evict(1L, 2L);

        profileService.unfollow(1L, 2L);
        verify(followCache, times(1)).evict(1L, 2L);
    }

    @Test
    void followIsIdempotent() {

        Profile profile = new Profile();
        profile.setUserId(1L);
        profile.setUsername("jagar");

        Profile targetProfile = new Profile();
        targetProfile.setUserId(2L);
        targetProfile.setUsername("target");

        when(profileRepository.findById(1L))
                .thenReturn(Optional.of(profile));

        when(profileRepository.findById(2L))
                .thenReturn(Optional.of(targetProfile));

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
