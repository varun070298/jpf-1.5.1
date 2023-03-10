/*****************************************************************************
 * Java Plug-in Framework (JPF)
 * Copyright (C) 2005 Dmitry Olshansky
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

/**
 * Base interface for those plug-in manifest element classes that may have UID.
 *
 * @see org.java.plugin.registry.PluginRegistry
 * @version $Id: UniqueIdentity.java,v 1.1 2005/12/10 13:13:06 ddimon Exp $
 */
public interface UniqueIdentity extends Identity {
    /**
     * @return unique ID of plug-in element
     */
    String getUniqueId();
}
