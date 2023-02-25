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

/**
 * Interface to collect documentation data for some plug-in element.
 * @version $Id: Documentation.java,v 1.1 2005/07/20 18:43:46 ddimon Exp $
 */
public interface Documentation {
    /**
     * @return plug-in element caption or empty string 
     */
    String getCaption();
    
    /**
     * @return main documentation text or empty string
     */
    String getText();
    
    /**
     * @return collection of {@link Reference references} in this documentation
     */
    Collection getReferences();
    
    /**
     * @return element, for which this documentation is provided
     */
    Identity getDeclaringIdentity();
    
    /**
     * Documentation reference.
     * @version $Id: Documentation.java,v 1.1 2005/07/20 18:43:46 ddimon Exp $
     */
    interface Reference {
        /**
         * @return the reference as specified in manifest
         */
        String getRef();
        
        /**
         * @return text to be used when making link for this reference
         */
        String getCaption();
        
        /**
         * @return element, for which this documentation reference is provided
         */
        Identity getDeclaringIdentity();
    }
}
