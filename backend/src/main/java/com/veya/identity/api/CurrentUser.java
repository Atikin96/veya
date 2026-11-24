package com.veya.identity.api;

import java.util.UUID;

/**
 * The authenticated caller, resolved from the verified JWT (subject = user id). Controllers of every module declare
 * it as a handler parameter; all ownership checks compare a resource's owner with {@link #id()} (US-30, LEG-3).
 */
public record CurrentUser(UUID id) {
}
