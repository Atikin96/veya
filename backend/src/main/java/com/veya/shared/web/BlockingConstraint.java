package com.veya.shared.web;

import java.util.UUID;

/** One entry of {@code Problem.blockingConstraints} (422, US-19): why a request is infeasible. */
public record BlockingConstraint(String type, String message, UUID itemKey) {
}
