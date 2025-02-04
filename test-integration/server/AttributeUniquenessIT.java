/*
 * GRAKN.AI - THE KNOWLEDGE GRAPH
 * Copyright (C) 2019 Grakn Labs Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package grakn.core.server;

import grakn.core.common.util.Collections;
import grakn.core.concept.ConceptId;
import grakn.core.concept.Label;
import grakn.core.concept.answer.ConceptMap;
import grakn.core.rule.GraknTestServer;
import grakn.core.server.kb.Schema;
import grakn.core.server.session.Session;
import grakn.core.server.session.TransactionOLTP;
import graql.lang.Graql;
import graql.lang.query.GraqlInsert;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static graql.lang.Graql.type;
import static graql.lang.Graql.var;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

@SuppressWarnings({"CheckReturnValue", "Duplicates"})
public class AttributeUniquenessIT {
    private Session session;

    @ClassRule
    public static final GraknTestServer server = new GraknTestServer();

    @Before
    public void setUp() {
        session = server.sessionWithNewKeyspace();
    }

    @After
    public void closeSession() {
        session.close();
    }

    @Test
    public void whenInsertingAttributesDuplicates_attributesInGraphShouldBeUnique() {
        String testAttributeLabel = "test-attribute";
        String testAttributeValue = "test-attribute-value";

        // define the schema
        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.define(type(testAttributeLabel).sub("attribute").datatype(Graql.Token.DataType.STRING)));
            tx.commit();
        }

        // insert 3 instances with the same value
        GraqlInsert query = Graql.insert(var("x").isa(testAttributeLabel).val(testAttributeValue));

        insertConcurrently(query, 16);

        try (TransactionOLTP tx = session.transaction().read()) {
            List<ConceptMap> conceptMaps = tx.execute(Graql.match(var(testAttributeLabel).isa(testAttributeLabel).val(testAttributeValue)).get());
            assertThat(conceptMaps, hasSize(1));
        }
    }

    @Test
    public void shouldAlsoMergeHasEdgesInTheMerging1() {
        String ownedAttributeLabel = "owned-attribute";
        String ownedAttributeValue = "owned-attribute-value";

        // define the schema
        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.define(
                    type(ownedAttributeLabel).sub("attribute").datatype(Graql.Token.DataType.STRING),
                    type("owner").sub("entity").has(ownedAttributeLabel)
            ));
            tx.commit();
        }

        // insert 3 "owner" (which is an entity) and "owned-attribute". each "owner" has an "owned-attribute"
        GraqlInsert query = Graql.insert(var().isa("owner").has(ownedAttributeLabel, ownedAttributeValue));
        insertConcurrently(query, 3);

        // verify there are 3 owners linked to only 1 attribute instance
        try (TransactionOLTP tx = session.transaction().read()) {
            Set<String> owned = new HashSet<>();
            Set<String> owner = new HashSet<>();
            List<ConceptMap> conceptMaps = tx.execute(Graql.match(
                    var("owned").isa(ownedAttributeLabel).val(ownedAttributeValue),
                    var("owner").isa("owner")).get());
            for (ConceptMap conceptMap : conceptMaps) {
                owned.add(conceptMap.get("owned").asAttribute().id().getValue());
                owner.add(conceptMap.get("owner").asEntity().id().getValue());
            }

            assertThat(owned, hasSize(1));
            assertThat(owner, hasSize(3));
        }
    }

    @Test
    public void shouldAlsoMergeHasEdgesWhenMerging2() {
        String ownedAttributeLabel = "owned-attribute";
        String ownedAttributeValue = "owned-attribute-value";
        String ownerLabel = "owner";
        String ownerValue1 = "owner-value-1";
        String ownerValue2 = "owner-value-2";

        // define the schema
        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.define(
                    type(ownedAttributeLabel).sub("attribute").datatype(Graql.Token.DataType.STRING),
                    type(ownerLabel).sub("attribute").datatype(Graql.Token.DataType.STRING).has(ownedAttributeLabel)
            ));
            tx.commit();
        }

        // insert 3 "owner" and 3 "owned-attribute". each "owner" has an "owned attribute"
        GraqlInsert query1 = Graql.insert(var().isa(ownerLabel).val(ownerValue1).has(ownedAttributeLabel, ownedAttributeValue));
        GraqlInsert query2 = Graql.insert(var().isa(ownerLabel).val(ownerValue1).has(ownedAttributeLabel, ownedAttributeValue));
        GraqlInsert query3 = Graql.insert(var().isa(ownerLabel).val(ownerValue2).has(ownedAttributeLabel, ownedAttributeValue));
        insertConcurrently(Collections.list(query1, query2, query3));

        // verify
        try (TransactionOLTP tx = session.transaction().read()) {
            Set<String> owned = new HashSet<>();
            Set<String> owner = new HashSet<>();
            List<ConceptMap> conceptMaps = tx.execute(Graql.match(
                    var("owned").isa(ownedAttributeLabel).val(ownedAttributeValue),
                    var("owner").isa(ownerLabel)).get());
            for (ConceptMap conceptMap : conceptMaps) {
                owned.add(conceptMap.get("owned").asAttribute().id().getValue());
                owner.add(conceptMap.get("owner").asAttribute().id().getValue());
            }

            assertThat(owned, hasSize(1));
            assertThat(owner, hasSize(2));
        }
    }

    @Test
    public void shouldAlsoMergeHasEdgesInTheMerging3() {
        String ownedAttributeLabel = "owned-attribute";
        String ownedAttributeValue = "owned-attribute-value";

        // define the schema
        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.define(
                    type(ownedAttributeLabel).sub("attribute").datatype(Graql.Token.DataType.STRING),
                    type("owner").sub("entity").has(ownedAttributeLabel)
            ));
            tx.commit();
        }

        GraqlInsert query1 = Graql.parse("insert $owned \"" + ownedAttributeValue + "\"isa owned-attribute; $owner1 isa owner, has owned-attribute $owned; $owner2 isa owner, has owned-attribute $owned;").asInsert();
        GraqlInsert query2 = Graql.parse("insert $owned \"" + ownedAttributeValue + "\" isa owned-attribute; $owner1 isa owner, has owned-attribute $owned; $owner2 isa owner, has owned-attribute $owned;").asInsert();
        GraqlInsert query3 = Graql.parse("insert $owned \"" + ownedAttributeValue + "\" isa owned-attribute; $owner1 isa owner, has owned-attribute $owned;").asInsert();
        insertConcurrently(Collections.list(query1, query2, query3));

        // verify
        try (TransactionOLTP tx = session.transaction().read()) {
            Set<String> owned = new HashSet<>();
            Set<String> owner = new HashSet<>();
            List<ConceptMap> conceptMaps = tx.execute(Graql.match(
                    var("owned").isa(ownedAttributeLabel).val(ownedAttributeValue),
                    var("owner").isa("owner")).get());
            for (ConceptMap conceptMap : conceptMaps) {
                owned.add(conceptMap.get("owned").asAttribute().id().getValue());
                owner.add(conceptMap.get("owner").asEntity().id().getValue());
            }

            assertThat(owned, hasSize(1));
            assertThat(owner, hasSize(5));
        }
    }

    @Test
    public void shouldAlsoMergeReifiedEdgesWhenMerging() {
        String ownedAttributeLabel = "owned-attribute";
        String ownedAttributeValue = "owned-attribute-value";

        // define the schema
        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.define(
                    type(ownedAttributeLabel).sub("attribute").datatype(Graql.Token.DataType.STRING),
                    type("owner").sub("entity").has(ownedAttributeLabel)
            ));
            tx.commit();
        }

        // use the 'via' feature when inserting to force reification
        GraqlInsert query1 = Graql.parse("insert $owner isa owner, has owned-attribute '" + ownedAttributeValue + "' via $reified;").asInsert();
        GraqlInsert query2 = Graql.parse("insert $owner isa owner, has owned-attribute '" + ownedAttributeValue + "' via $reified;").asInsert();
        GraqlInsert query3 = Graql.parse("insert $owner isa owner, has owned-attribute '" + ownedAttributeValue + "' via $reified;").asInsert();
        insertConcurrently(Collections.list(query1, query2, query3));

        // verify
        try (TransactionOLTP tx = session.transaction().read()) {
            Set<String> owned = new HashSet<>();
            Set<String> owner = new HashSet<>();
            List<ConceptMap> conceptMaps = tx.execute(Graql.match(
                    var("owned").isa(ownedAttributeLabel).val(ownedAttributeValue),
                    var("owner").isa("owner")).get());
            for (ConceptMap conceptMap : conceptMaps) {
                owned.add(conceptMap.get("owned").asAttribute().id().getValue());
                owner.add(conceptMap.get("owner").asEntity().id().getValue());
            }

            assertThat(owned, hasSize(1));
            assertThat(owner, hasSize(3));
        }
    }

    @Test
    public void shouldAlsoMergeRolePlayerEdgesInTheMerging() {

        String ownedAttributeLabel = "owned-attribute";
        String ownedAttributeValue = "owned-attribute-value";

        // define the schema
        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.define(
                    type("owner").sub("relation").relates("entity-role-player").relates("attribute-role-player"),
                    type("owned-entity").sub("entity").plays("entity-role-player"),
                    type(ownedAttributeLabel).sub("attribute").plays("attribute-role-player").datatype(Graql.Token.DataType.STRING)
            ));
            tx.commit();
        }

        // insert relations, each having an attribute as one of the role player
        GraqlInsert query1 = Graql.insert(
                var("erp").isa("owned-entity"), var("arp").isa(ownedAttributeLabel).val(ownedAttributeValue),
                var("owner").isa("owner").rel("entity-role-player", var("erp")).rel("attribute-role-player", var("arp")));
        GraqlInsert query2 = Graql.insert(
                var("erp").isa("owned-entity"), var("arp").isa(ownedAttributeLabel).val(ownedAttributeValue),
                var("owner").isa("owner").rel("entity-role-player", var("erp")).rel("attribute-role-player", var("arp")));
        GraqlInsert query3 = Graql.insert(
                var("erp").isa("owned-entity"), var("arp").isa(ownedAttributeLabel).val(ownedAttributeValue),
                var("owner").isa("owner").rel("entity-role-player", var("erp")).rel("attribute-role-player", var("arp")));
        insertConcurrently(Collections.list(query1, query2, query3));

        // verify
        try (TransactionOLTP tx = session.transaction().read()) {
            List<ConceptMap> conceptMaps = tx.execute(Graql.match(var("owner").isa("owner")
                    .rel("attribute-role-player", var("arp"))
            ).get());
            Set<String> owner = new HashSet<>();
            Set<String> arp = new HashSet<>();
            for (ConceptMap conceptMap : conceptMaps) {
                owner.add(conceptMap.get("owner").asRelation().id().getValue());
                arp.add(conceptMap.get("arp").asAttribute().id().getValue());
            }

            assertThat(arp, hasSize(1));
            assertThat(owner, hasSize(3));
        }
    }

    @Test
    public void whenDeletingAndReaddingSameAttributeInDifferentTx_attributesCacheIsInSyncAndShouldNotTryToMerge() {
        String testAttributeLabel = "test-attribute";
        String testAttributeValue = "test-attribute-value";
        String index = Schema.generateAttributeIndex(Label.of(testAttributeLabel), testAttributeValue);

        // define the schema
        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.define(type(testAttributeLabel).sub("attribute").datatype(Graql.Token.DataType.STRING)));
            tx.commit();
        }

        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.insert(var("x").isa(testAttributeLabel).val(testAttributeValue)));
            tx.commit();
        }

        assertNotNull(session.attributesCache().getIfPresent(index));


        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.match(var("x").isa(testAttributeLabel).val(testAttributeValue)).delete());
            tx.commit();
        }

        assertNull(session.attributesCache().getIfPresent(index));

        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.insert(var("x").isa(testAttributeLabel).val(testAttributeValue)));
            tx.commit();
        }

        assertNotNull(session.attributesCache().getIfPresent(index));

    }

    @Test
    public void whenDeletingAndReaddingSameAttributeInSameTx_shouldNotTryToMergeAndThereShouldBeOneAttributeNodeWithDifferentId() {
        String testAttributeLabel = "test-attribute";
        String testAttributeValue = "test-attribute-value";
        String index = Schema.generateAttributeIndex(Label.of(testAttributeLabel), testAttributeValue);

        // define the schema
        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.define(type(testAttributeLabel).sub("attribute").datatype(Graql.Token.DataType.STRING)));
            tx.commit();
        }
        String oldAttributeId;
        try (TransactionOLTP tx = session.transaction().write()) {
            List<ConceptMap> x = tx.execute(Graql.insert(var("x").isa(testAttributeLabel).val(testAttributeValue)));
            oldAttributeId = x.get(0).get("x").id().getValue();
            tx.commit();
        }

        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.match(var("x").isa(testAttributeLabel).val(testAttributeValue)).delete());
            tx.execute(Graql.insert(var("x").isa(testAttributeLabel).val(testAttributeValue)));
            tx.commit();
        }
        try (TransactionOLTP tx = session.transaction().write()) {
            List<ConceptMap> attribute = tx.execute(Graql.parse("match $x isa test-attribute; get;").asGet());
            assertEquals(1, attribute.size());
            String newAttributeId = attribute.get(0).get("x").id().getValue();
            assertNotEquals(newAttributeId, oldAttributeId);
            assertEquals(ConceptId.of(newAttributeId), session.attributesCache().getIfPresent(index));
        }
    }

    @Test
    public void whenAddingAndDeletingSameAttributeInSameTx_thereShouldBeNoAttributeIndexInAttributesCache() {
        String testAttributeLabel = "test-attribute";
        String testAttributeValue = "test-attribute-value";
        String index = Schema.generateAttributeIndex(Label.of(testAttributeLabel), testAttributeValue);

        // define the schema
        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.define(type(testAttributeLabel).sub("attribute").datatype(Graql.Token.DataType.STRING)));
            tx.commit();
        }

        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.insert(var("x").isa(testAttributeLabel).val(testAttributeValue)));
            tx.execute(Graql.match(var("x").isa(testAttributeLabel).val(testAttributeValue)).delete());
            tx.commit();
            assertNull(session.attributesCache().getIfPresent(index));
        }
        try (TransactionOLTP tx = session.transaction().write()) {
            List<ConceptMap> attribute = tx.execute(Graql.parse("match $x isa test-attribute; get;").asGet());
            assertEquals(0, attribute.size());
        }
    }

    private void insertConcurrently(GraqlInsert query, int repetitions) {
        List<GraqlInsert> queries = new ArrayList<>();
        for (int i = 0; i < repetitions; i++) {
            queries.add(query);
        }
        insertConcurrently(queries);
    }

    private void insertConcurrently(Collection<GraqlInsert> queries) {
        // use latch to make sure all threads will insert a new attribute instance
        CountDownLatch commitLatch = new CountDownLatch(queries.size());
        ExecutorService executorService = Executors.newFixedThreadPool(queries.size());
        List<Future> futures = new ArrayList<>();
        queries.forEach(query -> {
            futures.add(executorService.submit(() -> {
                TransactionOLTP tx = session.transaction().write();
                tx.execute(query);
                commitLatch.countDown();
                try {
                    commitLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                tx.commit();
            }));
        });
        for (Future future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }
    }
}