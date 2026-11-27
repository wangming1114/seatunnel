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
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.DecimalType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.MapType;
import org.apache.seatunnel.api.table.type.PrimitiveByteArrayType;
import org.apache.seatunnel.common.exception.SeaTunnelRuntimeException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class Gbase8aTypeConverterTest {

    private static final Gbase8aTypeConverter CONVERTER = Gbase8aTypeConverter.INSTANCE;

    private static Column convert(String dataType, Long length, Long precision, Integer scale) {
        BasicTypeDefine typeDefine =
                BasicTypeDefine.builder()
                        .name("test")
                        .columnType(dataType)
                        .dataType(dataType)
                        .length(length)
                        .precision(precision)
                        .scale(scale)
                        .build();
        return CONVERTER.convert(typeDefine);
    }

    @Test
    void testConvertNumeric() {
        Assertions.assertEquals(
                BasicType.BYTE_TYPE, convert("tinyint", null, null, null).getDataType());
        Assertions.assertEquals(
                BasicType.SHORT_TYPE, convert("smallint", null, null, null).getDataType());
        Assertions.assertEquals(BasicType.INT_TYPE, convert("int", null, null, null).getDataType());
        Assertions.assertEquals(
                BasicType.INT_TYPE, convert("mediumint", null, null, null).getDataType());
        Assertions.assertEquals(
                BasicType.LONG_TYPE, convert("bigint", null, null, null).getDataType());
        Assertions.assertEquals(
                BasicType.FLOAT_TYPE, convert("float", null, null, null).getDataType());
        Assertions.assertEquals(
                BasicType.DOUBLE_TYPE, convert("double", null, null, null).getDataType());
        Assertions.assertEquals(
                BasicType.BOOLEAN_TYPE, convert("bit", 1L, null, null).getDataType());
    }

    @Test
    void testConvertDecimal() {
        Assertions.assertEquals(
                new DecimalType(10, 2), convert("decimal", null, 10L, 2).getDataType());
        Assertions.assertEquals(
                new DecimalType(20, 0), convert("numeric", null, 20L, null).getDataType());
        // precision over 38 falls back to the SeaTunnel default decimal
        Assertions.assertEquals(
                new DecimalType(38, 18), convert("decimal", null, 65L, 30).getDataType());
    }

    @Test
    void testConvertStringAndBinary() {
        Column varchar = convert("varchar", 40L, null, null);
        Assertions.assertEquals(BasicType.STRING_TYPE, varchar.getDataType());
        Assertions.assertEquals(40L, varchar.getColumnLength());
        Assertions.assertEquals(
                BasicType.STRING_TYPE, convert("char", 4L, null, null).getDataType());
        // TEXT is a character type in GBase 8a
        Assertions.assertEquals(
                BasicType.STRING_TYPE, convert("text", null, null, null).getDataType());
        Assertions.assertEquals(
                PrimitiveByteArrayType.INSTANCE, convert("blob", null, null, null).getDataType());
        Assertions.assertEquals(
                PrimitiveByteArrayType.INSTANCE,
                convert("longblob", null, null, null).getDataType());
    }

    @Test
    void testConvertTemporal() {
        Assertions.assertEquals(
                LocalTimeType.LOCAL_DATE_TYPE, convert("date", null, null, null).getDataType());
        Assertions.assertEquals(
                LocalTimeType.LOCAL_TIME_TYPE, convert("time", null, null, null).getDataType());
        Assertions.assertEquals(
                LocalTimeType.LOCAL_DATE_TIME_TYPE,
                convert("datetime", null, null, null).getDataType());
        Assertions.assertEquals(
                LocalTimeType.LOCAL_DATE_TIME_TYPE,
                convert("timestamp", null, null, null).getDataType());
    }

    @Test
    void testConvertUnsupported() {
        Assertions.assertThrows(
                SeaTunnelRuntimeException.class, () -> convert("geometry", null, null, null));
    }

    private static String reconvert(Column column) {
        return CONVERTER.reconvert(column).getColumnType();
    }

    private static Column column(
            org.apache.seatunnel.api.table.type.SeaTunnelDataType<?> type, Long length) {
        return PhysicalColumn.builder().name("test").dataType(type).columnLength(length).build();
    }

    @Test
    void testReconvert() {
        Assertions.assertEquals("TINYINT(1)", reconvert(column(BasicType.BOOLEAN_TYPE, null)));
        Assertions.assertEquals("TINYINT", reconvert(column(BasicType.BYTE_TYPE, null)));
        Assertions.assertEquals("SMALLINT", reconvert(column(BasicType.SHORT_TYPE, null)));
        Assertions.assertEquals("INT", reconvert(column(BasicType.INT_TYPE, null)));
        Assertions.assertEquals("BIGINT", reconvert(column(BasicType.LONG_TYPE, null)));
        Assertions.assertEquals("FLOAT", reconvert(column(BasicType.FLOAT_TYPE, null)));
        Assertions.assertEquals("DOUBLE", reconvert(column(BasicType.DOUBLE_TYPE, null)));
        Assertions.assertEquals("DATE", reconvert(column(LocalTimeType.LOCAL_DATE_TYPE, null)));
        Assertions.assertEquals("TIME", reconvert(column(LocalTimeType.LOCAL_TIME_TYPE, null)));
        Assertions.assertEquals(
                "DATETIME", reconvert(column(LocalTimeType.LOCAL_DATE_TIME_TYPE, null)));
        // MySQL TIMESTAMP is read as TIMESTAMP_TZ, GBase 8a has no time zone aware type
        Assertions.assertEquals(
                "DATETIME", reconvert(column(LocalTimeType.OFFSET_DATE_TIME_TYPE, null)));
    }

    @Test
    void testReconvertString() {
        Assertions.assertEquals("TEXT", reconvert(column(BasicType.STRING_TYPE, null)));
        Assertions.assertEquals("VARCHAR(100)", reconvert(column(BasicType.STRING_TYPE, 100L)));
        Assertions.assertEquals("VARCHAR(10922)", reconvert(column(BasicType.STRING_TYPE, 10922L)));
        Assertions.assertEquals("TEXT", reconvert(column(BasicType.STRING_TYPE, 10923L)));
    }

    @Test
    void testReconvertBytes() {
        Assertions.assertEquals(
                "LONGBLOB", reconvert(column(PrimitiveByteArrayType.INSTANCE, null)));
        Assertions.assertEquals("BLOB", reconvert(column(PrimitiveByteArrayType.INSTANCE, 100L)));
        Assertions.assertEquals(
                "LONGBLOB", reconvert(column(PrimitiveByteArrayType.INSTANCE, 65536L)));
    }

    @Test
    void testReconvertDecimal() {
        Assertions.assertEquals("DECIMAL(10,2)", reconvert(column(new DecimalType(10, 2), null)));
        Assertions.assertEquals("DECIMAL(38,18)", reconvert(column(new DecimalType(38, 18), null)));
    }

    @Test
    void testReconvertUnsupported() {
        Assertions.assertThrows(
                SeaTunnelRuntimeException.class,
                () ->
                        reconvert(
                                column(
                                        new MapType<>(BasicType.STRING_TYPE, BasicType.STRING_TYPE),
                                        null)));
    }

    @Test
    void testRoundTrip() {
        Column origin = column(new DecimalType(12, 4), null);
        BasicTypeDefine typeDefine = CONVERTER.reconvert(origin);
        Assertions.assertEquals(origin.getDataType(), CONVERTER.convert(typeDefine).getDataType());
    }
}
