package io.algernon.vespera.deliverable;

import io.algernon.vespera.ledger.OccurrenceId;

/**
 * One code set of the stage-1 run upstream of the tree's generation run, as the tree is drawn from it (ADR-296 §8):
 * its folder relative to the corpus root, {@code ""} for the root's set, its default name, and the lowest occurrence id
 * among its members, which orders sets alike in kind and names a folder left empty (ADR-296 §3.1).
 *
 * @param folder the set's folder as {@code code_set} holds it
 * @param defaultName the folder's last element, or the corpus root's name for the root's set
 * @param lowestMember the lowest occurrence id of the set's members
 */
public record ListedCodeSet(String folder, String defaultName, OccurrenceId lowestMember) {}
