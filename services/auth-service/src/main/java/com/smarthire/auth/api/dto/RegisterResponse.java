package com.smarthire.auth.api.dto;

/** Response to a successful self-registration: the id of the newly created CANDIDATE account. */
public record RegisterResponse(String userId) {}
