/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 * Copyright (c) 2011-2012, Stephen Colebourne & Michael Nascimento Santos
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 *  * Neither the name of JSR-310 nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package java.time;

import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.StreamCorruptedException;

/**
 * The shared serialization delegate for this package.
 *
 * @since 1.8
 */
final class Ser implements Externalizable {

    /**
     * Serialization version.
     */
    private static final long serialVersionUID = -7683839454370182990L;

    static final byte DURATION_TYPE = 1;
    static final byte INSTANT_TYPE = 2;
    static final byte LOCAL_DATE_TYPE = 3;
    static final byte LOCAL_TIME_TYPE = 4;
    static final byte LOCAL_DATE_TIME_TYPE = 5;
    static final byte ZONE_DATE_TIME_TYPE = 6;
    static final byte ZONE_REGION_TYPE = 7;
    static final byte ZONE_OFFSET_TYPE = 8;
    static final byte OFFSET_TIME_TYPE = 9;
    static final byte OFFSET_DATE_TIME_TYPE = 10;
    static final byte YEAR_TYPE = 11;
    static final byte YEAR_MONTH_TYPE = 12;
    static final byte MONTH_DAY_TYPE = 13;
    static final byte PERIOD_TYPE = 14;

    /** The type being serialized. */
    private byte type;
    /** The object being serialized. */
    private Object object;

    /**
     * Constructor for deserialization.
     */
    public Ser() {
    }

    /**
     * Creates an instance for serialization.
     *
     * @param type
     *         the type
     * @param object
     *         the object
     */
    Ser(byte type, Object object) {
        this.type = type;
        this.object = object;
    }

    //-----------------------------------------------------------------------

    /**
     * Implements the {@code Externalizable} interface to write the object.
     *
     * @param out
     *         the data stream to write to, not null
     */
    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        writeInternal(type, object, out);
    }

    static void writeInternal(byte type, Object object, ObjectOutput out) throws IOException {
        out.writeByte(type);
        switch (type) {
        case DURATION_TYPE:
            ((Duration) object).writeExternal(out);
            break;
        case INSTANT_TYPE:
            ((Instant) object).writeExternal(out);
            break;
        case LOCAL_DATE_TYPE:
            ((LocalDate) object).writeExternal(out);
            break;
        case LOCAL_DATE_TIME_TYPE:
            ((LocalDateTime) object).writeExternal(out);
            break;
        case LOCAL_TIME_TYPE:
            ((LocalTime) object).writeExternal(out);
            break;
        case ZONE_REGION_TYPE:
            ((ZoneRegion) object).writeExternal(out);
            break;
        case ZONE_OFFSET_TYPE:
            ((ZoneOffset) object).writeExternal(out);
            break;
        case ZONE_DATE_TIME_TYPE:
            ((ZonedDateTime) object).writeExternal(out);
            break;
        case OFFSET_TIME_TYPE:
            ((OffsetTime) object).writeExternal(out);
            break;
        case OFFSET_DATE_TIME_TYPE:
            ((OffsetDateTime) object).writeExternal(out);
            break;
        case YEAR_TYPE:
            ((Year) object).writeExternal(out);
            break;
        case YEAR_MONTH_TYPE:
            ((YearMonth) object).writeExternal(out);
            break;
        case MONTH_DAY_TYPE:
            ((MonthDay) object).writeExternal(out);
            break;
        case PERIOD_TYPE:
            ((Period) object).writeExternal(out);
            break;
        default:
            throw new InvalidClassException("Unknown serialized type");
        }
    }

    //-----------------------------------------------------------------------

    /**
     * Implements the {@code Externalizable} interface to read the object.
     *
     * @param in
     *         the data to read, not null
     */
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        type = in.readByte();
        object = readInternal(type, in);
    }

    static Object read(ObjectInput in) throws IOException, ClassNotFoundException {
        byte type = in.readByte();
        return readInternal(type, in);
    }

    private static Object readInternal(byte type, ObjectInput in) throws IOException, ClassNotFoundException {
        switch (type) {
        case DURATION_TYPE:
            return Duration.readExternal(in);
        case INSTANT_TYPE:
            return Instant.readExternal(in);
        case LOCAL_DATE_TYPE:
            return LocalDate.readExternal(in);
        case LOCAL_DATE_TIME_TYPE:
            return LocalDateTime.readExternal(in);
        case LOCAL_TIME_TYPE:
            return LocalTime.readExternal(in);
        case ZONE_DATE_TIME_TYPE:
            return ZonedDateTime.readExternal(in);
        case ZONE_OFFSET_TYPE:
            return ZoneOffset.readExternal(in);
        case ZONE_REGION_TYPE:
            return ZoneRegion.readExternal(in);
        case OFFSET_TIME_TYPE:
            return OffsetTime.readExternal(in);
        case OFFSET_DATE_TIME_TYPE:
            return OffsetDateTime.readExternal(in);
        case YEAR_TYPE:
            return Year.readExternal(in);
        case YEAR_MONTH_TYPE:
            return YearMonth.readExternal(in);
        case MONTH_DAY_TYPE:
            return MonthDay.readExternal(in);
        case PERIOD_TYPE:
            return Period.readExternal(in);
        default:
            throw new StreamCorruptedException("Unknown serialized type");
        }
    }

    /**
     * Returns the object that will replace this one.
     *
     * @return the read object, should never be null
     */
    private Object readResolve() {
        return object;
    }

}
