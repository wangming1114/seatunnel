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

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.TableIdentifier;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.common.utils.JdbcUrlUtil;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.AbstractJdbcCatalog;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.utils.CatalogUtils;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.gbase8a.Gbase8aTypeConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.gbase8a.Gbase8aTypeMapper;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Catalog for GBase 8a. It enables {@code schema_save_mode} (auto create / recreate table) and
 * {@code data_save_mode} (truncate / error when data exists / custom SQL) for the JDBC sink.
 */
@Slf4j
public class Gbase8aCatalog extends AbstractJdbcCatalog {

    private static final String SELECT_COLUMNS_SQL_TEMPLATE =
            "SELECT * FROM information_schema.columns"
                    + " WHERE table_schema = '%s' AND table_name = '%s'"
                    + " ORDER BY ordinal_position ASC";

    private static final String SELECT_DATABASE_EXISTS =
            "SELECT schema_name FROM information_schema.schemata WHERE schema_name = '%s'";

    private static final String SELECT_TABLE_EXISTS =
            "SELECT table_schema, table_name FROM information_schema.tables"
                    + " WHERE table_schema = '%s' AND table_name = '%s'";

    public Gbase8aCatalog(
            String catalogName,
            String username,
            String pwd,
            JdbcUrlUtil.UrlInfo urlInfo,
            String driverClass) {
        super(catalogName, username, pwd, urlInfo, null, driverClass);
    }

    @Override
    protected String getDatabaseWithConditionSql(String databaseName) {
        return String.format(SELECT_DATABASE_EXISTS, escapeLiteral(databaseName));
    }

    @Override
    protected String getTableWithConditionSql(TablePath tablePath) {
        return String.format(
                SELECT_TABLE_EXISTS,
                escapeLiteral(tablePath.getDatabaseName()),
                escapeLiteral(tablePath.getTableName()));
    }

    @Override
    protected String getListDatabaseSql() {
        return "SHOW DATABASES;";
    }

    @Override
    protected String getListTableSql(String databaseName) {
        return "SHOW TABLES;";
    }

    @Override
    protected String getTableName(ResultSet rs) throws SQLException {
        return rs.getString(1);
    }

    @Override
    protected String getTableName(TablePath tablePath) {
        return tablePath.getTableName();
    }

    @Override
    protected String getSelectColumnsSql(TablePath tablePath) {
        return String.format(
                SELECT_COLUMNS_SQL_TEMPLATE,
                escapeLiteral(tablePath.getDatabaseName()),
                escapeLiteral(tablePath.getTableName()));
    }

    @Override
    protected TableIdentifier getTableIdentifier(TablePath tablePath) {
        return TableIdentifier.of(
                catalogName, tablePath.getDatabaseName(), tablePath.getTableName());
    }

    @Override
    protected Column buildColumn(ResultSet resultSet) throws SQLException {
        String columnName = resultSet.getString("COLUMN_NAME");
        // e.g. decimal(10,2)
        String columnType = resultSet.getString("COLUMN_TYPE");
        // e.g. decimal
        String dataType = resultSet.getString("DATA_TYPE").toUpperCase();
        String comment = resultSet.getString("COLUMN_COMMENT");
        Object defaultValue = resultSet.getObject("COLUMN_DEFAULT");
        boolean isNullable = "YES".equalsIgnoreCase(resultSet.getString("IS_NULLABLE"));
        long numberPrecision = resultSet.getLong("NUMERIC_PRECISION");
        int numberScale = resultSet.getInt("NUMERIC_SCALE");
        long charOctetLength = resultSet.getLong("CHARACTER_OCTET_LENGTH");

        BasicTypeDefine typeDefine =
                BasicTypeDefine.builder()
                        .name(columnName)
                        .columnType(columnType)
                        .dataType(dataType)
                        .length(Math.max(charOctetLength, numberPrecision))
                        .precision(numberPrecision)
                        .scale(numberScale)
                        .nullable(isNullable)
                        .defaultValue(defaultValue)
                        .comment(comment)
                        .build();
        return Gbase8aTypeConverter.INSTANCE.convert(typeDefine);
    }

    @Override
    protected String getCreateTableSql(
            TablePath tablePath, CatalogTable table, boolean createIndex) {
        if (createIndex) {
            log.info(
                    "GBase 8a does not enforce primary key or unique constraints, "
                            + "index creation is skipped for table {}",
                    tablePath);
        }
        return new Gbase8aCreateTableSqlBuilder(table).build(tablePath);
    }

    @Override
    protected String getDropTableSql(TablePath tablePath) {
        return String.format("DROP TABLE %s;", quotedTable(tablePath));
    }

    @Override
    protected String getCreateDatabaseSql(String databaseName) {
        return String.format("CREATE DATABASE `%s`;", databaseName);
    }

    @Override
    protected String getDropDatabaseSql(String databaseName) {
        return String.format("DROP DATABASE `%s`;", databaseName);
    }

    @Override
    protected String getTruncateTableSql(TablePath tablePath) {
        return String.format("TRUNCATE TABLE %s;", quotedTable(tablePath));
    }

    @Override
    protected String getExistDataSql(TablePath tablePath) {
        return String.format("SELECT 1 FROM %s LIMIT 1;", quotedTable(tablePath));
    }

    @Override
    public CatalogTable getTable(String sqlQuery) throws SQLException {
        Connection defaultConnection = getConnection(defaultUrl);
        return CatalogUtils.getCatalogTable(defaultConnection, sqlQuery, new Gbase8aTypeMapper());
    }

    private static String quotedTable(TablePath tablePath) {
        return String.format("`%s`.`%s`", tablePath.getDatabaseName(), tablePath.getTableName());
    }

    private static String escapeLiteral(String value) {
        return value == null ? null : value.replace("'", "''");
    }
}
