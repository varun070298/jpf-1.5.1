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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.java.plugin.registry.Identity;
import org.java.plugin.registry.ManifestProcessingException;
import org.java.plugin.registry.PluginAttribute;

/**
 * @version $Id: PluginAttributeImpl.java,v 1.3 2006/06/05 18:16:43 ddimon Exp $
 */
class PluginAttributeImpl extends PluginElementImpl implements PluginAttribute {
    private final PluginAttributeImpl superAttribute;
    private final ModelAttribute model;
    private List subAttributes;

    PluginAttributeImpl(final PluginDescriptorImpl descr,
            final PluginFragmentImpl aFragment, final ModelAttribute aModel,
            final PluginAttributeImpl aSuperAttribute)
            throws ManifestProcessingException {
        super(descr, aFragment, aModel.getId(), aModel.getDocumentation());
        model = aModel;
        superAttribute = aSuperAttribute;
        if (model.getValue() == null) {
            model.setValue(""); //$NON-NLS-1$
        }
        subAttributes = new ArrayList(model.getAttributes().size());
        for (Iterator it = model.getAttributes().iterator(); it.hasNext();) {
            subAttributes.add(new PluginAttributeImpl(descr, aFragment,
                    (ModelAttribute) it.next(), this));
        }
        subAttributes = Collections.unmodifiableList(subAttributes);
        if (log.isDebugEnabled()) {
            log.debug("object instantiated: " + this); //$NON-NLS-1$
        }
    }

    /**
     * @see org.java.plugin.registry.PluginAttribute#getSubAttribute(java.lang.String)
     */
    public PluginAttribute getSubAttribute(final String id) {
        PluginAttributeImpl result = null;
        for (Iterator it = subAttributes.iterator(); it.hasNext();) {
            PluginAttributeImpl param = (PluginAttributeImpl) it.next();
            if (param.getId().equals(id)) {
                if (result == null) {
                    result = param;
                } else {
                    throw new IllegalArgumentException(
                        "more than one attribute with ID " + id //$NON-NLS-1$
                        + " defined in plug-in " //$NON-NLS-1$
                        + getDeclaringPluginDescriptor().getUniqueId());
                }
            }
        }
        return result;
    }
    
    /**
     * @see org.java.plugin.registry.PluginAttribute#getSubAttributes()
     */
    public Collection getSubAttributes() {
        return subAttributes;
    }
    
    /**
     * @see org.java.plugin.registry.PluginAttribute#getSubAttributes(java.lang.String)
     */
    public Collection getSubAttributes(final String id) {
        List result = new LinkedList();
        for (Iterator it = subAttributes.iterator(); it.hasNext();) {
            PluginAttributeImpl param = (PluginAttributeImpl) it.next();
            if (param.getId().equals(id)) {
                result.add(param);
            }
        }
        return Collections.unmodifiableList(result);
    }
    
    /**
     * @see org.java.plugin.registry.PluginAttribute#getValue()
     */
    public String getValue() {
        return model.getValue();
    }
    
    /**
     * @see org.java.plugin.registry.xml.IdentityImpl#isEqualTo(
     *      org.java.plugin.registry.Identity)
     */
    protected boolean isEqualTo(final Identity idt) {
        if (!super.isEqualTo(idt)) {
            return false;
        }
        PluginAttributeImpl other = (PluginAttributeImpl) idt;
        if ((getSuperAttribute() == null)
                && (other.getSuperAttribute() == null)) {
            return true;
        }
        if ((getSuperAttribute() == null)
                || (other.getSuperAttribute() == null)) {
            return false;
        }
        return getSuperAttribute().equals(other.getSuperAttribute());
    }

    /**
     * @see org.java.plugin.registry.PluginAttribute#getSuperAttribute()
     */
    public PluginAttribute getSuperAttribute() {
        return superAttribute;
    }
}
