package ai.agent.swagger.model;

import java.time.Instant;

public record JwtResponse(
        String accessToken,
        Instant expiresAt
) {}
