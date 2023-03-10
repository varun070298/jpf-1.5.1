/*****************************************************************************
 * Java Plug-in Framework (JPF)
 * Copyright (C) 2004-2006 Dmitry Olshansky
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
package org.java.plugin.registry.xml;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.java.plugin.registry.Identity;
import org.java.plugin.registry.ManifestProcessingException;

/**
 * @version $Id: IdentityImpl.java,v 1.4 2006/08/26 15:14:08 ddimon Exp $
 */
abstract class IdentityImpl implements Identity {
    /**
     * Makes logging service available for descending classes.
     */
    protected final Log log = LogFactory.getLog(getClass());

    private final String id;

    protected IdentityImpl(final String anId)
            throws ManifestProcessingException {
        id = anId;
        if ((id == null) || (id.trim().length() == 0)) {
            throw new ManifestProcessingException(
                    PluginRegistryImpl.PACKAGE_NAME,
                    "manifestElementIdIsBlank"); //$NON-NLS-1$
        }
    }

    /**
     * @see org.java.plugin.registry.Identity#getId()
     */
    public String getId() {
        return id;
    }
    
    protected abstract boolean isEqualTo(final Identity idt);
    
    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Identity)) {
            return false;
        }
        return isEqualTo((Identity) obj);
    }
    
    private int hashCode = -1;
    
    /**
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        if (hashCode == -1) {
            hashCode = getClass().hashCode() ^ getId().hashCode();
        }
        return hashCode;
    }
}
