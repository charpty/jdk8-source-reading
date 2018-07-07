/*
 * Copyright (c) 1996, 2013, Oracle and/or its affiliates. All rights reserved.
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

package java.rmi;

/**
 * {@code RMISecurityManager} implements a policy identical to the policy
 * implemented by {@link SecurityManager}. RMI applications
 * should use the {@code SecurityManager} class or another appropriate
 * {@code SecurityManager} implementation instead of this class. RMI's class
 * loader will download classes from remote locations only if a security
 * manager has been set.
 *
 * @author Roger Riggs
 * @author Peter Jones
 * @since JDK1.1
 * @deprecated Use {@link SecurityManager} instead.
 */
@Deprecated
public class RMISecurityManager extends SecurityManager {

    /**
     * Constructs a new {@code RMISecurityManager}.
     *
     * @since JDK1.1
     */
    public RMISecurityManager() {
    }
}
