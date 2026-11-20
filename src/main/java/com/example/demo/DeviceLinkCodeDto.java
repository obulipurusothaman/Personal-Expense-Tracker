package com.example.demo;

import java.time.Instant;

public record DeviceLinkCodeDto(String code, Instant expiresAt) {
}
