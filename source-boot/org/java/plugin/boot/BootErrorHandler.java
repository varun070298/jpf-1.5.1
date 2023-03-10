/*****************************************************************************
 * Java Plug-in Framework (JPF)
 * Copyright (C) 2004-2005 Dmitry Olshansky
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *****************************************************************************/
package org.java.plugin.boot;

import org.java.plugin.registry.IntegrityCheckReport;

/**
 * Callback interface to handle boot-time application errors.
 * 
 * @version $Id: BootErrorHandler.java,v 1.1 2005/10/22 15:22:24 ddimon Exp $
 */
public interface BootErrorHandler {
    /**
     * Called if fatal error has occurred.
     * @param message error message
     */
    void handleFatalError(String message);
    
    /**
     * Called if fatal error has occurred.
     * @param message error message
     * @param t an error
     */
    void handleFatalError(String message, Throwable t);

    /**
     * Called if non-fatal error has occurred and application boot may be
     * continued.
     * @param message error message
     * @param e an error
     * @return <code>true</code> if user wish to continue application start
     */
    boolean handleError(String message, Exception e);
    
    /**
     * Called if an error has been detected during plug-ins integrity check and
     * application boot may be continued.
     * @param message error message
     * @param integrityCheckReport integrity check report
     * @return <code>true</code> if user wish to continue application start
     */
    boolean handleError(String message,
            IntegrityCheckReport integrityCheckReport);
}
