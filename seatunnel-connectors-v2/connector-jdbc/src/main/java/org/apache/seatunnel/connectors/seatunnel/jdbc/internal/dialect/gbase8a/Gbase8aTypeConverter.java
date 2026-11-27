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

import org.apache.seatunnel.api.table.catalog.Column;
import org.apache.seatunnel.api.table.catalog.PhysicalColumn;
import org.apache.seatunnel.api.table.converter.BasicTypeDefine;
import org.apache.seatunnel.api.table.converter.TypeConverter;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.DecimalType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.PrimitiveByteArrayType;
import org.apache.seatunnel.common.exception.CommonError;
import org.apache.seatunnel.connectors.seatunnel.common.source.TypeDefineUtils;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;

import com.google.auto.service.AutoService;
import lombok.extern.slf4j.Slf4j;

/**
 * Converts column types between GBase 8a and SeaTunnel.
 *
 * <p>GBase 8a is MySQL-protocol compatible but has a smaller type system: no unsigned numbers, no
 * ENUM/SET/JSON, VARCHAR is limited to 10922 characters and DECIMAL to precision 65.
 */
@Slf4j
@AutoService(TypeConverter.class)
public class Gbase8aTypeConverter implements TypeConverter<BasicTypeDefine> {

    // -------------------------number----------------------------
    public static final String GBASE8A_BIT = "BIT";
    public static final String GBASE8A_BOOL = "BOOL";
    public static final String GBASE8A_BOOLEAN = "BOOLEAN";
    public static final String GBASE8A_TINYINT = "TINYINT";
    public static final String GBASE8A_SMALLINT = "SMALLINT";
    public static final String GBASE8A_MEDIUMINT = "MEDIUMINT";
    public static final String GBASE8A_INT = "INT";
    public static final String GBASE8A_INTEGER = "INTEGER";
    public static final String GBASE8A_BIGINT = "BIGINT";
    public static final String GBASE8A_FLOAT = "FLOAT";
    public static final String GBASE8A_REAL = "REAL";
    public static final String GBASE8A_DOUBLE = "DOUBLE";
    public static final String GBASE8A_DECIMAL = "DECIMAL";
    public static final String GBASE8A_NUMERIC = "NUMERIC";

    // -------------------------string----------------------------
    public static final String GBASE8A_CHAR = "CHAR";
    public static final String GBASE8A_VARCHAR = "VARCHAR";
    public static final String GBASE8A_TEXT = "TEXT";
    public static final String GBASE8A_MEDIUMTEXT = "MEDIUMTEXT";
    public static final String GBASE8A_LONGTEXT = "LONGTEXT";

    // ------------------------------time-------------------------
    public static final String GBASE8A_DATE = "DATE";
    public static final String GBASE8A_TIME = "TIME";
    public static final String GBASE8A_DATETIME = "DATETIME";
    public static final String GBASE8A_TIMESTAMP = "TIMESTAMP";

    // ------------------------------binary-----------------------
    public static final String GBASE8A_BINARY = "BINARY";
    public static final String GBASE8A_VARBINARY = "VARBINARY";
    public static final String GBASE8A_BLOB = "BLOB";
    public static final String GBASE8A_LONGBLOB = "LONGBLOB";

    /** SeaTunnel decimal precision upper bound. */
    public static final int DEFAULT_PRECISION = 38;

    public static final int DEFAULT_SCALE = 18;
    public static final int MAX_PRECISION = 65;
    public static final int MAX_SCALE = 30;
    /** Max VARCHAR length in characters (32767 bytes / 3 bytes per utf8 character). */
    public static final long MAX_VARCHAR_LENGTH = 10922;

    public static final long MAX_BLOB_LENGTH = 65535;

    public static final Gbase8aTypeConverter INSTANCE = new Gbase8aTypeConverter();

    @Override
    public String identifier() {
        return DatabaseIdentifier.GBASE_8A;
    }

