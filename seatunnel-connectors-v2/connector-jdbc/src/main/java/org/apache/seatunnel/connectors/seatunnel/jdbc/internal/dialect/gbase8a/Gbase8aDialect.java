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
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.api.table.converter.TypeConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.converter.JdbcRowConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.JdbcDialect;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.JdbcDialectTypeMapper;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.dialectenum.FieldIdeEnum;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Gbase8aDialect implements JdbcDialect {

    public String fieldIde = FieldIdeEnum.ORIGINAL.getValue();

    public Gbase8aDialect() {}

    public Gbase8aDialect(String fieldIde) {
        this.fieldIde = fieldIde;
    }

    @Override
    public String dialectName() {
        return DatabaseIdentifier.GBASE_8A;
    }

    @Override
    public JdbcRowConverter getRowConverter() {
        return new Gbase8aJdbcRowConverter();
    }

    @Override
    public TypeConverter<BasicTypeDefine> getTypeConverter() {
        return Gbase8aTypeConverter.INSTANCE;
    }

    @Override
    public JdbcDialectTypeMapper getJdbcDialectTypeMapper() {
        return new Gbase8aTypeMapper();
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "`" + getFieldIde(identifier, fieldIde) + "`";
    }

    @Override
    public String quoteDatabaseIdentifier(String identifier) {
        return "`" + identifier + "`";
    }

    @Override
    public String tableIdentifier(TablePath tablePath) {
        return tableIdentifier(tablePath.getDatabaseName(), tablePath.getTableName());
    }

    @Override
    public String extractTableName(TablePath tablePath) {
        return tablePath.getTableName();
    }

    /** GBase 8a uses MySQL style {@code database.table} paths without a schema level. */
    @Override
    public TablePath parse(String tablePath) {
        return TablePath.of(tablePath, false);
    }

    /**
     * GBase 8a does not enforce primary key or unique constraints, so an upsert statement cannot
     * rely on key conflicts. Returning empty lets the sink fall back to query-then-update/insert.
     */
    @Override
    public Optional<String> getUpsertStatement(
            String database, String tableName, String[] fieldNames, String[] pkNames) {
        return Optional.empty();
    }

    /**
     * Streams the result set row by row (MySQL protocol semantics) to avoid loading a whole split
     * into memory.
     */
    @Override
    public PreparedStatement creatPreparedStatement(
            Connection connection, String queryTemplate, int fetchSize) throws SQLException {
        PreparedStatement statement =
                connection.prepareStatement(
                        queryTemplate, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        statement.setFetchSize(Integer.MIN_VALUE);
        return statement;
    }

    @Override
    public Map<String, String> defaultParameter() {
        Map<String, String> parameters = new HashMap<>();
        parameters.put("rewriteBatchedStatements", "true");
        return parameters;
    }
}
