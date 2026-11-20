package com.example.demo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceLinkRequest(@NotBlank @Size(max = 20) String code) {
}