    /**
     * Converts a GBase 8a column definition to a SeaTunnel column.
     *
     * @param typeDefine GBase 8a column definition; {@code length} is in bytes for character types
     * @return SeaTunnel column
     */
    @Override
    public Column convert(BasicTypeDefine typeDefine) {
        PhysicalColumn.PhysicalColumnBuilder builder =
                PhysicalColumn.builder()
                        .name(typeDefine.getName())
                        .sourceType(typeDefine.getColumnType())
                        .nullable(typeDefine.isNullable())
                        .defaultValue(typeDefine.getDefaultValue())
                        .comment(typeDefine.getComment());

        String dataType = typeDefine.getDataType().toUpperCase();
        switch (dataType) {
            case GBASE8A_BOOL:
            case GBASE8A_BOOLEAN:
                builder.dataType(BasicType.BOOLEAN_TYPE);
                break;
            case GBASE8A_BIT:
                if (typeDefine.getLength() == null || typeDefine.getLength() <= 1) {
                    builder.dataType(BasicType.BOOLEAN_TYPE);
                } else {
                    builder.dataType(PrimitiveByteArrayType.INSTANCE);
                    // BIT(M) holds M bits, round up to bytes
                    builder.columnLength((typeDefine.getLength() + 7) / 8);
                }
                break;
            case GBASE8A_TINYINT:
                builder.dataType(BasicType.BYTE_TYPE);
                break;
            case GBASE8A_SMALLINT:
                builder.dataType(BasicType.SHORT_TYPE);
                break;
            case GBASE8A_MEDIUMINT:
            case GBASE8A_INT:
            case GBASE8A_INTEGER:
                builder.dataType(BasicType.INT_TYPE);
                break;
            case GBASE8A_BIGINT:
                builder.dataType(BasicType.LONG_TYPE);
                break;
            case GBASE8A_FLOAT:
                builder.dataType(BasicType.FLOAT_TYPE);
                break;
            case GBASE8A_REAL:
            case GBASE8A_DOUBLE:
                builder.dataType(BasicType.DOUBLE_TYPE);
                break;
            case GBASE8A_DECIMAL:
            case GBASE8A_NUMERIC:
                DecimalType decimalType;
                if (typeDefine.getPrecision() == null || typeDefine.getPrecision() <= 0) {
                    decimalType = new DecimalType(DEFAULT_PRECISION, DEFAULT_SCALE);
                } else if (typeDefine.getPrecision() > DEFAULT_PRECISION) {
                    log.warn(
                            "Column {} type {}({},{}) exceeds the SeaTunnel decimal precision {}, "
                                    + "it will be converted to decimal({},{}) and may overflow.",
                            typeDefine.getName(),
                            dataType,
                            typeDefine.getPrecision(),
                            typeDefine.getScale(),
                            DEFAULT_PRECISION,
                            DEFAULT_PRECISION,
                            DEFAULT_SCALE);
                    decimalType = new DecimalType(DEFAULT_PRECISION, DEFAULT_SCALE);
                } else {
                    decimalType =
                            new DecimalType(
                                    typeDefine.getPrecision().intValue(),
                                    typeDefine.getScale() == null ? 0 : typeDefine.getScale());
                }
                builder.dataType(decimalType);
                builder.columnLength((long) decimalType.getPrecision());
                builder.scale(decimalType.getScale());
                break;
            case GBASE8A_CHAR:
            case GBASE8A_VARCHAR:
                builder.dataType(BasicType.STRING_TYPE);
                if (typeDefine.getLength() == null || typeDefine.getLength() <= 0) {
                    builder.columnLength(TypeDefineUtils.charTo4ByteLength(MAX_VARCHAR_LENGTH));
                } else {
                    builder.columnLength(typeDefine.getLength());
                }
                break;
            case GBASE8A_TEXT:
            case GBASE8A_MEDIUMTEXT:
            case GBASE8A_LONGTEXT:
                builder.dataType(BasicType.STRING_TYPE);
                if (typeDefine.getLength() != null && typeDefine.getLength() > 0) {
                    builder.columnLength(typeDefine.getLength());
                }
                break;
            case GBASE8A_BINARY:
            case GBASE8A_VARBINARY:
            case GBASE8A_BLOB:
            case GBASE8A_LONGBLOB:
                builder.dataType(PrimitiveByteArrayType.INSTANCE);
                if (typeDefine.getLength() != null && typeDefine.getLength() > 0) {
                    builder.columnLength(typeDefine.getLength());
                }
                break;
            case GBASE8A_DATE:
                builder.dataType(LocalTimeType.LOCAL_DATE_TYPE);
                break;
            case GBASE8A_TIME:
                builder.dataType(LocalTimeType.LOCAL_TIME_TYPE);
                break;
            case GBASE8A_DATETIME:
            case GBASE8A_TIMESTAMP:
                builder.dataType(LocalTimeType.LOCAL_DATE_TIME_TYPE);
                break;
            default:
                throw CommonError.convertToSeaTunnelTypeError(
                        DatabaseIdentifier.GBASE_8A, dataType, typeDefine.getName());
        }
        return builder.build();
    }

