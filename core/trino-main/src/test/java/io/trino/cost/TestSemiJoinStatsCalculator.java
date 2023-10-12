/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.trino.cost;

import io.trino.sql.planner.Symbol;
import org.junit.jupiter.api.Test;

import static io.trino.cost.PlanNodeStatsAssertion.assertThat;
import static io.trino.cost.SemiJoinStatsCalculator.computeAntiJoin;
import static io.trino.cost.SemiJoinStatsCalculator.computeSemiJoin;
import static java.lang.Double.NEGATIVE_INFINITY;
import static java.lang.Double.NaN;
import static java.lang.Double.POSITIVE_INFINITY;

public class TestSemiJoinStatsCalculator
{
    private final SymbolStatsEstimate uStats = SymbolStatsEstimate.builder()
            .setAverageRowSize(8.0)
            .setDistinctValuesCount(300)
            .setLowValue(0)
            .setHighValue(20)
            .setNullsFraction(0.1)
            .build();
    private final SymbolStatsEstimate wStats = SymbolStatsEstimate.builder()
            .setAverageRowSize(8.0)
            .setDistinctValuesCount(30)
            .setLowValue(0)
            .setHighValue(20)
            .setNullsFraction(0.1)
            .build();
    private final SymbolStatsEstimate xStats = SymbolStatsEstimate.builder()
            .setAverageRowSize(4.0)
            .setDistinctValuesCount(40.0)
            .setLowValue(-10.0)
            .setHighValue(10.0)
            .setNullsFraction(0.25)
            .build();
    private final SymbolStatsEstimate yStats = SymbolStatsEstimate.builder()
            .setAverageRowSize(4.0)
            .setDistinctValuesCount(20.0)
            .setLowValue(0.0)
            .setHighValue(5.0)
            .setNullsFraction(0.5)
            .build();
    private final SymbolStatsEstimate zStats = SymbolStatsEstimate.builder()
            .setAverageRowSize(4.0)
            .setDistinctValuesCount(5.0)
            .setLowValue(-100.0)
            .setHighValue(100.0)
            .setNullsFraction(0.1)
            .build();
    private final SymbolStatsEstimate leftOpenStats = SymbolStatsEstimate.builder()
            .setAverageRowSize(4.0)
            .setDistinctValuesCount(50.0)
            .setLowValue(NEGATIVE_INFINITY)
            .setHighValue(15.0)
            .setNullsFraction(0.1)
            .build();
    private final SymbolStatsEstimate rightOpenStats = SymbolStatsEstimate.builder()
            .setAverageRowSize(4.0)
            .setDistinctValuesCount(50.0)
            .setLowValue(-15.0)
            .setHighValue(POSITIVE_INFINITY)
            .setNullsFraction(0.1)
            .build();
    private final SymbolStatsEstimate unknownRangeStats = SymbolStatsEstimate.builder()
            .setAverageRowSize(4.0)
            .setDistinctValuesCount(50.0)
            .setLowValue(NEGATIVE_INFINITY)
            .setHighValue(POSITIVE_INFINITY)
            .setNullsFraction(0.1)
            .build();
    private final SymbolStatsEstimate emptyRangeStats = SymbolStatsEstimate.builder()
            .setAverageRowSize(4.0)
            .setDistinctValuesCount(0.0)
            .setLowValue(NaN)
            .setHighValue(NaN)
            .setNullsFraction(NaN)
            .build();
    private final SymbolStatsEstimate fractionalNdvStats = SymbolStatsEstimate.builder()
            .setAverageRowSize(NaN)
            .setDistinctValuesCount(0.1)
            .setNullsFraction(0)
            .build();

    private final Symbol u = new Symbol("u");
    private final Symbol w = new Symbol("w");
    private final Symbol x = new Symbol("x");
    private final Symbol y = new Symbol("y");
    private final Symbol z = new Symbol("z");
    private final Symbol leftOpen = new Symbol("leftOpen");
    private final Symbol rightOpen = new Symbol("rightOpen");
    private final Symbol unknownRange = new Symbol("unknownRange");
    private final Symbol emptyRange = new Symbol("emptyRange");
    private final Symbol unknown = new Symbol("unknown");
    private final Symbol fractionalNdv = new Symbol("fractionalNdv");
    private final PlanNodeStatsEstimate inputStatistics = PlanNodeStatsEstimate.builder()
            .addSymbolStatistics(u, uStats)
            .addSymbolStatistics(w, wStats)
            .addSymbolStatistics(x, xStats)
            .addSymbolStatistics(y, yStats)
            .addSymbolStatistics(z, zStats)
            .addSymbolStatistics(leftOpen, leftOpenStats)
            .addSymbolStatistics(rightOpen, rightOpenStats)
            .addSymbolStatistics(unknownRange, unknownRangeStats)
            .addSymbolStatistics(emptyRange, emptyRangeStats)
            .addSymbolStatistics(unknown, SymbolStatsEstimate.unknown())
            .addSymbolStatistics(fractionalNdv, fractionalNdvStats)
            .setOutputRowCount(1000.0)
            .build();

