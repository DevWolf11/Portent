package dev.plugindoctor.model;

/** A class entry ASM could not parse. The scan continues; the admin is told what was skipped. */
public record UnreadableClass(String entryName, String reason) {}
