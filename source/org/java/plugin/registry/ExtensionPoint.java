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
 * This interface abstracts the extension point - a place where the
 * functionality of plug-in can be extended.
 * <p>
 * Extension point UID is a combination of declaring plug-in ID and extension
 * point ID that is unique within whole set of registered plug-ins.
 * </p>
 * 
 * @version $Id: ExtensionPoint.java,v 1.4 2006/08/19 17:37:22 ddimon Exp $
 */
public interface ExtensionPoint extends UniqueIdentity, PluginElement {
    /**
     * Extension point multiplicity constant.
     */
    String EXT_MULT_ANY = "any"; //$NON-NLS-1$
    
    /**
     * Extension point multiplicity constant.
     */
    String EXT_MULT_ONE = "one"; //$NON-NLS-1$
    
    /**
     * Extension point multiplicity constant.
     */
    String EXT_MULT_ONE_PER_PLUGIN = "one-per-plugin"; //$NON-NLS-1$
    
    /**
     * Extension point multiplicity constant.
     */
    String EXT_MULT_NONE = "none"; //$NON-NLS-1$

    /**
     * @return multiplicity of this extension point
     */
    String getMultiplicity();

    /**
     * Returns collection of all top level parameter definitions declared
     * in this extension point and all it parents.
     * @return collection of {@link ExtensionPoint.ParameterDefinition} objects
     */
    Collection getParameterDefinitions();
    
    /**
     * @param id ID of parameter definition to look for
     * @return parameter definition with given ID
     */
    ParameterDefinition getParameterDefinition(String id);

    /**
     * Returns a collection of all extensions that available for this point.
     * @return collection of {@link Extension} objects
     */
    Collection getAvailableExtensions();
    
    /**
     * @param uniqueId unique ID of extension
     * @return extension that is available for this point
     */
    Extension getAvailableExtension(String uniqueId);
    
    /**
     * Checks if extension is available for this extension point. If this method
     * returns <code>true</code>, the method
     * {@link #getAvailableExtension(String)} should return valid extension for
     * the same UID.
     * @param uniqueId unique ID of extension
     * @return <code>true</code> if extension is available for this extension
     *         point
     */
    boolean isExtensionAvailable(String uniqueId);

    /**
     * Returns a collection of all extensions that was successfully "connected"
     * to this point.
     * @return collection of {@link Extension} objects
     */
    Collection getConnectedExtensions();
    
    /**
     * @param uniqueId unique ID of extension
     * @return extension that was successfully "connected" to this point
     */
    Extension getConnectedExtension(String uniqueId);
    
    /**
     * Checks if extension is in valid state and successfully "connected"
     * to this extension point. If this method returns <code>true</code>,
     * the method {@link #getConnectedExtension(String)} should return
     * valid extension for the same UID.
     * @param uniqueId unique ID of extension
     * @return <code>true</code> if extension was successfully "connected" to
     *         this extension point
     */
    boolean isExtensionConnected(String uniqueId);
    
    /**
     * @return <code>true</code> if extension point is considered to be valid
     */
    boolean isValid();
    
    /**
     * @return parent extension point plug-in ID or <code>null</code>
     */
    String getParentPluginId();
    
    /**
     * @return parent extension point ID or <code>null</code>
     */
    String getParentExtensionPointId();
    
    /**
     * @param extensionPoint extension point
     * @return <code>true</code> if this point is successor of given extension
     *         point
     */
    boolean isSuccessorOf(ExtensionPoint extensionPoint);
    
    /**
     * Looks for all available (valid) successors of this extension point.
     * The search should be done recursively including all descendants of this
     * extension point.
     * @return collection of {@link ExtensionPoint} objects
     */
    Collection getDescendants();

    /**
     * This interface abstracts parameter definition - a parameter
     * "type declaration".
     * @version $Id: ExtensionPoint.java,v 1.4 2006/08/19 17:37:22 ddimon Exp $
     */
    interface ParameterDefinition extends PluginElement {
        /**
         * Parameter definition type constant.
         */
        String TYPE_STRING = "string"; //$NON-NLS-1$

        /**
         * Parameter definition type constant.
         */
        String TYPE_BOOLEAN = "boolean"; //$NON-NLS-1$

        /**
         * Parameter definition type constant.
         */
        String TYPE_NUMBER = "number"; //$NON-NLS-1$

        /**
         * Parameter definition type constant.
         */
        String TYPE_DATE = "date"; //$NON-NLS-1$

        /**
         * Parameter definition type constant.
         */
        String TYPE_TIME = "time"; //$NON-NLS-1$

        /**
         * Parameter definition type constant.
         */
        String TYPE_DATETIME = "date-time"; //$NON-NLS-1$

        /**
         * Parameter definition type constant.
         */
        String TYPE_NULL = "null"; //$NON-NLS-1$

        /**
         * Parameter definition type constant.
         */
        String TYPE_ANY = "any"; //$NON-NLS-1$

        /**
         * Parameter definition type constant.
         */
        String TYPE_PLUGIN_ID = "plugin-id"; //$NON-NLS-1$

        /**
         * Parameter definition type constant.
         */
        String TYPE_EXTENSION_POINT_ID = "extension-point-id"; //$NON-NLS-1$

        /**
         * Parameter definition type constant.
         */
        String TYPE_EXTENSION_ID = "extension-id"; //$NON-NLS-1$

        /**
         * Parameter definition type constant.
         */
        String TYPE_FIXED = "fixed"; //$NON-NLS-1$

        /**
         * Parameter definition type constant.
         */
        String TYPE_RESOURCE = "resource"; //$NON-NLS-1$

        /**
         * Parameter definition multiplicity constant.
         */
        String MULT_ONE = "one"; //$NON-NLS-1$

        /**
         * Parameter definition multiplicity constant.
         */
        String MULT_ANY = "any"; //$NON-NLS-1$

        /**
         * Parameter definition multiplicity constant.
         */
        String MULT_NONE_OR_ONE = "none-or-one"; //$NON-NLS-1$

        /**
         * Parameter definition multiplicity constant.
         */
        String MULT_ONE_OR_MORE = "one-or-more"; //$NON-NLS-1$

        /**
         * @return multiplicity of parameter, that can be defined according
         *         to this definition
         */
        String getMultiplicity();

        /**
         * @return value type of parameter, that can be defined according
         *         to this definition
         */
        String getType();
        
        /**
         * @return custom data for additional customization of some types
         */
        String getCustomData();

        /**
         * Returns collection of all parameter sub-definitions declared
         * in this parameter definition.
         * @return collection of {@link ExtensionPoint.ParameterDefinition}
         *         objects
         */
        Collection getSubDefinitions();

        /**
         * @param id ID of parameter sub-definition to look for
         * @return parameter sub-definition with given ID
         */
        ParameterDefinition getSubDefinition(String id);

        /**
         * @return extension point, this definition belongs to
         */
        ExtensionPoint getDeclaringExtensionPoint();
        
        /**
         * @return parameter definition, of which this one is child or
         *         <code>null</code> if this is top level parameter definition
         */
        ParameterDefinition getSuperDefinition();
        
        /**
         * @return default parameter value as it is defined in manifest
         */
        String getDefaultValue();
    }
}