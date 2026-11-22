package com.mujian.backend.common.dictionary.projection;

public record OptionDictionaryProjection(
    String optionGroup, String code, String label, int enabled) {}