    @Test
    public void testSemiJoin()
    {
        // overlapping ranges
        assertThat(computeSemiJoin(inputStatistics, inputStatistics, x, w))
                .symbolStats(x, stats -> stats
                        .lowValue(xStats.getLowValue())
                        .highValue(xStats.getHighValue())
                        .nullsFraction(0)
                        .distinctValuesCount(wStats.getDistinctValuesCount()))
                .symbolStats(w, stats -> stats.isEqualTo(wStats))
                .symbolStats(z, stats -> stats.isEqualTo(zStats))
                .outputRowsCount(inputStatistics.getOutputRowCount() * xStats.getValuesFraction() * (wStats.getDistinctValuesCount() / xStats.getDistinctValuesCount()));

        // overlapping ranges, nothing filtered out
        assertThat(computeSemiJoin(inputStatistics, inputStatistics, x, u))
                .symbolStats(x, stats -> stats
                        .lowValue(xStats.getLowValue())
                        .highValue(xStats.getHighValue())
                        .nullsFraction(0)
                        .distinctValuesCount(xStats.getDistinctValuesCount()))
                .symbolStats(u, stats -> stats.isEqualTo(uStats))
                .symbolStats(z, stats -> stats.isEqualTo(zStats))
                .outputRowsCount(inputStatistics.getOutputRowCount() * xStats.getValuesFraction());

        // source stats are unknown
        assertThat(computeSemiJoin(inputStatistics, inputStatistics, unknown, u))
                .symbolStats(unknown, stats -> stats
                        .nullsFraction(0)
                        .distinctValuesCountUnknown()
                        .unknownRange())
                .symbolStats(u, stats -> stats.isEqualTo(uStats))
                .symbolStats(z, stats -> stats.isEqualTo(zStats))
                .outputRowsCountUnknown();

        // filtering stats are unknown
        assertThat(computeSemiJoin(inputStatistics, inputStatistics, x, unknown))
                .symbolStats(x, stats -> stats
                        .nullsFraction(0)
                        .lowValue(xStats.getLowValue())
                        .highValue(xStats.getHighValue())
                        .distinctValuesCountUnknown())
                .symbolStatsUnknown(unknown)
                .symbolStats(z, stats -> stats.isEqualTo(zStats))
                .outputRowsCountUnknown();

        // zero distinct values
        assertThat(computeSemiJoin(inputStatistics, inputStatistics, emptyRange, emptyRange))
                .outputRowsCount(0);

        // fractional distinct values
        assertThat(computeSemiJoin(inputStatistics, inputStatistics, fractionalNdv, fractionalNdv))
                .outputRowsCount(1000)
                .symbolStats(fractionalNdv, stats -> stats
                        .nullsFraction(0)
                        .distinctValuesCount(0.1));
    }

    @Test
    public void testAntiJoin()
    {
        // overlapping ranges
        assertThat(computeAntiJoin(inputStatistics, inputStatistics, u, x))
                .symbolStats(u, stats -> stats
                        .lowValue(uStats.getLowValue())
                        .highValue(uStats.getHighValue())
                        .nullsFraction(0)
                        .distinctValuesCount(uStats.getDistinctValuesCount() - xStats.getDistinctValuesCount()))
                .symbolStats(x, stats -> stats.isEqualTo(xStats))
                .symbolStats(z, stats -> stats.isEqualTo(zStats))
                .outputRowsCount(inputStatistics.getOutputRowCount() * uStats.getValuesFraction() * (1 - xStats.getDistinctValuesCount() / uStats.getDistinctValuesCount()));

        // overlapping ranges, everything filtered out (but we leave 0.5 due to safety coeeficient)
        assertThat(computeAntiJoin(inputStatistics, inputStatistics, x, u))
                .symbolStats(x, stats -> stats
                        .lowValue(xStats.getLowValue())
                        .highValue(xStats.getHighValue())
                        .nullsFraction(0)
                        .distinctValuesCount(xStats.getDistinctValuesCount() * 0.5))
                .symbolStats(u, stats -> stats.isEqualTo(uStats))
                .symbolStats(z, stats -> stats.isEqualTo(zStats))
                .outputRowsCount(inputStatistics.getOutputRowCount() * xStats.getValuesFraction() * 0.5);

        // source stats are unknown
        assertThat(computeAntiJoin(inputStatistics, inputStatistics, unknown, u))
                .symbolStats(unknown, stats -> stats
                        .nullsFraction(0)
                        .distinctValuesCountUnknown()
                        .unknownRange())
                .symbolStats(u, stats -> stats.isEqualTo(uStats))
                .symbolStats(z, stats -> stats.isEqualTo(zStats))
                .outputRowsCountUnknown();

        // filtering stats are unknown
        assertThat(computeAntiJoin(inputStatistics, inputStatistics, x, unknown))
                .symbolStats(x, stats -> stats
                        .nullsFraction(0)
                        .lowValue(xStats.getLowValue())
                        .highValue(xStats.getHighValue())
                        .distinctValuesCountUnknown())
                .symbolStatsUnknown(unknown)
                .symbolStats(z, stats -> stats.isEqualTo(zStats))
                .outputRowsCountUnknown();

        // zero distinct values
        assertThat(computeAntiJoin(inputStatistics, inputStatistics, emptyRange, emptyRange))
                .outputRowsCount(0);

        // fractional distinct values
        assertThat(computeAntiJoin(inputStatistics, inputStatistics, fractionalNdv, fractionalNdv))
                .outputRowsCount(500)
                .symbolStats(fractionalNdv, stats -> stats
                        .nullsFraction(0)
                        .distinctValuesCount(0.05));
    }
}
