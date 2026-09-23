package com.aifishing.planning.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratePreferTest {

    @Test
    void detectsRespondAsync() {
        assertThat(GeneratePrefer.respondAsync(null)).isFalse();
        assertThat(GeneratePrefer.respondAsync("")).isFalse();
        assertThat(GeneratePrefer.respondAsync("return=representation")).isFalse();
        assertThat(GeneratePrefer.respondAsync("respond-async")).isTrue();
        assertThat(GeneratePrefer.respondAsync("Respond-Async")).isTrue();
        assertThat(GeneratePrefer.respondAsync("respond-async, wait=10")).isTrue();
    }
}
