// Copyright 2020 Oath Inc.
// Licensed under the terms of the Apache license. Please see LICENSE.md file distributed with this work for terms.
package com.yahoo.bard.webservice.web.apirequest.generator.having.antlr;


import static com.yahoo.bard.webservice.web.ErrorMessageFormat.HAVING_METRICS_NOT_IN_QUERY_FORMAT;
import static com.yahoo.bard.webservice.web.ErrorMessageFormat.HAVING_METRIC_UNDEFINED;
import static com.yahoo.bard.webservice.web.ErrorMessageFormat.HAVING_NON_NUMERIC;
import static com.yahoo.bard.webservice.web.ErrorMessageFormat.HAVING_OPERATOR_INVALID;

import com.yahoo.bard.webservice.data.metric.LogicalMetric;
import com.yahoo.bard.webservice.data.metric.MetricDictionary;
import com.yahoo.bard.webservice.web.ApiHaving;
import com.yahoo.bard.webservice.web.apirequest.exceptions.BadApiRequestException;
import com.yahoo.bard.webservice.web.apirequest.exceptions.BadHavingException;
import com.yahoo.bard.webservice.web.HavingOperation;
import com.yahoo.bard.webservice.web.havingparser.HavingsBaseListener;
import com.yahoo.bard.webservice.web.havingparser.HavingsParser;

import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApiHavingsListListener extends HavingsBaseListener {
    private static final Logger LOG = LoggerFactory.getLogger(ApiHavingsListListener.class);

    private final Map<HavingsParser.HavingComponentContext, ApiHaving> havings = new HashMap<>();
    private final Map<LogicalMetric, Set<ApiHaving>> metricHavingsMap = new LinkedHashMap<>();
    private Exception error = null;
    private final MetricDictionary metricDictionary;
    private final Set<LogicalMetric> logicalMetrics;
    /**
     *  Constructor.
     *
     * @param metricDictionary dictionary of all the valid metrics
     * @param logicalMetrics The logical metrics used in this query
     */
    public ApiHavingsListListener(MetricDictionary metricDictionary, Set<LogicalMetric> logicalMetrics) {
        this.metricDictionary = metricDictionary;
        this.logicalMetrics = logicalMetrics;
    }

    @Override
    public void exitHavings(HavingsParser.HavingsContext ctx) {
        for (HavingsParser.HavingComponentContext f : ctx.havingComponent()) {
            ApiHaving having = havings.get(f);
            if (having == null) {
                // there was some error and it has been saved in the errors list
                break;
            }
            LogicalMetric metric = having.getMetric();
            if (!metricHavingsMap.containsKey(metric)) {
                metricHavingsMap.put(metric, new LinkedHashSet<>());
            }
            Set<ApiHaving> havingSet = metricHavingsMap.get(metric);
            havingSet.add(having);
            metricHavingsMap.put(metric, havingSet);
        }
    }

    @Override
    public void exitHavingComponent(HavingsParser.HavingComponentContext ctx) {
        LogicalMetric metric = extractMetric(ctx);
        if (metric == null) {
            return;
        }

        HavingOperation operation = extractOperation(ctx);
        if (operation == null) {
            return;
        }

        List<Double> values = extractValues(ctx);

        //Make the constructor public in having
        ApiHaving having = new ApiHaving(metric, operation, values);
        havings.put(ctx, having);

    }

    /**
     * Extract the metric portion of a having.
     * @param havingContext the Having context
     * @return the Metric
     */
    protected LogicalMetric extractMetric(HavingsParser.HavingComponentContext havingContext) {
        String metricName = havingContext.metric().getText();
        LogicalMetric metric = metricDictionary.get(metricName);

        // If no metric is found in metric dictionary throw exception.
        if (metric == null) {
            LOG.debug(HAVING_METRIC_UNDEFINED.logFormat(metricName));
            error = new BadHavingException(HAVING_METRIC_UNDEFINED.format(metricName));
            return null;
        }

        // If the having metric is not found in query metrics throw exception
        if (!logicalMetrics.contains(metric)) {
            LOG.debug(HAVING_METRICS_NOT_IN_QUERY_FORMAT.logFormat(metricName));
            error = new BadHavingException(HAVING_METRICS_NOT_IN_QUERY_FORMAT.format(metricName));
            return null;
        }

        return metric;
    }

    /**
     * Extract the operation from having.
     *
     * @param havingContext the Having context
     * @return the operation
     */
    protected HavingOperation extractOperation(HavingsParser.HavingComponentContext havingContext) {
        String operationName = havingContext.OPERATOR().getText();
        HavingOperation operation;
        try {
            operation = HavingOperation.fromString(operationName);
        } catch (IllegalArgumentException ignored) {
            LOG.debug(HAVING_OPERATOR_INVALID.logFormat(operationName));
            error = new BadHavingException(HAVING_OPERATOR_INVALID.format(operationName));
            return null;
        }
        return operation;
    }

    /**
     * Extract values from the having.
     *
     * @param havingContext the Having context
     * @return the list of values
     */
    protected List<Double> extractValues(HavingsParser.HavingComponentContext havingContext) {
        List<Double> values = new LinkedList<>();
        Double val;
        for (TerminalNode tok : havingContext.values().VALUE()) {
            try {
                val = Double.parseDouble(tok.getText());
            } catch (NumberFormatException e) {
                LOG.debug(HAVING_NON_NUMERIC.format(e.getMessage()));
                error = new BadHavingException(HAVING_NON_NUMERIC.format(e.getMessage()));
                return null;
            }
            values.add(val);
        }
        return values;
    }

    /**
     * Gets the list of parsed metricHavingMap. If a parsing error occured or an invalid having was
     * specified, throws BadHavingException
     *
     * @return a Map of ApiHaving keyed by Metric
     * @throws BadHavingException Thrown when an invalid having is specified
     * @throws BadApiRequestException Thrown when a filter isn't supported
     */
    public Map<LogicalMetric, Set<ApiHaving>> getMetricHavingsMap () throws BadHavingException, BadApiRequestException {
        processErrors();
        return metricHavingsMap;
    }

    /**
     * Throw any pending exceptions.
     *
     * @throws BadHavingException Thrown when the having is invalid
     */
    protected void processErrors() throws BadHavingException {
        if (error != null) {
            throw (BadHavingException) error;
        }
    }
}
