/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.gbase8a;

import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.JdbcDialect;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.dialectenum.FieldIdeEnum;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class Gbase8aDialectTest {

    @Test
    void testFactory() {
        Gbase8aDialectFactory factory = new Gbase8aDialectFactory();
        Assertions.assertTrue(factory.acceptsURL("jdbc:gbase://localhost:5258/test"));
        Assertions.assertFalse(factory.acceptsURL("jdbc:mysql://localhost:3306/test"));
        JdbcDialect dialect = factory.create("", FieldIdeEnum.UPPERCASE.getValue());
        Assertions.assertEquals("`ID`", dialect.quoteIdentifier("id"));
    }

    @Test
    void testIdentifiers() {
        Gbase8aDialect dialect = new Gbase8aDialect();
        Assertions.assertEquals("`order`", dialect.quoteIdentifier("order"));
        Assertions.assertEquals("`db`", dialect.quoteDatabaseIdentifier("db"));
        Assertions.assertEquals("`db`.`tbl`", dialect.tableIdentifier(dialect.parse("db.tbl")));

        TablePath tablePath = dialect.parse("db.tbl");
        Assertions.assertEquals("db", tablePath.getDatabaseName());
        Assertions.assertNull(tablePath.getSchemaName());
        Assertions.assertEquals("tbl", tablePath.getTableName());
        Assertions.assertEquals("tbl", dialect.extractTableName(tablePath));
    }

    @Test
    void testInsertStatement() {
        Gbase8aDialect dialect = new Gbase8aDialect();
        Assertions.assertEquals(
                "INSERT INTO `db`.`tbl` (`id`, `name`) VALUES (:id, :name)",
                dialect.getInsertIntoStatement("db", "tbl", new String[] {"id", "name"}));
        Assertions.assertFalse(
                dialect.getUpsertStatement("db", "tbl", new String[] {"id"}, new String[] {"id"})
                        .isPresent());
    }

    @Test
    void testTypeConverter() {
        Assertions.assertSame(
                Gbase8aTypeConverter.INSTANCE, new Gbase8aDialect().getTypeConverter());
    }
}
