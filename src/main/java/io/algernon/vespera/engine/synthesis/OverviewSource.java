package io.algernon.vespera.engine.synthesis;

/**
 * One written cluster a topic's overview is asked over (ADR-402 section 1): its number among the topic's clusters, its
 * title and its synthesis doc's writing, with that writing's own citations still in it, which the question takes out.
 *
 * @param number the cluster's place among the topic's clusters, from 1, as the arrangement lists them (section 2)
 * @param title the cluster's title
 * @param writing the cluster's synthesis doc's prose
 */
public record OverviewSource(int number, String title, String writing) {}
