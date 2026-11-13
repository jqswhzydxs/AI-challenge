package com.xq.contracts.agent;

import java.util.List;

public record VerifiedIdentity(String userId, String username, List<String> roles) {}
