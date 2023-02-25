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
import java.util.Iterator;
import java.util.LinkedList;

import org.java.plugin.registry.ExtensionPoint;
import org.java.plugin.registry.ExtensionPoint.ParameterDefinition;

/**
 * @version $Id: MockParameterDefinition.java,v 1.3 2006/09/14 18:15:45 ddimon Exp $
 */
public class MockParameterDefinition extends MockPluginElement implements
        ParameterDefinition {
    private String customData;
    private ExtensionPoint declaringExtensionPoint;
    private String defaultValue;
    private String multiplicity;
    private String type;
    private ParameterDefinition superDefinition;
    private LinkedList subDefinitions = new LinkedList();

    /**
     * @see org.java.plugin.registry.ExtensionPoint.ParameterDefinition#getCustomData()
     */
    public String getCustomData() {
        return customData;
    }
    
    /**
     * @param value the custom data to set
     * @return this instance
     */
    public MockParameterDefinition setCustomData(final String value) {
        customData = value;
        return this;
    }

    /**
     * @see org.java.plugin.registry.ExtensionPoint.ParameterDefinition#getDeclaringExtensionPoint()
     */
    public ExtensionPoint getDeclaringExtensionPoint() {
        return declaringExtensionPoint;
    }
    
    /**
     * @param value the declaring extension point to set
     * @return this instance
     */
    public MockParameterDefinition setDeclaringExtensionPoint(
            final ExtensionPoint value) {
        declaringExtensionPoint = value;
        return this;
    }

    /**
     * @see org.java.plugin.registry.ExtensionPoint.ParameterDefinition#getDefaultValue()
     */
    public String getDefaultValue() {
        return defaultValue;
    }
    
    /**
     * @param value the default value to set
     * @return this instance
     */
    public MockParameterDefinition setDefaultValue(final String value) {
        defaultValue = value;
        return this;
    }

    /**
     * @see org.java.plugin.registry.ExtensionPoint.ParameterDefinition#getMultiplicity()
     */
    public String getMultiplicity() {
        return multiplicity;
    }
    
    /**
     * @param value the multiplicity to set
     * @return this instance
     */
    public MockParameterDefinition setMultiplicity(final String value) {
        multiplicity = value;
        return this;
    }

    /**
     * @see org.java.plugin.registry.ExtensionPoint.ParameterDefinition#getSubDefinition(java.lang.String)
     */
    public ParameterDefinition getSubDefinition(String id) {
        for (Iterator it = subDefinitions.iterator(); it.hasNext();) {
            ParameterDefinition paramDef = (ParameterDefinition) it.next();
            if (paramDef.getId().equals(id)) {
                return paramDef;
            }
        }
        throw new IllegalArgumentException(
                "unknown parameter definition ID " + id); //$NON-NLS-1$
    }

    /**
     * @see org.java.plugin.registry.ExtensionPoint.ParameterDefinition#getSubDefinitions()
     */
    public Collection getSubDefinitions() {
        return Collections.unmodifiableCollection(subDefinitions);
    }
    
    /**
     * @param parameterDefinition sub-parameter definition to add
     * @return this instance
     */
    public MockParameterDefinition addSubDefinition(
            final ParameterDefinition parameterDefinition) {
        subDefinitions.add(parameterDefinition);
        return this;
    }

    /**
     * @see org.java.plugin.registry.ExtensionPoint.ParameterDefinition#getSuperDefinition()
     */
    public ParameterDefinition getSuperDefinition() {
        return superDefinition;
    }
    
    /**
     * @param value the super definition to set
     * @return this instance
     */
    public MockParameterDefinition setSuperDefinition(
            final ParameterDefinition value) {
        superDefinition = value;
        return this;
    }

    /**
     * @see org.java.plugin.registry.ExtensionPoint.ParameterDefinition#getType()
     */
    public String getType() {
        return type;
    }
    
    /**
     * @param value the type to set
     * @return this instance
     */
    public MockParameterDefinition setType(final String value) {
        type = value;
        return this;
    }
}
