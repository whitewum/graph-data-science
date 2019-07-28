/*
 * Copyright (c) 2017-2019 "Neo4j,"
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
package org.neo4j.graphalgo.pregel;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.neo4j.graphalgo.PropertyMapping;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.HugeWeightMapping;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.huge.loader.HugeGraphFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.ProgressLogger;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphalgo.pregel.components.SCComputation;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Transaction;
import org.neo4j.test.rule.ImpermanentDatabaseRule;

import java.util.HashMap;
import java.util.Map;

public class SCCTest {

    private static final String ID_PROPERTY = "id";
    private static final String MESSAGE_PROPERTY = "message";

    private static final Label NODE_LABEL = Label.label("Node");

    private static final String TEST_GRAPH =
            "CREATE (nA:Node { id: 0 })\n" +
            "CREATE (nB:Node { id: 1 })\n" +
            "CREATE (nC:Node { id: 2 })\n" +
            "CREATE (nD:Node { id: 3 })\n" +
            "CREATE (nE:Node { id: 4 })\n" +
            "CREATE (nF:Node { id: 5 })\n" +
            "CREATE (nG:Node { id: 6 })\n" +
            "CREATE (nH:Node { id: 7 })\n" +
            "CREATE (nI:Node { id: 8 })\n" +
            // {J}
            "CREATE (nJ:Node { id: 9 })\n" +
            "CREATE\n" +
            // {A, B, C, D}
            "  (nA)-[:TYPE { message: 1 }]->(nB),\n" +
            "  (nB)-[:TYPE { message: 1 }]->(nC),\n" +
            "  (nC)-[:TYPE { message: 1 }]->(nD),\n" +
            "  (nD)-[:TYPE { message: 1 }]->(nA),\n" +
            // {E, F, G}
            "  (nE)-[:TYPE { message: 1 }]->(nF),\n" +
            "  (nF)-[:TYPE { message: 1 }]->(nG),\n" +
            "  (nG)-[:TYPE { message: 1 }]->(nE),\n" +
            // {H, I}
            "  (nI)-[:TYPE { message: 1 }]->(nH),\n" +
            "  (nH)-[:TYPE { message: 1 }]->(nI)";

    @ClassRule
    public static final ImpermanentDatabaseRule DB = new ImpermanentDatabaseRule();

    @BeforeClass
    public static void setup() {
        DB.execute(TEST_GRAPH);
    }

    @AfterClass
    public static void shutdown() {
        DB.shutdown();
    }

    private final Graph graph;

    public SCCTest() {
        graph = new GraphLoader(DB)
                .withAnyRelationshipType()
                .withAnyLabel()
                // The following options need to be default for Pregel
                .withDirection(Direction.BOTH)
                .withOptionalRelationshipWeightsFromProperty(MESSAGE_PROPERTY, 1)
                .load(HugeGraphFactory.class);
    }

    @Test
    public void runSCC() {
        int batchSize = 10;
        int maxIterations = 10;

        Pregel pregelJob = new Pregel(
                graph,
                new SCComputation(),
                batchSize,
                Pools.DEFAULT_CONCURRENCY,
                Pools.DEFAULT,
                AllocationTracker.EMPTY,
                ProgressLogger.NULL_LOGGER);

        HugeWeightMapping nodeValues = pregelJob.run(maxIterations);

        assertValues(graph, nodeValues,0, 0, 1, 0, 2, 0, 3, 0, 4, 4, 5, 4, 6, 4, 7, 7, 8, 7, 9, 9);
    }

    private void assertValues(final Graph graph, HugeWeightMapping computedValues, final long... values) {
        Map<Long, Long> expectedValues = new HashMap<>();
        try (Transaction tx = DB.beginTx()) {
            for (int i = 0; i < values.length; i+=2) {
                expectedValues.put(DB.findNode(NODE_LABEL, ID_PROPERTY, values[i]).getId(), values[i + 1]);
            }
            tx.success();
        }
        expectedValues.forEach((idProp, expectedValue) -> {
            long neoId = graph.toOriginalNodeId(idProp);
            long computedValue = (long) computedValues.nodeWeight(neoId);
            Assert.assertEquals(
                    String.format("Node.id = %d should have component %d", idProp, expectedValue),
                    (long) expectedValue,
                    computedValue);
        });
    }
}
