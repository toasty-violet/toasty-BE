package com.toasty.domain.user.service;

import com.toasty.domain.user.entity.User;

public record UserLoginResult(User user, boolean isOnboardingCompleted) {}
