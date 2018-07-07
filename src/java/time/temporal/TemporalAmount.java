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
 *
 *
 *
 *
 *
 * Copyright (c) 2012, 2013 Stephen Colebourne & Michael Nascimento Santos
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
package java.time.temporal;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Period;
import java.util.List;

/**
 * Framework-level interface defining an amount of time, such as
 * "6 hours", "8 days" or "2 years and 3 months".
 * <p>
 * This is the base interface type for amounts of time.
 * An amount is distinct from a date or time-of-day in that it is not tied
 * to any specific point on the time-line.
 * <p>
 * The amount can be thought of as a {@code Map} of {@link TemporalUnit} to
 * {@code long}, exposed via {@link #getUnits()} and {@link #get(TemporalUnit)}.
 * A simple case might have a single unit-value pair, such as "6 hours".
 * A more complex case may have multiple unit-value pairs, such as
 * "7 years, 3 months and 5 days".
 * <p>
 * There are two common implementations.
 * {@link Period} is a date-based implementation, storing years, months and days.
 * {@link Duration} is a time-based implementation, storing seconds and nanoseconds,
 * but providing some access using other duration based units such as minutes,
 * hours and fixed 24-hour days.
 * <p>
 * This interface is a framework-level interface that should not be widely
 * used in application code. Instead, applications should create and pass
 * around instances of concrete types, such as {@code Period} and {@code Duration}.
 *
 * @since 1.8
 */
public interface TemporalAmount {

    /**
     * Returns the value of the requested unit.
     * The units returned from {@link #getUnits()} uniquely define the
     * value of the {@code TemporalAmount}.  A value must be returned
     * for each unit listed in {@code getUnits}.
     *
     * @param unit
     *         the {@code TemporalUnit} for which to return the value
     *
     * @return the long value of the unit
     *
     * @throws DateTimeException
     *         if a value for the unit cannot be obtained
     * @throws UnsupportedTemporalTypeException
     *         if the {@code unit} is not supported
     */
    long get(TemporalUnit unit);

    /**
     * Returns the list of units uniquely defining the value of this TemporalAmount.
     * The list of {@code TemporalUnits} is defined by the implementation class.
     * The list is a snapshot of the units at the time {@code getUnits}
     * is called and is not mutable.
     * The units are ordered from longest duration to the shortest duration
     * of the unit.
     *
     * @return the List of {@code TemporalUnits}; not null
     */
    List<TemporalUnit> getUnits();

    /**
     * Adds to the specified temporal object.
     * <p>
     * Adds the amount to the specified temporal object using the logic
     * encapsulated in the implementing class.
     * <p>
     * There are two equivalent ways of using this method.
     * The first is to invoke this method directly.
     * The second is to use {@link Temporal#plus(TemporalAmount)}:
     * <pre>
     *   // These two lines are equivalent, but the second approach is recommended
     *   dateTime = amount.addTo(dateTime);
     *   dateTime = dateTime.plus(adder);
     * </pre>
     * It is recommended to use the second approach, {@code plus(TemporalAmount)},
     * as it is a lot clearer to read in code.
     *
     * @param temporal
     *         the temporal object to add the amount to, not null
     *
     * @return an object of the same observable type with the addition made, not null
     *
     * @throws DateTimeException
     *         if unable to add
     * @throws ArithmeticException
     *         if numeric overflow occurs
     */
    Temporal addTo(Temporal temporal);

    /**
     * Subtracts this object from the specified temporal object.
     * <p>
     * Subtracts the amount from the specified temporal object using the logic
     * encapsulated in the implementing class.
     * <p>
     * There are two equivalent ways of using this method.
     * The first is to invoke this method directly.
     * The second is to use {@link Temporal#minus(TemporalAmount)}:
     * <pre>
     *   // these two lines are equivalent, but the second approach is recommended
     *   dateTime = amount.subtractFrom(dateTime);
     *   dateTime = dateTime.minus(amount);
     * </pre>
     * It is recommended to use the second approach, {@code minus(TemporalAmount)},
     * as it is a lot clearer to read in code.
     *
     * @param temporal
     *         the temporal object to subtract the amount from, not null
     *
     * @return an object of the same observable type with the subtraction made, not null
     *
     * @throws DateTimeException
     *         if unable to subtract
     * @throws ArithmeticException
     *         if numeric overflow occurs
     */
    Temporal subtractFrom(Temporal temporal);
}
