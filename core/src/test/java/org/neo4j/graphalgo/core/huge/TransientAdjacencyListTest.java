/*
 * Copyright (c) 2017-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphalgo.core.huge;

import org.junit.jupiter.api.Test;
import org.neo4j.graphalgo.core.GraphDimensions;
import org.neo4j.graphalgo.core.ImmutableGraphDimensions;
import org.neo4j.graphalgo.core.utils.BitUtil;
import org.neo4j.graphalgo.core.utils.mem.MemoryRange;
import org.neo4j.graphalgo.core.utils.mem.MemoryTree;
import org.neo4j.graphalgo.core.utils.paged.PageUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.graphalgo.core.huge.TransientAdjacencyList.PAGE_MASK;
import static org.neo4j.graphalgo.core.huge.TransientAdjacencyList.PAGE_SHIFT;
import static org.neo4j.graphalgo.core.huge.TransientAdjacencyList.computeAdjacencyByteSize;
import static org.neo4j.graphalgo.core.utils.BitUtil.ceilDiv;

class TransientAdjacencyListTest {

    @Test
    void shouldComputeCompressedMemoryEstimationForSinglePage() {
        GraphDimensions dimensions = ImmutableGraphDimensions.builder()
            .nodeCount(100)
            .maxRelCount(100)
            .build();

        MemoryTree memRec = TransientAdjacencyList.compressedMemoryEstimation(false).estimate(dimensions, 1);

        long classSize = 24;
        long bestCaseAdjacencySize = 500;
        long worstCaseAdjacencySize = 500;

        int minPages = PageUtil.numPagesFor(bestCaseAdjacencySize, PAGE_SHIFT, PAGE_MASK);
        int maxPages = PageUtil.numPagesFor(worstCaseAdjacencySize, PAGE_SHIFT, PAGE_MASK);
        long bytesPerPage = BitUtil.align(16 + 262144L, 8);
        long minMemoryReqs = minPages * bytesPerPage + BitUtil.align(16 + minPages * 4, 8);
        long maxMemoryReqs = maxPages * bytesPerPage + BitUtil.align(16 + maxPages * 4, 8);

        MemoryRange expected = MemoryRange.of(minMemoryReqs + classSize, maxMemoryReqs + classSize);

        assertEquals(expected, memRec.memoryUsage());
    }

    @Test
    void shouldComputeUncompressedMemoryEstimationForSinglePage() {
        GraphDimensions dimensions = ImmutableGraphDimensions.builder()
            .nodeCount(100)
            .maxRelCount(100)
            .build();

        MemoryTree memRec = TransientAdjacencyList.uncompressedMemoryEstimation(false).estimate(dimensions, 1);

        long classSize = 24;
        long uncompressedAdjacencySize = 1200;

        int pages = PageUtil.numPagesFor(uncompressedAdjacencySize, PAGE_SHIFT, PAGE_MASK);
        long bytesPerPage = BitUtil.align(16 + 262144L, 8);
        long memoryReqs = pages * bytesPerPage + BitUtil.align(16 + pages * 4, 8);

        MemoryRange expected = MemoryRange.of(memoryReqs + classSize);

        assertEquals(expected, memRec.memoryUsage());
    }

    @Test
    void shouldComputeCompressedMemoryEstimationForMultiplePage() {
        GraphDimensions dimensions = ImmutableGraphDimensions.builder()
            .nodeCount(100_000_000L)
            .maxRelCount(100_000_000_000L)
            .build();

        MemoryTree memRec = TransientAdjacencyList.compressedMemoryEstimation(false).estimate(dimensions, 1);

        long classSize = 24;
        long bestCaseAdjacencySize = 100_500_000_000L;
        long worstCaseAdjacencySize = 300_300_000_000L;

        int minPages = PageUtil.numPagesFor(bestCaseAdjacencySize, PAGE_SHIFT, PAGE_MASK);
        int maxPages = PageUtil.numPagesFor(worstCaseAdjacencySize, PAGE_SHIFT, PAGE_MASK);
        long bytesPerPage = BitUtil.align(16 + 262144L, 8);
        long minMemoryReqs = minPages * bytesPerPage + BitUtil.align(16 + minPages * 4, 8);
        long maxMemoryReqs = maxPages * bytesPerPage + BitUtil.align(16 + maxPages * 4, 8);

        MemoryRange expected = MemoryRange.of(minMemoryReqs + classSize, maxMemoryReqs + classSize);

        assertEquals(expected, memRec.memoryUsage());
    }

    @Test
    void shouldComputeUncompressedMemoryEstimationForMultiplePage() {
        GraphDimensions dimensions = ImmutableGraphDimensions.builder()
            .nodeCount(100_000_000L)
            .maxRelCount(100_000_000_000L)
            .build();

        MemoryTree memRec = TransientAdjacencyList.uncompressedMemoryEstimation(false).estimate(dimensions, 1);

        long classSize = 24;

        long uncompessedAdjacencySize = 800_400_000_000L;

        int pages = PageUtil.numPagesFor(uncompessedAdjacencySize, PAGE_SHIFT, PAGE_MASK);
        long bytesPerPage = BitUtil.align(16 + 262144L, 8);
        long memoryReqs = pages * bytesPerPage + BitUtil.align(16 + pages * 4, 8);

        MemoryRange expected = MemoryRange.of(memoryReqs + classSize);

        assertEquals(expected, memRec.memoryUsage());
    }

    @Test
    void shouldComputeAdjacencyByteSize() {
        long avgDegree = 1000;
        long nodeCount = 100_000_000;
        long delta = 100_000;
        long firstAdjacencyIdAvgByteSize = ceilDiv(ceilDiv(64 - Long.numberOfLeadingZeros(nodeCount - 1), 7), 2);
        // int relationshipByteSize = encodedVLongSize(delta);
        long relationshipByteSize = ceilDiv(64 - Long.numberOfLeadingZeros(delta - 1), 7);
        // int degreeByteSize = Integer.BYTES;
        int degreeByteSize = 4;
        long compressedAdjacencyByteSize = relationshipByteSize * (avgDegree - 1);
        long expected = (degreeByteSize + firstAdjacencyIdAvgByteSize + compressedAdjacencyByteSize) * nodeCount;

        assertEquals(expected, computeAdjacencyByteSize(avgDegree, nodeCount, delta));
    }

    @Test
    void shouldComputeAdjacencyByteSizeNoNodes() {
        long avgDegree = 0;
        long nodeCount = 0;
        long delta = 0;
        assertEquals(0, computeAdjacencyByteSize(avgDegree, nodeCount, delta));
    }

    @Test
    void shouldComputeAdjacencyByteSizeNoRelationships() {
        long avgDegree = 0;
        long nodeCount = 100;
        long delta = 0;
        assertEquals(400, computeAdjacencyByteSize(avgDegree, nodeCount, delta));
    }
}
