// Copyright 2020 Oath Inc.
// Licensed under the terms of the Apache license. Please see LICENSE.md file distributed with this work for terms.
package com.yahoo.bard.webservice.web.apirequest.generator.having;

import com.yahoo.bard.webservice.data.metric.LogicalMetric;
import com.yahoo.bard.webservice.web.LogicalHaving;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A having generator decorator that uses the metrics from the query as the dictionary of metrics for building havings.
 */
public class PerRequestDictionaryHavingGenerator implements HavingGenerator {

    private final DefaultHavingApiGenerator havingGenerator;

    /**
     * Constructor.
     *
     * @param havingGenerator  A default having generator to decorate.
     */
    public PerRequestDictionaryHavingGenerator(DefaultHavingApiGenerator havingGenerator) {
        this.havingGenerator = havingGenerator;
    }

    /**
     * Wrap the enclosed having generator in a query scoped metric dictionary.
     *
     * @param havingString  The having clause from the URI
     * @param logicalMetrics The set of metrics provided
     *
     * @return  A collection of LogicalHaving objects grouped by Logical Metric
     */
    @Override
    public Map<LogicalMetric, Set<LogicalHaving>> apply(String havingString, Set<LogicalMetric> logicalMetrics) {
        return havingGenerator
                .withMetricDictionary(
                        logicalMetrics.stream().collect(Collectors.toMap(LogicalMetric::getName, Function.identity()))
                )
                .apply(havingString, logicalMetrics);
    }
}
