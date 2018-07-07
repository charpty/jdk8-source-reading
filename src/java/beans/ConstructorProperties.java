/*
 * Copyright (c) 2006, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.beans;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;


import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>An annotation on a constructor that shows how the parameters of
 * that constructor correspond to the constructed object's getter
 * methods.  For example:
 *
 * <blockquote>
 * <pre>
 * public class Point {
 * &#64;ConstructorProperties({"x", "y"})
 * public Point(int x, int y) {
 * this.x = x;
 * this.y = y;
 * }
 *
 * public int getX() {
 * return x;
 * }
 *
 * public int getY() {
 * return y;
 * }
 *
 * private final int x, y;
 * }
 * </pre>
 * </blockquote>
 *
 * The annotation shows that the first parameter of the constructor
 * can be retrieved with the {@code getX()} method and the second with
 * the {@code getY()} method.  Since parameter names are not in
 * general available at runtime, without the annotation there would be
 * no way to know whether the parameters correspond to {@code getX()}
 * and {@code getY()} or the other way around.
 *
 * @since 1.6
 */
@Documented
@Target(CONSTRUCTOR)
@Retention(RUNTIME)
public @interface ConstructorProperties {
    /**
     * <p>The getter names.</p>
     *
     * @return the getter names corresponding to the parameters in the
     * annotated constructor.
     */
    String[] value();
}
