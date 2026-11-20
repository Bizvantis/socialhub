package com.socialhub.videoservice.service;

import com.socialhub.common.web.ApiException;
import com.socialhub.common.web.CurrentUser;
import com.socialhub.common.web.PageResponse;
import com.socialhub.common.web.Pages;
import com.socialhub.videoservice.client.MediaClient;
import com.socialhub.videoservice.dto.VideoDtos.*;
import com.socialhub.videoservice.entity.Channel;
import com.socialhub.videoservice.repository.ChannelRepository;
import com.socialhub.videoservice.repository.RelationsDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ChannelService {

    private final ChannelRepository channels;
    private final RelationsDao relations;
    private final MediaClient media;
    private final VideoEvents events;

    public ChannelService(ChannelRepository channels, RelationsDao relations, MediaClient media, VideoEvents events) {
        this.channels = channels;
        this.relations = relations;
        this.media = media;
        this.events = events;
    }

    @Transactional
    public ChannelResponse create(CurrentUser user, CreateChannelRequest req) {
        if (channels.findByOwnerId(user.id()).isPresent()) {
            throw ApiException.conflict("You already have a channel");
        }
        String handle = req.handle().toLowerCase(Locale.ROOT);
        if (channels.existsByHandle(handle)) {
            throw ApiException.conflict("Handle @" + handle + " is taken");
        }
        Channel c = new Channel();
        c.setOwnerId(user.id());
        c.setHandle(handle);
        c.setName(req.name().trim());
        c.setDescription(req.description());
        channels.saveAndFlush(c); // flush so a concurrent duplicate fails here (unique keys) -> 409
        events.channelCreated(c);
        return VideoMapper.channel(c, false);
    }

    @Transactional(readOnly = true)
    public ChannelResponse mine(CurrentUser user) {
        return VideoMapper.channel(channels.findByOwnerId(user.id())
                .orElseThrow(() -> ApiException.notFound("Channel")), false);
    }

    @Transactional(readOnly = true)
    public ChannelResponse get(CurrentUser viewer, Long id) {
        Channel c = channels.findById(id).orElseThrow(() -> ApiException.notFound("Channel"));
        return VideoMapper.channel(c, viewer != null && relations.isSubscribed(viewer.id(), c.getId()));
    }

    @Transactional(readOnly = true)
    public ChannelResponse byHandle(CurrentUser viewer, String handle) {
        Channel c = channels.findByHandle(handle.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> ApiException.notFound("Channel"));
        return VideoMapper.channel(c, viewer != null && relations.isSubscribed(viewer.id(), c.getId()));
    }

    @Transactional
    public ChannelResponse update(CurrentUser user, Long id, UpdateChannelRequest req) {
        Channel c = channels.findById(id).orElseThrow(() -> ApiException.notFound("Channel"));
        user.requireOwnerOrAdmin(c.getOwnerId());
        if (req.name() != null) c.setName(req.name().trim());
        if (req.description() != null) c.setDescription(req.description());
        if (req.avatarMediaId() != null) {
            media.requireAttachable(req.avatarMediaId(), c.getOwnerId(), "AVATAR");
            c.setAvatarMediaId(req.avatarMediaId());
        }
        if (req.bannerMediaId() != null) {
            media.requireAttachable(req.bannerMediaId(), c.getOwnerId(), "BANNER");
            c.setBannerMediaId(req.bannerMediaId());
        }
        events.channelUpdated(c);
        return VideoMapper.channel(c, false);
    }

    /** Idempotent: subscribing twice is a no-op (INSERT IGNORE), the counter moves only once. */
    @Transactional
    public ChannelResponse subscribe(CurrentUser user, Long id) {
        Channel c = channels.findById(id).orElseThrow(() -> ApiException.notFound("Channel"));
        if (c.getOwnerId().equals(user.id())) {
            throw ApiException.badRequest("You cannot subscribe to your own channel");
        }
        int count = c.getSubscriberCount();
        if (relations.subscribe(user.id(), c.getId())) {
            // Atomic SQL increment; the entity field is NOT touched, so a flush can never
            // write back a stale count over a concurrent increment.
            channels.adjustSubscribers(c.getId(), 1);
            count++;
            events.subscribed(c, user.id(), true);
        }
        return VideoMapper.channel(c, true, count);
    }

    @Transactional
    public ChannelResponse unsubscribe(CurrentUser user, Long id) {
        Channel c = channels.findById(id).orElseThrow(() -> ApiException.notFound("Channel"));
        int count = c.getSubscriberCount();
        if (relations.unsubscribe(user.id(), c.getId())) {
            channels.adjustSubscribers(c.getId(), -1);
            count = Math.max(0, count - 1);
            events.subscribed(c, user.id(), false);
        }
        return VideoMapper.channel(c, false, count);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChannelSummary> subscriptions(CurrentUser user, int page, int size) {
        int s = Pages.clampSize(size);
        List<Long> ids = relations.subscribedChannelIds(user.id(), s + 1, Math.max(page, 0) * s);
        boolean hasNext = ids.size() > s;
        List<Long> pageIds = ids.subList(0, Math.min(ids.size(), s));
        Map<Long, Channel> byId = channels.findByIdIn(pageIds).stream()
                .collect(Collectors.toMap(Channel::getId, Function.identity()));
        List<ChannelSummary> items = pageIds.stream().map(byId::get).filter(java.util.Objects::nonNull)
                .map(VideoMapper::summary).toList();
        return new PageResponse<>(items, Math.max(page, 0), s, -1, hasNext);
    }
}
