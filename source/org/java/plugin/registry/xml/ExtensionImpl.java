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

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.java.plugin.PathResolver;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;
import org.java.plugin.registry.Identity;
import org.java.plugin.registry.ManifestProcessingException;
import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.registry.PluginFragment;
import org.java.plugin.registry.PluginRegistry;
import org.java.plugin.registry.ExtensionPoint.ParameterDefinition;
import org.java.plugin.registry.IntegrityCheckReport.ReportItem;
import org.java.plugin.registry.xml.ExtensionPointImpl.ParameterDefinitionImpl;

/**
 * @version $Id: ExtensionImpl.java,v 1.9 2006/08/28 17:18:12 ddimon Exp $
 */
final class ExtensionImpl extends PluginElementImpl implements Extension {
    private final ModelExtension model;
    private List parameters;
    private Boolean isValid;
    
    ExtensionImpl(final PluginDescriptorImpl descr,
            final PluginFragmentImpl aFragment, final ModelExtension aModel)
            throws ManifestProcessingException {
        super(descr, aFragment, aModel.getId(), aModel.getDocumentation());
        model = aModel;
        if ((model.getPluginId() == null)
                || (model.getPluginId().trim().length() == 0)) {
            throw new ManifestProcessingException(
                    PluginRegistryImpl.PACKAGE_NAME,
                    "extensionIdIsBlank", descr.getId()); //$NON-NLS-1$
        }
        if ((model.getPointId() == null)
                || (model.getPointId().trim().length() == 0)) {
            throw new ManifestProcessingException(
                    PluginRegistryImpl.PACKAGE_NAME,
                    "extendedPointIdIsBlank", descr.getId()); //$NON-NLS-1$
        }
        parameters = new ArrayList(model.getParams().size());
        for (Iterator it = model.getParams().iterator(); it.hasNext();) {
            parameters.add(new ParameterImpl(null, (ModelParameter) it.next()));
        }
        parameters = Collections.unmodifiableList(parameters);
        if (log.isDebugEnabled()) {
            log.debug("object instantiated: " + this); //$NON-NLS-1$
        }
    }

    /**
     * @see org.java.plugin.registry.UniqueIdentity#getUniqueId()
     */
    public String getUniqueId() {
        return getDeclaringPluginDescriptor().getRegistry().makeUniqueId(
                getDeclaringPluginDescriptor().getId(), getId());
    }

    /**
     * @see org.java.plugin.registry.Extension#getParameters()
     */
    public Collection getParameters() {
        return parameters;
    }

    /**
     * @see org.java.plugin.registry.Extension#getParameter(java.lang.String)
     */
    public Parameter getParameter(final String id) {
        ParameterImpl result = null;
        for (Iterator it = parameters.iterator(); it.hasNext();) {
            ParameterImpl param = (ParameterImpl) it.next();
            if (param.getId().equals(id)) {
                if (result == null) {
                    result = param;
                } else {
                    throw new IllegalArgumentException(
                        "more than one parameter with ID " + id //$NON-NLS-1$
                        + " defined in extension " + getUniqueId()); //$NON-NLS-1$
                }
            }
        }
        return result;
    }

