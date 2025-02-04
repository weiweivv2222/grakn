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

package grakn.core.graql.query;

import grakn.core.concept.answer.ConceptMap;
import grakn.core.concept.type.AttributeType;
import grakn.core.rule.GraknTestServer;
import grakn.core.server.exception.TransactionException;
import grakn.core.server.session.Session;
import grakn.core.server.session.TransactionOLTP;
import graql.lang.Graql;
import graql.lang.pattern.Pattern;
import graql.lang.query.GraqlInsert;
import graql.lang.query.MatchClause;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class NumberCastingIT {

    @ClassRule
    public static final GraknTestServer graknServer = new GraknTestServer();
    public static Session session;

    @Before
    public void newSession() {
        session = graknServer.sessionWithNewKeyspace();
        try (TransactionOLTP tx = session.transaction().write()) {
            tx.putAttributeType("attr-long", AttributeType.DataType.LONG);
            tx.putAttributeType("attr-double", AttributeType.DataType.DOUBLE);
            tx.putAttributeType("attr-date", AttributeType.DataType.DATE);
            tx.commit();
        }
    }

    @After
    public void closeSession() {
        session.close();
    }

    private void verifyWrite(Session session, Pattern pattern) {
        GraqlInsert insert = Graql.insert(pattern.statements());
        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(insert);
            tx.commit();
        }
    }

    private void verifyRead(Session session, Pattern pattern) {
        MatchClause match = Graql.match(pattern.statements());
        try (TransactionOLTP tx = session.transaction().write()) {
            List<ConceptMap> answers = tx.execute(match);
            assertFalse(answers.isEmpty());
        }
    }

    private void cleanup(Session session, Pattern pattern) {
        MatchClause match = Graql.match(pattern.statements());
        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(match.delete());
            tx.commit();
        }
    }

    @Test
    public void whenAddressingDoubleAsInteger_ConversionHappens() {
        Pattern pattern = Graql.var("x").val(10).isa("attr-double");
        verifyWrite(session, pattern);
        verifyRead(session, pattern);
        cleanup(session, pattern);
    }

    @Test
    public void whenAddressingDoubleAsLong_ConversionHappens() {
        Pattern pattern = Graql.var("x").val(10L).isa("attr-double");
        verifyWrite(session, pattern);
        verifyRead(session, pattern);
        cleanup(session, pattern);
    }

    @Test // TODO: This test is not needed as the method `.val(...)` will cast to either Long or Double
    public void whenAddressingDoubleAsFloat_ConversionHappens() {
        Pattern pattern = Graql.var("x").val(10f).isa("attr-double");
        verifyWrite(session, pattern);
        verifyRead(session, pattern);
    }

    @Test
    public void whenAddressingLongAsInt_ConversionHappens() {
        Pattern pattern = Graql.var("x").val(10).isa("attr-long");
        verifyWrite(session, pattern);
        verifyRead(session, pattern);
        cleanup(session, pattern);
    }

    @Test
    public void whenAddressingDateAsLocalDate_ConversionHappens() {
        LocalDate now = LocalDate.now();

        Pattern pattern = Graql.parsePattern("$x " + now + " isa attr-date;");
        verifyWrite(session, pattern);
        verifyRead(session, pattern);
        cleanup(session, pattern);
    }

    @Test
    public void whenAddressingDateAsLocalDateTime_ConversionHappens() {
        LocalDateTime now = LocalDateTime.now();

        Pattern pattern = Graql.parsePattern("$x " + now + " isa attr-date;");
        verifyWrite(session, pattern);
        verifyRead(session, pattern);
        cleanup(session, pattern);
    }

    @Test
    public void whenAddressingLongAsConvertibleDouble_exceptionIsThrown() throws TransactionException {
        double value = 10.0;
        Pattern pattern = Graql.var("x").val(value).isa("attr-long");
        verifyWrite(session, pattern);
        verifyRead(session, pattern);
        cleanup(session, pattern);
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void whenAddressingDateAsNonDate_exceptionIsThrown() throws TransactionException {
        double value = 10000000.0;
        Pattern pattern = Graql.var("x").val(value).isa("attr-date");
        expectedException.expect(TransactionException.class);
        expectedException.expectMessage("The value [" + value + "] of type [Double] must be of datatype [java.time.LocalDateTime]");
        verifyWrite(session, pattern);
    }

    @Test
    public void whenAddressingDoubleAsBoolean_exceptionIsThrown() throws TransactionException {
        boolean value = true;
        Pattern pattern = Graql.var("x").val(value).isa("attr-double");
        expectedException.expect(TransactionException.class);
        expectedException.expectMessage("The value [" + value + "] of type [Boolean] must be of datatype [java.lang.Double]");
        verifyWrite(session, pattern);
    }

    @Test
    public void whenAddressingLongAsDouble_exceptionIsThrown() throws TransactionException {
        double value = 10.1;
        Pattern pattern = Graql.var("x").val(value).isa("attr-long");
        expectedException.expect(TransactionException.class);
        expectedException.expectMessage("The value [" + value + "] of type [Double] must be of datatype [java.lang.Long]");
        verifyWrite(session, pattern);
    }

    @Test
    public void compareDoubleAndLong() {
        int one_int = 1, two_int = 2;
        double one_double = 1.0, two_double = 2.0;

        try (TransactionOLTP tx = session.transaction().write()) {
            tx.execute(Graql.insert(Graql.val(one_int).isa("attr-long")));
            tx.execute(Graql.insert(Graql.val(two_double).isa("attr-double")));
            tx.commit();
        }

        List<ConceptMap> answers;
        try (TransactionOLTP tx = session.transaction().read()) {
            answers = tx.execute(Graql.match(Graql.var("x").eq(one_int).isa("attr-long")).get());
            assertEquals(1, answers.size());

            answers = tx.execute(Graql.match(Graql.var("x").eq(one_double).isa("attr-long")).get());
            assertEquals(1, answers.size());
        }

        try (TransactionOLTP tx = session.transaction().read()) {
            answers = tx.execute(Graql.match(Graql.var("x").eq(two_int).isa("attr-double")).get());
            assertEquals(1, answers.size());

            answers = tx.execute(Graql.match(Graql.var("x").eq(two_double).isa("attr-double")).get());
            assertEquals(1, answers.size());
        }

        try (TransactionOLTP tx = session.transaction().read()) {
            answers = tx.execute(Graql.match(Graql.var("x").val(one_double).isa("attr-long")).get());
            assertEquals(1, answers.size());

            answers = tx.execute(Graql.match(Graql.var("x").val(two_int).isa("attr-double")).get());
            assertEquals(1, answers.size());
        }

        try (TransactionOLTP tx = session.transaction().read()) {
            answers = tx.execute(Graql.match(Graql.var("x").gte(one_int).isa("attribute")).get());
            assertEquals(2, answers.size());

            answers = tx.execute(Graql.match(Graql.var("x").gte(one_double).isa("attribute")).get());
            assertEquals(2, answers.size());
        }

        try (TransactionOLTP tx = session.transaction().read()) {
            answers = tx.execute(Graql.match(Graql.var("x").lt(two_int).isa("attribute")).get());
            assertEquals(1, answers.size());

            answers = tx.execute(Graql.match(Graql.var("x").lt(two_double).isa("attribute")).get());
            assertEquals(1, answers.size());
        }
    }
}
