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
package java.util.stream;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * An extension of {@link Consumer} used to conduct values through the stages of
 * a stream pipeline, with additional methods to manage size information,
 * control flow, etc.  Before calling the {@code accept()} method on a
 * {@code Sink} for the first time, you must first call the {@code begin()}
 * method to inform it that data is coming (optionally informing the sink how
 * much data is coming), and after all data has been sent, you must call the
 * {@code end()} method.  After calling {@code end()}, you should not call
 * {@code accept()} without again calling {@code begin()}.  {@code Sink} also
 * offers a mechanism by which the sink can cooperatively signal that it does
 * not wish to receive any more data (the {@code cancellationRequested()}
 * method), which a source can poll before sending more data to the
 * {@code Sink}.
 *
 * <p>A sink may be in one of two states: an initial state and an active state.
 * It starts out in the initial state; the {@code begin()} method transitions
 * it to the active state, and the {@code end()} method transitions it back into
 * the initial state, where it can be re-used.  Data-accepting methods (such as
 * {@code accept()} are only valid in the active state.
 *
 * @param <T>
 *         type of elements for value streams
 *
 * @since 1.8
 */
interface Sink<T> extends Consumer<T> {
    /**
     * Resets the sink state to receive a fresh data set.  This must be called
     * before sending any data to the sink.  After calling {@link #end()},
     * you may call this method to reset the sink for another calculation.
     *
     * @param size
     *         The exact size of the data to be pushed downstream, if
     *         known or {@code -1} if unknown or infinite.
     *
     *         <p>Prior to this call, the sink must be in the initial state, and after
     *         this call it is in the active state.
     */
    default void begin(long size) {
    }

    /**
     * Indicates that all elements have been pushed.  If the {@code Sink} is
     * stateful, it should send any stored state downstream at this time, and
     * should clear any accumulated state (and associated resources).
     *
     * <p>Prior to this call, the sink must be in the active state, and after
     * this call it is returned to the initial state.
     */
    default void end() {
    }

    /**
     * Indicates that this {@code Sink} does not wish to receive any more data.
     *
     * @return true if cancellation is requested
     */
    default boolean cancellationRequested() {
        return false;
    }

    /**
     * Accepts an int value.
     *
     * @throws IllegalStateException
     *         if this sink does not accept int values
     */
    default void accept(int value) {
        throw new IllegalStateException("called wrong accept method");
    }

    /**
     * Accepts a long value.
     *
     * @throws IllegalStateException
     *         if this sink does not accept long values
     */
    default void accept(long value) {
        throw new IllegalStateException("called wrong accept method");
    }

    /**
     * Accepts a double value.
     *
     * @throws IllegalStateException
     *         if this sink does not accept double values
     */
    default void accept(double value) {
        throw new IllegalStateException("called wrong accept method");
    }

    /**
     * {@code Sink} that implements {@code Sink<Integer>}, re-abstracts
     * {@code accept(int)}, and wires {@code accept(Integer)} to bridge to
     * {@code accept(int)}.
     */
    interface OfInt extends Sink<Integer>, IntConsumer {
        @Override
        void accept(int value);

        @Override
        default void accept(Integer i) {
            if (Tripwire.ENABLED) {
                Tripwire.trip(getClass(), "{0} calling Sink.OfInt.accept(Integer)");
            }
            accept(i.intValue());
        }
    }

    /**
     * {@code Sink} that implements {@code Sink<Long>}, re-abstracts
     * {@code accept(long)}, and wires {@code accept(Long)} to bridge to
     * {@code accept(long)}.
     */
    interface OfLong extends Sink<Long>, LongConsumer {
        @Override
        void accept(long value);

        @Override
        default void accept(Long i) {
            if (Tripwire.ENABLED) {
                Tripwire.trip(getClass(), "{0} calling Sink.OfLong.accept(Long)");
            }
            accept(i.longValue());
        }
    }

    /**
     * {@code Sink} that implements {@code Sink<Double>}, re-abstracts
     * {@code accept(double)}, and wires {@code accept(Double)} to bridge to
     * {@code accept(double)}.
     */
    interface OfDouble extends Sink<Double>, DoubleConsumer {
        @Override
        void accept(double value);

        @Override
        default void accept(Double i) {
            if (Tripwire.ENABLED) {
                Tripwire.trip(getClass(), "{0} calling Sink.OfDouble.accept(Double)");
            }
            accept(i.doubleValue());
        }
    }

    /**
     * Abstract {@code Sink} implementation for creating chains of
     * sinks.  The {@code begin}, {@code end}, and
     * {@code cancellationRequested} methods are wired to chain to the
     * downstream {@code Sink}.  This implementation takes a downstream
     * {@code Sink} of unknown input shape and produces a {@code Sink<T>}.  The
     * implementation of the {@code accept()} method must call the correct
     * {@code accept()} method on the downstream {@code Sink}.
     */
    static abstract class ChainedReference<T, E_OUT> implements Sink<T> {
        protected final Sink<? super E_OUT> downstream;

        public ChainedReference(Sink<? super E_OUT> downstream) {
            this.downstream = Objects.requireNonNull(downstream);
        }

        @Override
        public void begin(long size) {
            downstream.begin(size);
        }

        @Override
        public void end() {
            downstream.end();
        }

        @Override
        public boolean cancellationRequested() {
            return downstream.cancellationRequested();
        }
    }

    /**
     * Abstract {@code Sink} implementation designed for creating chains of
     * sinks.  The {@code begin}, {@code end}, and
     * {@code cancellationRequested} methods are wired to chain to the
     * downstream {@code Sink}.  This implementation takes a downstream
     * {@code Sink} of unknown input shape and produces a {@code Sink.OfInt}.
     * The implementation of the {@code accept()} method must call the correct
     * {@code accept()} method on the downstream {@code Sink}.
     */
    static abstract class ChainedInt<E_OUT> implements Sink.OfInt {
        protected final Sink<? super E_OUT> downstream;

        public ChainedInt(Sink<? super E_OUT> downstream) {
            this.downstream = Objects.requireNonNull(downstream);
        }

        @Override
        public void begin(long size) {
            downstream.begin(size);
        }

        @Override
        public void end() {
            downstream.end();
        }

        @Override
        public boolean cancellationRequested() {
            return downstream.cancellationRequested();
        }
    }

    /**
     * Abstract {@code Sink} implementation designed for creating chains of
     * sinks.  The {@code begin}, {@code end}, and
     * {@code cancellationRequested} methods are wired to chain to the
     * downstream {@code Sink}.  This implementation takes a downstream
     * {@code Sink} of unknown input shape and produces a {@code Sink.OfLong}.
     * The implementation of the {@code accept()} method must call the correct
     * {@code accept()} method on the downstream {@code Sink}.
     */
    static abstract class ChainedLong<E_OUT> implements Sink.OfLong {
        protected final Sink<? super E_OUT> downstream;

        public ChainedLong(Sink<? super E_OUT> downstream) {
            this.downstream = Objects.requireNonNull(downstream);
        }

        @Override
        public void begin(long size) {
            downstream.begin(size);
        }

        @Override
        public void end() {
            downstream.end();
        }

        @Override
        public boolean cancellationRequested() {
            return downstream.cancellationRequested();
        }
    }

    /**
     * Abstract {@code Sink} implementation designed for creating chains of
     * sinks.  The {@code begin}, {@code end}, and
     * {@code cancellationRequested} methods are wired to chain to the
     * downstream {@code Sink}.  This implementation takes a downstream
     * {@code Sink} of unknown input shape and produces a {@code Sink.OfDouble}.
     * The implementation of the {@code accept()} method must call the correct
     * {@code accept()} method on the downstream {@code Sink}.
     */
    static abstract class ChainedDouble<E_OUT> implements Sink.OfDouble {
        protected final Sink<? super E_OUT> downstream;

        public ChainedDouble(Sink<? super E_OUT> downstream) {
            this.downstream = Objects.requireNonNull(downstream);
        }

        @Override
        public void begin(long size) {
            downstream.begin(size);
        }

        @Override
        public void end() {
            downstream.end();
        }

        @Override
        public boolean cancellationRequested() {
            return downstream.cancellationRequested();
        }
    }
}