    /**
     * @see org.java.plugin.registry.Extension#getParameters(java.lang.String)
     */
    public Collection getParameters(final String id) {
        List result = new LinkedList();
        for (Iterator it = parameters.iterator(); it.hasNext();) {
            ParameterImpl param = (ParameterImpl) it.next();
            if (param.getId().equals(id)) {
                result.add(param);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * @see org.java.plugin.registry.Extension#getExtendedPluginId()
     */
    public String getExtendedPluginId() {
        return model.getPluginId();
    }

    /**
     * @see org.java.plugin.registry.Extension#getExtendedPointId()
     */
    public String getExtendedPointId() {
        return model.getPointId();
    }

    /**
     * @see org.java.plugin.registry.Extension#isValid()
     */
    public boolean isValid() {
        if (isValid == null) {
            validate();
        }
        return isValid.booleanValue();
    }
    
    Collection validate() {
        ExtensionPoint point =
            getExtensionPoint(getExtendedPluginId(), getExtendedPointId());
        if (point == null) {
            isValid = Boolean.FALSE;
            return Collections.singletonList(
                    new IntegrityChecker.ReportItemImpl(
                        ReportItem.SEVERITY_ERROR, this,
                        ReportItem.ERROR_INVALID_EXTENSION,
                        "extPointNotAvailable", new Object[] { //$NON-NLS-1$
                        getDeclaringPluginDescriptor().getRegistry()
                            .makeUniqueId(getExtendedPluginId(),
                                    getExtendedPointId()), getUniqueId()}));
        }
        Collection result =
            validateParameters(point.getParameterDefinitions(), parameters);
        isValid = result.isEmpty() ? Boolean.TRUE : Boolean.FALSE;
        return result;
    }
    
    ExtensionPoint getExtensionPoint(final String uniqueId) {
        PluginRegistry registry = getDeclaringPluginDescriptor().getRegistry();
        return getExtensionPoint(registry.extractPluginId(uniqueId),
                registry.extractId(uniqueId));
    }

    ExtensionPoint getExtensionPoint(final String pluginId,
            final String pointId) {
        PluginRegistry registry = getDeclaringPluginDescriptor().getRegistry();
        if (!registry.isPluginDescriptorAvailable(pluginId)) {
            return null;
        }
        for (Iterator it = registry.getPluginDescriptor(pluginId)
                .getExtensionPoints().iterator(); it.hasNext();) {
            ExtensionPoint point = (ExtensionPoint) it.next();
            if (point.getId().equals(pointId)) {
                return point;
            }
        }
        return null;
    }
    
    private Collection validateParameters(final Collection allDefinitions,
            final Collection allParams) {
        List result = new LinkedList();
        Map groups = new HashMap();
        for (Iterator it = allParams.iterator(); it.hasNext();) {
            Parameter param = (Parameter) it.next();
            ParameterDefinition def = param.getDefinition();
            if (def == null) {
                result.add(new IntegrityChecker.ReportItemImpl(
                        ReportItem.SEVERITY_ERROR, this,
                        ReportItem.ERROR_INVALID_EXTENSION,
                        "cantDetectParameterDef", new Object[] { //$NON-NLS-1$
                        param.getId(), getUniqueId()}));
                continue;
            }
            if (groups.containsKey(param.getId())) {
                ((Collection) groups.get(param.getId())).add(param);
            } else {
                Collection paramGroup = new LinkedList();
                paramGroup.add(param);
                groups.put(param.getId(), paramGroup);
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        for (Iterator it = allDefinitions.iterator(); it.hasNext();) {
            ParameterDefinition def = (ParameterDefinition) it.next();
            Collection paramGroup = (Collection) groups.get(def.getId());
            result.addAll(validateParameters(def,
                    (paramGroup != null) ? paramGroup
                            : Collections.EMPTY_LIST));
        }
        return result;
    }
    
    private Collection validateParameters(final ParameterDefinition def,
            final Collection params) {
        if (log.isDebugEnabled()) {
            log.debug("validating parameters for definition " + def); //$NON-NLS-1$
        }
        if (ParameterDefinition.MULT_ONE.equals(def.getMultiplicity())
                && (params.size() != 1)) {
            return Collections.singletonList(
                    new IntegrityChecker.ReportItemImpl(
                        ReportItem.SEVERITY_ERROR, this,
                        ReportItem.ERROR_INVALID_EXTENSION,
                        "tooManyOrFewParams", new Object[] { //$NON-NLS-1$
                        def.getId(), getUniqueId()}));
        } else if (ParameterDefinition.MULT_NONE_OR_ONE.equals(
                def.getMultiplicity()) && (params.size() > 1)) {
            return Collections.singletonList(
                    new IntegrityChecker.ReportItemImpl(
                        ReportItem.SEVERITY_ERROR, this,
                        ReportItem.ERROR_INVALID_EXTENSION,
                        "tooManyParams", new Object[] { //$NON-NLS-1$
                                def.getId(), getUniqueId()}));
        } else if (ParameterDefinition.MULT_ONE_OR_MORE.equals(
                def.getMultiplicity()) && params.isEmpty()) {
            return Collections.singletonList(
                    new IntegrityChecker.ReportItemImpl(
                        ReportItem.SEVERITY_ERROR, this,
                        ReportItem.ERROR_INVALID_EXTENSION,
                        "tooFewParams", new Object[] { //$NON-NLS-1$
                                def.getId(), getUniqueId()}));
        }
        if (params.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        List result = new LinkedList();
        int count = 1;
        for (Iterator it = params.iterator(); it.hasNext(); count++) {
            ParameterImpl param = (ParameterImpl) it.next();
            if (!param.isValid()) {
                result.add(new IntegrityChecker.ReportItemImpl(
                        ReportItem.SEVERITY_ERROR, this,
                        ReportItem.ERROR_INVALID_EXTENSION,
                        "invalidParameterValue", new Object[] { //$NON-NLS-1$
                        def.getId(), new Integer(count), getUniqueId()}));
            }
            if (!ParameterDefinition.TYPE_ANY.equals(def.getType())
                    && result.isEmpty()) {
                result.addAll(validateParameters(
                        param.getDefinition().getSubDefinitions(),
                        param.getSubParameters()));
            }
        }
        return result;
    }
    
    /**
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return "{PluginExtension: uid=" + getUniqueId() + "}"; //$NON-NLS-1$ //$NON-NLS-2$
    }
    
    void registryChanged() {
        isValid = null;
    }

    private class ParameterImpl extends PluginElementImpl implements Parameter {
        private final ModelParameter modelParam;
        private ParameterValueParser valueParser;
        private List subParameters;
        private ParameterDefinition definition = null;
        private boolean definitionDetected = false;
        private final ParameterImpl superParameter;

        ParameterImpl(final ParameterImpl aSuperParameter,
                final ModelParameter aModel)
                throws ManifestProcessingException {
            super(ExtensionImpl.this.getDeclaringPluginDescriptor(),
                    ExtensionImpl.this.getDeclaringPluginFragment(),
                    aModel.getId(), aModel.getDocumentation());
            this.superParameter = aSuperParameter;
            modelParam = aModel;
            subParameters = new ArrayList(modelParam.getParams().size());
            for (Iterator it = modelParam.getParams().iterator();
                    it.hasNext();) {
                subParameters.add(new ParameterImpl(this,
                        (ModelParameter) it.next()));
            }
            subParameters = Collections.unmodifiableList(subParameters);
            if (log.isDebugEnabled()) {
                log.debug("object instantiated: " + this); //$NON-NLS-1$
            }
        }

        /**
         * @see org.java.plugin.registry.Extension.Parameter#getDeclaringExtension()
         */
        public Extension getDeclaringExtension() {
            return ExtensionImpl.this;
        }

        /**
         * @see org.java.plugin.registry.PluginElement#getDeclaringPluginDescriptor()
         */
        public PluginDescriptor getDeclaringPluginDescriptor() {
            return ExtensionImpl.this.getDeclaringPluginDescriptor();
        }
        
        /**
         * @see org.java.plugin.registry.PluginElement#getDeclaringPluginFragment()
         */
        public PluginFragment getDeclaringPluginFragment() {
            return ExtensionImpl.this.getDeclaringPluginFragment();
        }

        /**
         * @see org.java.plugin.registry.Extension.Parameter#getDefinition()
         */
        public ParameterDefinition getDefinition() {
            if (definitionDetected) {
                return definition;
            }
            definitionDetected = true;
            if (log.isDebugEnabled()) {
                log.debug("detecting definition for parameter " + this); //$NON-NLS-1$
            }
            Collection definitions;
            if (superParameter != null) {
                if (superParameter.getDefinition() == null) {
                    return null;
                }
                if (ParameterDefinition.TYPE_ANY.equals(
                            superParameter.getDefinition().getType())) {
                    definition = superParameter.getDefinition();
                    if (log.isDebugEnabled()) {
                        log.debug("definition detected - " + definition); //$NON-NLS-1$
                    }
                    return definition;
                }
                definitions =
                    superParameter.getDefinition().getSubDefinitions();
            } else {
                definitions = getExtensionPoint(
                            getDeclaringExtension().getExtendedPluginId(),
                            getDeclaringExtension().getExtendedPointId()).
                                getParameterDefinitions();
            }
            for (Iterator it = definitions.iterator(); it.hasNext();) {
                ParameterDefinition def = (ParameterDefinition) it.next();
                if (def.getId().equals(getId())) {
                    definition = def;
                    break;
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("definition detected - " + definition); //$NON-NLS-1$
            }
            return definition;
        }

        /**
         * @see org.java.plugin.registry.Extension.Parameter#getSuperParameter()
         */
        public Parameter getSuperParameter() {
            return superParameter;
        }
        
        /**
         * @see org.java.plugin.registry.Extension.Parameter#getSubParameters()
         */
        public Collection getSubParameters() {
            return subParameters;
        }
        
        /**
         * @see org.java.plugin.registry.Extension.Parameter#getSubParameter(
         *      java.lang.String)
         */
        public Parameter getSubParameter(final String id) {
            ParameterImpl result = null;
            for (Iterator it = subParameters.iterator(); it.hasNext();) {
                ParameterImpl param = (ParameterImpl) it.next();
                if (param.getId().equals(id)) {
                    if (result == null) {
                        result = param;
                    } else {
                        throw new IllegalArgumentException(
                            "more than one parameter with ID " + id //$NON-NLS-1$
                            + " defined in extension " + getUniqueId()); //$NON-NLS-1$
                    }
                }
            }
            return result;
        }

        /**
         * @see org.java.plugin.registry.Extension.Parameter#getSubParameters(
         *      java.lang.String)
         */
        public Collection getSubParameters(final String id) {
            List result = new LinkedList();
            for (Iterator it = subParameters.iterator(); it.hasNext();) {
                ParameterImpl param = (ParameterImpl) it.next();
                if (param.getId().equals(id)) {
                    result.add(param);
                }
            }
            return Collections.unmodifiableList(result);
        }

        /**
         * @see org.java.plugin.registry.Extension.Parameter#rawValue()
         */
        public String rawValue() {
            return (modelParam.getValue() != null) ? modelParam.getValue() : ""; //$NON-NLS-1$
        }
        
        boolean isValid() {
            if (valueParser != null) {
                return valueParser.isParsingSucceeds();
            }
            if (log.isDebugEnabled()) {
                log.debug("validating parameter " + this); //$NON-NLS-1$
            }
            valueParser = new ParameterValueParser(
                    getDeclaringPluginDescriptor().getRegistry(),
                    getDefinition(), modelParam.getValue());
            if (!valueParser.isParsingSucceeds()) {
                log.warn("parsing value for parameter " + this //$NON-NLS-1$
                        + " failed, message is: " //$NON-NLS-1$
                        + valueParser.getParsingMessage());
            }
            return valueParser.isParsingSucceeds();
        }
        
        /**
         * @see org.java.plugin.registry.Extension.Parameter#valueAsBoolean()
         */
        public Boolean valueAsBoolean() {
            if (!isValid()) {
                throw new UnsupportedOperationException(
                        "parameter value is invalid"); //$NON-NLS-1$
            }
            if (!ParameterDefinition.TYPE_BOOLEAN.equals(
                    definition.getType())) {
                throw new UnsupportedOperationException("parameter type is not " //$NON-NLS-1$
                        + ParameterDefinition.TYPE_BOOLEAN);
            }
            if (valueParser.getValue() == null) {
                return (Boolean) ((ParameterDefinitionImpl) getDefinition())
                .getValueParser().getValue();
            }
            return (Boolean) valueParser.getValue();
        }
        
        /**
         * @see org.java.plugin.registry.Extension.Parameter#valueAsDate()
         */
        public Date valueAsDate() {
            if (!isValid()) {
                throw new UnsupportedOperationException(
                        "parameter value is invalid"); //$NON-NLS-1$
            }
            if (!ParameterDefinition.TYPE_DATE.equals(definition.getType())
                    && !ParameterDefinition.TYPE_DATETIME.equals(
                            definition.getType())
                    && !ParameterDefinition.TYPE_TIME.equals(
                            definition.getType())) {
                throw new UnsupportedOperationException("parameter type is not " //$NON-NLS-1$
                        + ParameterDefinition.TYPE_DATE + " nor " //$NON-NLS-1$
                        + ParameterDefinition.TYPE_DATETIME + " nor" //$NON-NLS-1$
                        + ParameterDefinition.TYPE_TIME);
            }
            if (valueParser.getValue() == null) {
                return (Date) ((ParameterDefinitionImpl) getDefinition())
                .getValueParser().getValue();
            }
            return (Date) valueParser.getValue();
        }
        
        /**
         * @see org.java.plugin.registry.Extension.Parameter#valueAsNumber()
         */
        public Number valueAsNumber() {
            if (!isValid()) {
                throw new UnsupportedOperationException(
                        "parameter value is invalid"); //$NON-NLS-1$
            }
            if (!ParameterDefinition.TYPE_NUMBER.equals(definition.getType())) {
                throw new UnsupportedOperationException("parameter type is not " //$NON-NLS-1$
                        + ParameterDefinition.TYPE_NUMBER);
            }
            if (valueParser.getValue() == null) {
                return (Number) ((ParameterDefinitionImpl) getDefinition())
                .getValueParser().getValue();
            }
            return (Number) valueParser.getValue();
        }
        
        /**
         * @see org.java.plugin.registry.Extension.Parameter#valueAsString()
         */
        public String valueAsString() {
            if (!isValid()) {
                throw new UnsupportedOperationException(
                        "parameter value is invalid"); //$NON-NLS-1$
            }
            if (!ParameterDefinition.TYPE_STRING.equals(definition.getType())
                    && !ParameterDefinition.TYPE_FIXED.equals(
                            definition.getType())) {
                throw new UnsupportedOperationException("parameter type is not " //$NON-NLS-1$
                        + ParameterDefinition.TYPE_STRING);
            }
            if (valueParser.getValue() == null) {
                return (String) ((ParameterDefinitionImpl) getDefinition())
                .getValueParser().getValue();
            }
            return (String) valueParser.getValue();
        }

        /**
         * @see org.java.plugin.registry.Extension.Parameter#valueAsExtension()
         */
        public Extension valueAsExtension() {
            if (!isValid()) {
                throw new UnsupportedOperationException(
                        "parameter value is invalid"); //$NON-NLS-1$
            }
            if (!ParameterDefinition.TYPE_EXTENSION_ID.equals(
                    definition.getType())) {
                throw new UnsupportedOperationException(
                        "parameter type is not " //$NON-NLS-1$
                        + ParameterDefinition.TYPE_EXTENSION_ID);
            }
            if (valueParser.getValue() == null) {
                return (Extension) ((ParameterDefinitionImpl) getDefinition())
                .getValueParser().getValue();
            }
            return (Extension) valueParser.getValue();
        }
        
        /**
         * @see org.java.plugin.registry.Extension.Parameter#valueAsExtensionPoint()
         */
        public ExtensionPoint valueAsExtensionPoint() {
            if (!isValid()) {
                throw new UnsupportedOperationException(
                        "parameter value is invalid"); //$NON-NLS-1$
            }
            if (!ParameterDefinition.TYPE_EXTENSION_POINT_ID.equals(
                    definition.getType())) {
                throw new UnsupportedOperationException(
                        "parameter type is not " //$NON-NLS-1$
                        + ParameterDefinition.TYPE_EXTENSION_POINT_ID);
            }
            if (valueParser.getValue() == null) {
                return (ExtensionPoint) ((ParameterDefinitionImpl) getDefinition())
                .getValueParser().getValue();
            }
            return (ExtensionPoint) valueParser.getValue();
        }
        
        /**
         * @see org.java.plugin.registry.Extension.Parameter#valueAsPluginDescriptor()
         */
        public PluginDescriptor valueAsPluginDescriptor() {
            if (!isValid()) {
                throw new UnsupportedOperationException(
                        "parameter value is invalid"); //$NON-NLS-1$
            }
            if (!ParameterDefinition.TYPE_PLUGIN_ID.equals(
                    definition.getType())) {
                throw new UnsupportedOperationException("parameter type is not " //$NON-NLS-1$
                        + ParameterDefinition.TYPE_PLUGIN_ID);
            }
            if (valueParser.getValue() == null) {
                return (PluginDescriptor) ((ParameterDefinitionImpl) getDefinition())
                .getValueParser().getValue();
            }
            return (PluginDescriptor) valueParser.getValue();
        }
        
        /**
         * @see org.java.plugin.registry.Extension.Parameter#valueAsUrl()
         */
        public URL valueAsUrl() {
            return valueAsUrl(null);
        }

        /**
         * @see org.java.plugin.registry.Extension.Parameter#valueAsUrl(
         *      org.java.plugin.PathResolver)
         */
        public URL valueAsUrl(final PathResolver pathResolver) {
            if (!isValid()) {
                throw new UnsupportedOperationException(
                        "parameter value is invalid"); //$NON-NLS-1$
            }
            if (!ParameterDefinition.TYPE_RESOURCE.equals(
                    definition.getType())) {
                throw new UnsupportedOperationException(
                        "parameter type is not " //$NON-NLS-1$
                        + ParameterDefinition.TYPE_RESOURCE);
            }
            if ((valueParser.getValue() == null) && (rawValue() == null)) {
                return valueAsUrl(pathResolver,
                        getDefinition().getDeclaringExtensionPoint(),
                        (URL) ((ParameterDefinitionImpl) getDefinition())
                        .getValueParser().getValue(),
                        getDefinition().getDefaultValue());
            }
            return valueAsUrl(pathResolver, getDeclaringPluginDescriptor(),
                    (URL) valueParser.getValue(), rawValue());
        }
        
        private URL valueAsUrl(final PathResolver pathResolver,
                final Identity idt, final URL absoluteUrl,
                final String relativeUrl) {
            if ((pathResolver == null) || (absoluteUrl != null)) {
                return absoluteUrl;
            }
            if (relativeUrl == null) {
                return null;
            }
            return pathResolver.resolvePath(idt, relativeUrl);
        }
        
        /**
         * @see java.lang.Object#toString()
         */
        public String toString() {
            return "{PluginExtension.Parameter: extUid=" //$NON-NLS-1$
                + getDeclaringExtension().getUniqueId() + "; id=" + getId() //$NON-NLS-1$
                + "}"; //$NON-NLS-1$
        }
        
        /**
         * @see org.java.plugin.registry.xml.IdentityImpl#isEqualTo(
         *      org.java.plugin.registry.Identity)
         */
        protected boolean isEqualTo(final Identity idt) {
            if (!super.isEqualTo(idt)) {
                return false;
            }
            ParameterImpl other = (ParameterImpl) idt;
            if ((getSuperParameter() == null)
                    && (other.getSuperParameter() == null)) {
                return true;
            }
            if ((getSuperParameter() == null)
                    || (other.getSuperParameter() == null)) {
                return false;
            }
            return getSuperParameter().equals(other.getSuperParameter());
        }
    }
}
