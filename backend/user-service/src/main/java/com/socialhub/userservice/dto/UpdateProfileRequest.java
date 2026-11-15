package com.socialhub.userservice.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @Size(min = 1, max = 50)
        String displayName,

        @Size(max = 300)
        String bio,

        Boolean privateAccount
) {}