package com.luan.FinancialSystem.service;

import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.Set;

@Service
public class TokenBlacklistService {

    private final Set<String> blacklist = new HashSet<>();

    public void invalidate(String token) {
        blacklist.add(token);
    }

    public boolean isBlacklisted(String token) {
        return blacklist.contains(token);
    }
}
