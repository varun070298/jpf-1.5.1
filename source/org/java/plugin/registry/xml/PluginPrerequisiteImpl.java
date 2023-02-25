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
import org.java.plugin.registry.Documentation;
import org.java.plugin.registry.ManifestProcessingException;
import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.registry.PluginFragment;
import org.java.plugin.registry.PluginPrerequisite;
import org.java.plugin.registry.Version;

/**
 * @version $Id: PluginPrerequisiteImpl.java,v 1.7 2006/06/05 18:16:43 ddimon Exp $
 */
class PluginPrerequisiteImpl implements PluginPrerequisite {
    private static Log log = LogFactory.getLog(PluginPrerequisiteImpl.class);

    static boolean matches(final Version source, final Version target,
            final String match) {
        if (source == null) {
            return true;
        }
        if (MATCH_EQUAL.equals(match)) {
            return target.equals(source);
        } else if (MATCH_EQUIVALENT.equals(match)) {
            return target.isEquivalentTo(source);
        } else if (MATCH_COMPATIBLE.equals(match)) {
            return target.isCompatibleWith(source);
        } else if (MATCH_GREATER_OR_EQUAL.equals(match)) {
            return target.isGreaterOrEqualTo(source);
        }
        return target.isCompatibleWith(source);
    }
    
    private final PluginDescriptorImpl descriptor;
    private final PluginFragmentImpl fragment;
    private final ModelPrerequisite model;
    private DocumentationImpl doc;
    
    PluginPrerequisiteImpl(final PluginDescriptorImpl descr,
            final PluginFragmentImpl aFragment, final ModelPrerequisite aModel)
            throws ManifestProcessingException {
        super();
        descriptor = descr;
        fragment = aFragment;
        model = aModel;
        if ((model.getPluginId() == null)
                || (model.getPluginId().trim().length() == 0)) {
            throw new ManifestProcessingException(
                    PluginRegistryImpl.PACKAGE_NAME,
                    "prerequisitePliginIdIsBlank", descr.getId()); //$NON-NLS-1$
        }
        if (descr.getId().equals(model.getPluginId())) {
            throw new ManifestProcessingException(
                    PluginRegistryImpl.PACKAGE_NAME,
                    "invalidPrerequisitePluginId", descr.getId()); //$NON-NLS-1$
        }
        if ((model.getId() == null) || (model.getId().trim().length() == 0)) {
            model.setId("prerequisite:" + model.getPluginId()); //$NON-NLS-1$
        }
        if (model.getDocumentation() != null) {
            doc = new DocumentationImpl(this, model.getDocumentation());
        }
        if (log.isDebugEnabled()) {
            log.debug("object instantiated: " + this); //$NON-NLS-1$
        }
    }

    /**
     * @see org.java.plugin.registry.PluginPrerequisite#getPluginId()
     */
    public String getPluginId() {
        return model.getPluginId();
    }

    /**
     * @see org.java.plugin.registry.PluginPrerequisite#getPluginVersion()
     */
    public Version getPluginVersion() {
        return model.getPluginVersion();
    }

    /**
     * @see org.java.plugin.registry.PluginPrerequisite#getDeclaringPluginDescriptor()
     */
    public PluginDescriptor getDeclaringPluginDescriptor() {
        return descriptor;
    }

    /**
     * @see org.java.plugin.registry.PluginPrerequisite#getDeclaringPluginFragment()
     */
    public PluginFragment getDeclaringPluginFragment() {
        return fragment;
    }

    /**
     * @see org.java.plugin.registry.PluginPrerequisite#isOptional()
     */
    public boolean isOptional() {
        return model.isOptional();
    }
    
    /**
     * @see org.java.plugin.registry.PluginPrerequisite#isReverseLookup()
     */
    public boolean isReverseLookup() {
        return model.isReverseLookup();
    }

    /**
     * @see org.java.plugin.registry.PluginPrerequisite#matches()
     */
    public boolean matches() {
        PluginDescriptor descr = null;
        try {
            descr = this.descriptor.getRegistry().getPluginDescriptor(
                    model.getPluginId());
        } catch (IllegalArgumentException iae) {
            return false;
        }
        return matches(model.getPluginVersion(), descr.getVersion(),
                model.getMatch());
    }

    /**
     * @see org.java.plugin.registry.PluginPrerequisite#getMatch()
     */
    public String getMatch() {
        return model.getMatch();
    }

    /**
     * @see org.java.plugin.registry.PluginPrerequisite#isExported()
     */
    public boolean isExported() {
        return model.isExported();
    }

    /**
     * @see org.java.plugin.registry.Identity#getId()
     */
    public String getId() {
        return model.getId();
    }
    
    /**
     * @see org.java.plugin.registry.Documentable#getDocsPath()
     */
    public String getDocsPath() {
        return (fragment != null) ? fragment.getDocsPath()
                : descriptor.getDocsPath();
    }
    
    /**
     * @see org.java.plugin.registry.Documentable#getDocumentation()
     */
    public Documentation getDocumentation() {
        return doc;
    }

    /**
     * @see org.java.plugin.registry.UniqueIdentity#getUniqueId()
     */
    public String getUniqueId() {
        return descriptor.getRegistry().makeUniqueId(descriptor.getId(),
                getId());
    }
    
    /**
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return "{Prerequisite: uid=" + getUniqueId() + "}"; //$NON-NLS-1$ //$NON-NLS-2$
    }
}
