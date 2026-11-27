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

import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SqlType;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.converter.AbstractJdbcRowConverter;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.DatabaseIdentifier;

import javax.annotation.Nullable;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class Gbase8aJdbcRowConverter extends AbstractJdbcRowConverter {
    @Override
    public String converterName() {
        return DatabaseIdentifier.GBASE_8A;
    }

    /**
     * GBase 8a has no time zone aware type and its MySQL Connector/J 5.x based driver does not
     * support {@link OffsetDateTime} in {@code setObject}. A {@code TIMESTAMP_TZ} value is written
     * as the local date-time of the same instant in the JVM default time zone.
     */
    @Override
    protected void setValueToStatementByDataType(
            Object value,
            PreparedStatement statement,
            SeaTunnelDataType<?> seaTunnelDataType,
            int statementIndex,
            @Nullable String sourceType)
            throws SQLException {
        if (seaTunnelDataType.getSqlType() == SqlType.TIMESTAMP_TZ) {
            OffsetDateTime offsetDateTime = (OffsetDateTime) value;
            statement.setTimestamp(
                    statementIndex,
                    Timestamp.valueOf(
                            offsetDateTime
                                    .atZoneSameInstant(ZoneId.systemDefault())
                                    .toLocalDateTime()));
            return;
        }
        super.setValueToStatementByDataType(
                value, statement, seaTunnelDataType, statementIndex, sourceType);
    }
}
