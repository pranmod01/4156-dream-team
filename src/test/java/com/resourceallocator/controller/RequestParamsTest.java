package com.resourceallocator.controller;
// AI-generated (Claude Code, Claude Opus 5.5)

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resourceallocator.exception.InvalidRequestException;
import com.resourceallocator.model.ResourceType;
import org.junit.jupiter.api.Test;

class RequestParamsTest {

    @Test
    void parseEnumAcceptsAnyCaseAndSurroundingSpaces() {
        assertThat(RequestParams.parseEnum(
                ResourceType.class, "resourceType", " gpu "))
                .isEqualTo(ResourceType.GPU);
    }

    @Test
    void parseEnumOfMissingValueIsNull() {
        assertThat(RequestParams.parseEnum(
                ResourceType.class, "resourceType", null)).isNull();
    }

    @Test
    void parseEnumRejectsUnknownValueAndKeepsTheCause() {
        assertThatThrownBy(() -> RequestParams.parseEnum(
                ResourceType.class, "resourceType", "tpu"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Invalid resourceType 'tpu'")
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }
}
