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
package org.java.plugin.registry;

import java.util.Collection;
import java.util.Locale;


/**
 * Result of validation performed by registry on all registered plug-ins. This
 * includes dependencies check, parameters check (against parameter definitions)
 * and any other kind of validation.
 * @version $Id: IntegrityCheckReport.java,v 1.1 2005/07/20 18:43:46 ddimon Exp $
 */
public interface IntegrityCheckReport {
    /**
     * @return number of items with severity {@link ReportItem#SEVERITY_ERROR}
     *         in this report
     */
    int countErrors();
    
    /**
     * @return number of items with severity {@link ReportItem#SEVERITY_WARNING}
     *         in this report
     */
    int countWarnings();
    
    /**
     * @return collection of {@link ReportItem} objects
     */
    Collection getItems();
    
    /**
     * Integrity check report element. Holds all information about particular
     * check event.
     * @version $Id: IntegrityCheckReport.java,v 1.1 2005/07/20 18:43:46 ddimon Exp $
     */
    interface ReportItem {
        /**
         * Item severity code.
         */
        byte SEVERITY_ERROR = 1;
        
        /**
         * Item severity code.
         */
        byte SEVERITY_WARNING = 2;
        
        /**
         * Item severity code.
         */
        byte SEVERITY_INFO = 3;
        
        /**
         * Item error code.
         */
        int ERROR_NO_ERROR = 0;
        
        /**
         * Item error code.
         */
        int ERROR_CHECKER_FAULT = 1;
        
        /**
         * Item error code.
         */
        int ERROR_MANIFEST_PROCESSING_FAILED = 2;
        
        /**
         * Item error code.
         */
        int ERROR_UNSATISFIED_PREREQUISITE = 3;
        
        /**
         * Item error code.
         */
        int ERROR_BAD_LIBRARY = 4;
        
        /**
         * Item error code.
         */
        int ERROR_INVALID_EXTENSION_POINT = 5;
        
        /**
         * Item error code.
         */
        int ERROR_INVALID_EXTENSION = 6;
        
        /**
         * @return severity code for this report item
         */
        byte getSeverity();
        
        /**
         * @return source for this report item, can be <code>null</code>
         */
        Identity getSource();
        
        /**
         * @return error code for this report item
         */
        int getCode();
        
        /**
         * @return message, associated with this report item for the system
         *         default locale
         */
        String getMessage();
        
        /**
         * @param locale locale to get message for
         * @return message, associated with this report item for given locale
         */
        String getMessage(Locale locale);
    }
}
