/*****************************************************************************
 * Java Plug-in Framework (JPF)
 * Copyright (C) 2006 Dmitry Olshansky
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
package org.java.plugin.tools.mocks;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

import org.java.plugin.registry.Documentation;
import org.java.plugin.registry.Identity;

/**
 * @version $Id: MockDocumentation.java,v 1.2 2006/09/14 18:10:39 ddimon Exp $
 */
public class MockDocumentation implements Documentation {
    private String caption;
    private Identity declaringIdentity;
    private String text;
    private LinkedList references = new LinkedList();

    /**
     * @see org.java.plugin.registry.Documentation#getCaption()
     */
    public String getCaption() {
        return caption;
    }
    
    /**
     * @param value the caption to set
     * @return this instance
     */
    public MockDocumentation setCaption(final String value) {
        caption = value;
        return this;
    }

    /**
     * @see org.java.plugin.registry.Documentation#getDeclaringIdentity()
     */
    public Identity getDeclaringIdentity() {
        return declaringIdentity;
    }
    
    /**
     * @param value the declaring identity to set
     * @return this instance
     */
    public MockDocumentation setDeclaringIdentity(final Identity value) {
        declaringIdentity = value;
        return this;
    }

    /**
     * @see org.java.plugin.registry.Documentation#getReferences()
     */
    public Collection getReferences() {
        return Collections.unmodifiableCollection(references);
    }
    
    /**
     * @param reference reference to add
     * @return this instance
     */
    public MockDocumentation addReference(final String reference) {
        references.add(reference);
        return this;
    }

    /**
     * @see org.java.plugin.registry.Documentation#getText()
     */
    public String getText() {
        return text;
    }

    /**
     * @param value the text to set
     * @return this instance
     */
    public MockDocumentation setText(final String value) {
        text = value;
        return this;
    }
}