    /**
     * Converts a SeaTunnel column to a GBase 8a column definition, used to build CREATE TABLE and
     * ALTER TABLE statements.
     *
     * @param column SeaTunnel column; {@code columnLength} is in bytes for string and binary types
     * @return GBase 8a column definition
     */
    @Override
    public BasicTypeDefine reconvert(Column column) {
        BasicTypeDefine.BasicTypeDefineBuilder builder =
                BasicTypeDefine.builder()
                        .name(column.getName())
                        .nullable(column.isNullable())
                        .comment(column.getComment())
                        .defaultValue(column.getDefaultValue());
        switch (column.getDataType().getSqlType()) {
            case BOOLEAN:
                builder.columnType(GBASE8A_TINYINT + "(1)");
                builder.dataType(GBASE8A_TINYINT);
                builder.length(1L);
                break;
            case TINYINT:
                builder.columnType(GBASE8A_TINYINT);
                builder.dataType(GBASE8A_TINYINT);
                break;
            case SMALLINT:
                builder.columnType(GBASE8A_SMALLINT);
                builder.dataType(GBASE8A_SMALLINT);
                break;
            case INT:
                builder.columnType(GBASE8A_INT);
                builder.dataType(GBASE8A_INT);
                break;
            case BIGINT:
                builder.columnType(GBASE8A_BIGINT);
                builder.dataType(GBASE8A_BIGINT);
                break;
            case FLOAT:
                builder.columnType(GBASE8A_FLOAT);
                builder.dataType(GBASE8A_FLOAT);
                break;
            case DOUBLE:
                builder.columnType(GBASE8A_DOUBLE);
                builder.dataType(GBASE8A_DOUBLE);
                break;
            case DECIMAL:
                reconvertDecimal(column, builder);
                break;
            case STRING:
                Long stringLength = column.getColumnLength();
                if (stringLength == null || stringLength <= 0) {
                    builder.columnType(GBASE8A_TEXT);
                    builder.dataType(GBASE8A_TEXT);
                } else if (stringLength <= MAX_VARCHAR_LENGTH) {
                    builder.columnType(String.format("%s(%s)", GBASE8A_VARCHAR, stringLength));
                    builder.dataType(GBASE8A_VARCHAR);
                    builder.length(stringLength);
                } else {
                    builder.columnType(GBASE8A_TEXT);
                    builder.dataType(GBASE8A_TEXT);
                }
                break;
            case BYTES:
                Long bytesLength = column.getColumnLength();
                if (bytesLength != null && bytesLength > 0 && bytesLength <= MAX_BLOB_LENGTH) {
                    builder.columnType(GBASE8A_BLOB);
                    builder.dataType(GBASE8A_BLOB);
                } else {
                    builder.columnType(GBASE8A_LONGBLOB);
                    builder.dataType(GBASE8A_LONGBLOB);
                }
                break;
            case DATE:
                builder.columnType(GBASE8A_DATE);
                builder.dataType(GBASE8A_DATE);
                break;
            case TIME:
                builder.columnType(GBASE8A_TIME);
                builder.dataType(GBASE8A_TIME);
                break;
            case TIMESTAMP:
            case TIMESTAMP_TZ:
                // GBase 8a TIMESTAMP has a narrow range and auto-update semantics, use DATETIME.
                // TIMESTAMP_TZ values are written as local date-time, see Gbase8aJdbcRowConverter
                builder.columnType(GBASE8A_DATETIME);
                builder.dataType(GBASE8A_DATETIME);
                break;
            default:
                throw CommonError.convertToConnectorTypeError(
                        DatabaseIdentifier.GBASE_8A,
                        column.getDataType().getSqlType().name(),
                        column.getName());
        }
        return builder.build();
    }

    private void reconvertDecimal(Column column, BasicTypeDefine.BasicTypeDefineBuilder builder) {
        DecimalType decimalType = (DecimalType) column.getDataType();
        long precision = decimalType.getPrecision();
        int scale = decimalType.getScale();
        if (precision <= 0) {
            precision = DEFAULT_PRECISION;
            scale = DEFAULT_SCALE;
        } else if (precision > MAX_PRECISION) {
            scale = (int) Math.max(0, scale - (precision - MAX_PRECISION));
            precision = MAX_PRECISION;
        }
        if (scale < 0) {
            scale = 0;
        } else if (scale > MAX_SCALE) {
            scale = MAX_SCALE;
        }
        if (scale > precision) {
            scale = (int) precision;
        }
        if (precision != decimalType.getPrecision() || scale != decimalType.getScale()) {
            log.warn(
                    "The decimal column {} type decimal({},{}) is out of range for GBase 8a, "
                            + "it will be converted to decimal({},{})",
                    column.getName(),
                    decimalType.getPrecision(),
                    decimalType.getScale(),
                    precision,
                    scale);
        }
        builder.columnType(String.format("%s(%s,%s)", GBASE8A_DECIMAL, precision, scale));
        builder.dataType(GBASE8A_DECIMAL);
        builder.precision(precision);
        builder.scale(scale);
    }
}
