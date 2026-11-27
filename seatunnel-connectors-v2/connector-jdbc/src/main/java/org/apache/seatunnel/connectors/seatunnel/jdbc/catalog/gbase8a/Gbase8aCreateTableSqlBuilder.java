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

import org.apache.seatunnel.shade.org.apache.commons.lang3.StringUtils;

import org.apache.seatunnel.api.table.catalog.CatalogTable;
import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.TablePath;
import org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.utils.CatalogUtils;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.gbase8a.Gbase8aTypeConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.seatunnel.shade.com.google.common.base.Preconditions.checkArgument;

/**
 * Builds the GBase 8a {@code CREATE TABLE} statement.
 *
 * <p>Primary key / unique / index clauses are not generated because GBase 8a (MPP) does not enforce
 * them, and MySQL-only table options (ENGINE, AUTO_INCREMENT, COLLATE) are not supported. Without
 * an explicit {@code DISTRIBUTED BY} clause GBase 8a creates a randomly distributed table.
 */
public class Gbase8aCreateTableSqlBuilder {

    /**
     * Types that are invalid without a length. Query-mode metadata only reports the bare type name,
     * so such source types are re-derived from the SeaTunnel type instead of being reused.
     */
    private static final List<String> LENGTH_REQUIRED_TYPES =
            Arrays.asList("CHAR", "VARCHAR", "BINARY", "VARBINARY", "DECIMAL", "NUMERIC");

    private final List<Column> columns;
    private final String comment;
    private final String sourceCatalogName;
    private final String fieldIde;

    public Gbase8aCreateTableSqlBuilder(CatalogTable catalogTable) {
        this.columns = catalogTable.getTableSchema().getColumns();
        checkArgument(columns != null && !columns.isEmpty(), "columns must not be empty");
        this.comment = catalogTable.getComment();
        this.sourceCatalogName = catalogTable.getCatalogName();
        this.fieldIde = catalogTable.getOptions().get("fieldIde");
    }

    public String build(TablePath tablePath) {
        String columnSqls =
                columns.stream()
                        .map(column -> "\t" + buildColumnSql(column))
                        .collect(Collectors.joining(",\n"));
        StringBuilder sql =
                new StringBuilder()
                        .append("CREATE TABLE `")
                        .append(tablePath.getDatabaseName())
                        .append("`.")
                        .append(
                                CatalogUtils.quoteIdentifier(
                                        tablePath.getTableName(), fieldIde, "`"))
                        .append(" (\n")
                        .append(columnSqls)
                        .append("\n)");
        if (StringUtils.isNotBlank(comment)) {
            sql.append(" COMMENT = '").append(escapeComment(comment)).append("'");
        }
        return sql.append(";").toString();
    }

    String buildColumnSql(Column column) {
        StringBuilder columnSql =
                new StringBuilder(CatalogUtils.quoteIdentifier(column.getName(), fieldIde, "`"))
                        .append(" ")
                        .append(resolveColumnType(column));
        if (!column.isNullable()) {
            columnSql.append(" NOT NULL");
        }
        if (StringUtils.isNotBlank(column.getComment())) {
            columnSql.append(" COMMENT '").append(escapeComment(column.getComment())).append("'");
        }
        return columnSql.toString();
    }

    private String resolveColumnType(Column column) {
        if (column.getSinkType() != null) {
            return column.getSinkType();
        }
        String sourceType = column.getSourceType();
        if (StringUtils.equalsIgnoreCase(DatabaseIdentifier.GBASE_8A, sourceCatalogName)
                && StringUtils.isNotBlank(sourceType)
                && !LENGTH_REQUIRED_TYPES.contains(sourceType.trim().toUpperCase())) {
            // same database type: keep the original definition, e.g. varchar(10)
            return sourceType;
        }
        return Gbase8aTypeConverter.INSTANCE.reconvert(column).getColumnType();
    }

    private static String escapeComment(String comment) {
        return comment.replace("\\", "\\\\").replace("'", "''");
    }
}
