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

package org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.gbase8a;

import org.apache.seatunnel.shade.com.google.common.collect.Lists;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.catalog.PrimaryKey;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.catalog.TableSchema;
import org.apache.seatunnel.api.table.factory.CatalogFactory;
import org.apache.seatunnel.api.table.factory.FactoryUtil;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.DecimalType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.PrimitiveByteArrayType;
import org.apache.seatunnel.common.utils.JdbcUrlUtil;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class Gbase8aCreateTableSqlBuilderTest {

    private static final TablePath TABLE_PATH = TablePath.of("test_db", null, "test_table");

    private static CatalogTable catalogTable(String catalogName, Map<String, String> options) {
        TableSchema tableSchema =
                TableSchema.builder()
                        .column(PhysicalColumn.of("id", BasicType.LONG_TYPE, 22, false, null, "id"))
                        .column(
                                PhysicalColumn.of(
                                        "name",
                                        BasicType.STRING_TYPE,
                                        128,
                                        true,
                                        null,
                                        "it's name"))
                        .column(
                                PhysicalColumn.of(
                                        "note",
                                        BasicType.STRING_TYPE,
                                        (Long) null,
                                        true,
                                        null,
                                        null))
                        .column(
                                PhysicalColumn.of(
                                        "price", new DecimalType(10, 2), 10, true, null, null))
                        .column(
                                PhysicalColumn.of(
                                        "data",
                                        PrimitiveByteArrayType.INSTANCE,
                                        (Long) null,
                                        true,
                                        null,
                                        null))
                        .column(
                                PhysicalColumn.of(
                                        "create_time",
                                        LocalTimeType.LOCAL_DATE_TIME_TYPE,
                                        (Long) null,
                                        true,
                                        null,
                                        null))
                        .primaryKey(PrimaryKey.of("pk_id", Lists.newArrayList("id")))
                        .build();
        return CatalogTable.of(
                TableIdentifier.of(catalogName, TABLE_PATH),
                tableSchema,
                options,
                Collections.emptyList(),
                "test comment");
    }

    @Test
    void testBuildFromOtherCatalog() {
        String sql =
                new Gbase8aCreateTableSqlBuilder(
                                catalogTable(DatabaseIdentifier.MYSQL, new HashMap<>()))
                        .build(TABLE_PATH);
        String expected =
                "CREATE TABLE `test_db`.`test_table` (\n"
                        + "\t`id` BIGINT NOT NULL COMMENT 'id',\n"
                        + "\t`name` VARCHAR(128) COMMENT 'it''s name',\n"
                        + "\t`note` TEXT,\n"
                        + "\t`price` DECIMAL(10,2),\n"
                        + "\t`data` LONGBLOB,\n"
                        + "\t`create_time` DATETIME\n"
                        + ") COMMENT = 'test comment';";
        Assertions.assertEquals(expected, sql);
        // GBase 8a does not enforce constraints, so no PRIMARY KEY clause is generated
        Assertions.assertFalse(sql.contains("PRIMARY KEY"));
    }

    @Test
    void testBuildWithFieldIde() {
        Map<String, String> options = new HashMap<>();
        options.put("fieldIde", "uppercase");
        String sql =
                new Gbase8aCreateTableSqlBuilder(catalogTable(DatabaseIdentifier.MYSQL, options))
                        .build(TABLE_PATH);
        Assertions.assertTrue(
                sql.startsWith("CREATE TABLE `test_db`.`TEST_TABLE` (\n\t`ID` BIGINT"));
    }

    @Test
    void testReuseSourceTypeFromGbase8a() {
        TableSchema tableSchema =
                TableSchema.builder()
                        .column(
                                PhysicalColumn.builder()
                                        .name("c1")
                                        .dataType(BasicType.STRING_TYPE)
                                        .columnLength(40L)
                                        .sourceType("varchar(10)")
                                        .nullable(true)
                                        .build())
                        .column(
                                PhysicalColumn.builder()
                                        .name("c2")
                                        .dataType(BasicType.STRING_TYPE)
                                        .columnLength(40L)
                                        // query mode metadata only reports the bare type name
                                        .sourceType("VARCHAR")
                                        .nullable(true)
                                        .build())
                        .build();
        CatalogTable table =
                CatalogTable.of(
                        TableIdentifier.of(DatabaseIdentifier.GBASE_8A, TABLE_PATH),
                        tableSchema,
                        new HashMap<>(),
                        Collections.emptyList(),
                        null);
        Assertions.assertEquals(
                "CREATE TABLE `test_db`.`test_table` (\n"
                        + "\t`c1` varchar(10),\n"
                        + "\t`c2` VARCHAR(40)\n"
                        + ");",
                new Gbase8aCreateTableSqlBuilder(table).build(TABLE_PATH));
    }

    @Test
    void testCatalogSql() {
        Gbase8aCatalog catalog =
                new Gbase8aCatalog(
                        DatabaseIdentifier.GBASE_8A,
                        "root",
                        "root",
                        JdbcUrlUtil.getUrlInfo("jdbc:gbase://localhost:5258/test_db"),
                        "com.gbase.jdbc.Driver");
        Assertions.assertEquals(
                "TRUNCATE TABLE `test_db`.`test_table`;", catalog.getTruncateTableSql(TABLE_PATH));
        Assertions.assertEquals(
                "SELECT 1 FROM `test_db`.`test_table` LIMIT 1;",
                catalog.getExistDataSql(TABLE_PATH));
        Assertions.assertEquals(
                "DROP TABLE `test_db`.`test_table`;", catalog.getDropTableSql(TABLE_PATH));
        Assertions.assertEquals(
                "CREATE DATABASE `test_db`;", catalog.getCreateDatabaseSql("test_db"));
    }

    @Test
    void testCatalogFactoryDiscovered() {
        CatalogFactory factory =
                FactoryUtil.discoverFactory(
                        Thread.currentThread().getContextClassLoader(),
                        CatalogFactory.class,
                        DatabaseIdentifier.GBASE_8A);
        Assertions.assertTrue(factory instanceof Gbase8aCatalogFactory);
        Map<String, Object> config = new HashMap<>();
        config.put("url", "jdbc:gbase://localhost:5258/test_db");
        config.put("username", "root");
        config.put("password", "root");
        config.put("driver", "com.gbase.jdbc.Driver");
        Assertions.assertTrue(
                factory.createCatalog(DatabaseIdentifier.GBASE_8A, ReadonlyConfig.fromMap(config))
                        instanceof Gbase8aCatalog);
    }
}
