/*****************************************************************************
 * Java Plug-in Framework (JPF)
 * Copyright (C) 2004-2007 Dmitry Olshansky
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.java.plugin.registry.Documentation;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;
import org.java.plugin.registry.Identity;
import org.java.plugin.registry.Library;
import org.java.plugin.registry.ManifestProcessingException;
import org.java.plugin.registry.PluginAttribute;
import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.registry.PluginPrerequisite;
import org.java.plugin.registry.PluginRegistry;
import org.java.plugin.registry.Version;

/**
 * @version $Id: PluginDescriptorImpl.java,v 1.7 2007/03/03 17:16:59 ddimon Exp $
 */
class PluginDescriptorImpl extends IdentityImpl implements PluginDescriptor {
    private final PluginRegistry registry;
    private final ModelPluginDescriptor model;
    private Map pluginPrerequisites;
    private Map libraries;
    private Map extensionPoints;
    private Map extensions;
    private Documentation doc;
    private List fragments;
    private List attributes;

    PluginDescriptorImpl(final PluginRegistry aRegistry,
            final ModelPluginDescriptor aModel)
            throws ManifestProcessingException {
        super(aModel.getId());
        registry = aRegistry;
        model = aModel;
        if (model.getVendor() == null) {
            model.setVendor(""); //$NON-NLS-1$
        }
        if ((model.getClassName() != null)
                && (model.getClassName().trim().length() == 0)) {
            model.setClassName(null);
        }
        if ((model.getDocsPath() == null)
                || (model.getDocsPath().trim().length() == 0)) {
            model.setDocsPath("docs"); //$NON-NLS-1$
        }
        if (model.getDocumentation() != null) {
            doc = new DocumentationImpl(this, model.getDocumentation());
        }
        
        attributes = new LinkedList();
        fragments = new LinkedList();
        pluginPrerequisites = new HashMap();
        libraries = new HashMap();
        extensionPoints = new HashMap();
        extensions = new HashMap();
        
        processAttributes(null, model);
        processPrerequisites(null, model);
        processLibraries(null, model);
        processExtensionPoints(null, model);
        processExtensions(null, model);
        
        if (log.isDebugEnabled()) {
            log.debug("object instantiated: " + this); //$NON-NLS-1$
        }
    }
    
    void registerFragment(final PluginFragmentImpl fragment)
            throws ManifestProcessingException {
        fragments.add(fragment);
        processAttributes(fragment, fragment.getModel());
        processPrerequisites(fragment, fragment.getModel());
        processLibraries(fragment, fragment.getModel());
        processExtensionPoints(fragment, fragment.getModel());
        processExtensions(fragment, fragment.getModel());
    }
    
    void unregisterFragment(final PluginFragmentImpl fragment) {
        // removing attributes
        for (Iterator it = attributes.iterator(); it.hasNext();) {
            if (fragment.equals(((PluginAttribute) it.next())
                    .getDeclaringPluginFragment())) {
                it.remove();
            }
        }
        // removing prerequisites
        for (Iterator it = pluginPrerequisites.entrySet().iterator();
                it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            if (fragment.equals(((PluginPrerequisite) entry.getValue())
                    .getDeclaringPluginFragment())) {
                it.remove();
            }
        }
        // removing libraries
        for (Iterator it = libraries.entrySet().iterator();
                it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            if (fragment.equals(((Library) entry.getValue())
                    .getDeclaringPluginFragment())) {
                it.remove();
            }
        }
        // removing extension points
        for (Iterator it = extensionPoints.entrySet().iterator();
                it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            if (fragment.equals(((ExtensionPoint) entry.getValue())
                    .getDeclaringPluginFragment())) {
                it.remove();
            }
        }
        // removing extensions
        for (Iterator it = extensions.entrySet().iterator();
                it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            if (fragment.equals(((Extension) entry.getValue())
                    .getDeclaringPluginFragment())) {
                it.remove();
            }
        }
        fragments.remove(fragment);
    }
    
    private void processAttributes(final PluginFragmentImpl fragment,
            final ModelPluginManifest modelManifest)
            throws ManifestProcessingException {
        for (Iterator it = modelManifest.getAttributes().iterator();
                it.hasNext();) {
            attributes.add(new PluginAttributeImpl(this, fragment,
                    (ModelAttribute) it.next(), null));
        }
    }
    
    private void processPrerequisites(final PluginFragmentImpl fragment,
            final ModelPluginManifest modelManifest)
            throws ManifestProcessingException {
        for (Iterator it = modelManifest.getPrerequisites().iterator();
                it.hasNext();) {
            PluginPrerequisiteImpl pluginPrerequisite =
                new PluginPrerequisiteImpl(this, fragment,
                        (ModelPrerequisite) it.next());
            if (pluginPrerequisites.containsKey(
                    pluginPrerequisite.getPluginId())) {
                throw new ManifestProcessingException(
                        PluginRegistryImpl.PACKAGE_NAME,
                        "duplicateImports", new Object[] { //$NON-NLS-1$
                            pluginPrerequisite.getPluginId(), getId()});
            }
            pluginPrerequisites.put(pluginPrerequisite.getPluginId(),
                pluginPrerequisite);
        }
    }
    
    private void processLibraries(final PluginFragmentImpl fragment,
            final ModelPluginManifest modelManifest)
            throws ManifestProcessingException {
        for (Iterator it = modelManifest.getLibraries().iterator();
                it.hasNext();) {
            LibraryImpl lib = new LibraryImpl(this, fragment,
                    (ModelLibrary) it.next());
            if (libraries.containsKey(lib.getId())) {
                throw new ManifestProcessingException(
                        PluginRegistryImpl.PACKAGE_NAME,
                        "duplicateLibraries", new Object[] { //$NON-NLS-1$
                            lib.getId(), getId()});
            }
            libraries.put(lib.getId(), lib);
        }
    }
    
    private void processExtensionPoints(final PluginFragmentImpl fragment,
            final ModelPluginManifest modelManifest)
            throws ManifestProcessingException {
        for (Iterator it = modelManifest.getExtensionPoints().iterator();
                it.hasNext();) {
            ExtensionPointImpl extensionPoint =
                new ExtensionPointImpl(this, fragment,
                        (ModelExtensionPoint) it.next());
            if (extensionPoints.containsKey(extensionPoint.getId())) {
                throw new ManifestProcessingException(
                        PluginRegistryImpl.PACKAGE_NAME,
                        "duplicateExtensionPoints", new Object[] { //$NON-NLS-1$
                            extensionPoint.getId(), getId()});
            }
            extensionPoints.put(extensionPoint.getId(), extensionPoint);
        }
    }
    
    private void processExtensions(final PluginFragmentImpl fragment,
            final ModelPluginManifest modelManifest)
            throws ManifestProcessingException {
        for (Iterator it = modelManifest.getExtensions().iterator();
                it.hasNext();) {
            ExtensionImpl extension = new ExtensionImpl(this, fragment,
                    (ModelExtension) it.next());
            if (extensions.containsKey(extension.getId())) {
                throw new ManifestProcessingException(
                        PluginRegistryImpl.PACKAGE_NAME,
                        "duplicateExtensions", new Object[] { //$NON-NLS-1$
                            extension.getId(), getId()});
            }
            if (!getId().equals(extension.getExtendedPluginId())
                    && !pluginPrerequisites.containsKey(
                            extension.getExtendedPluginId())) {
                throw new ManifestProcessingException(
                        PluginRegistryImpl.PACKAGE_NAME,
                        "pluginNotDeclaredInPrerequisites", new Object[] { //$NON-NLS-1$
                            extension.getExtendedPluginId(), extension.getId(),
                            getId()});
            }
            extensions.put(extension.getId(), extension);
        }
        //extensions = Collections.unmodifiableMap(extensions);
    }

    /**
     * @see org.java.plugin.registry.UniqueIdentity#getUniqueId()
     */
    public String getUniqueId() {
        return registry.makeUniqueId(getId(), model.getVersion());
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getVendor()
     */
    public String getVendor() {
        return model.getVendor();
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getVersion()
     */
    public Version getVersion() {
        return model.getVersion();
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getPrerequisites()
     */
    public Collection getPrerequisites() {
        return Collections.unmodifiableCollection(pluginPrerequisites.values());
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getPrerequisite(java.lang.String)
     */
    public PluginPrerequisite getPrerequisite(String id) {
        return (PluginPrerequisite) pluginPrerequisites.get(id);
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getExtensionPoints()
     */
    public Collection getExtensionPoints() {
        return Collections.unmodifiableCollection(extensionPoints.values());
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getExtensionPoint(java.lang.String)
     */
    public ExtensionPoint getExtensionPoint(String id) {
        return (ExtensionPoint) extensionPoints.get(id);
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getExtensions()
     */
    public Collection getExtensions() {
        return Collections.unmodifiableCollection(extensions.values());
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getExtension(java.lang.String)
     */
    public Extension getExtension(String id) {
        return (Extension) extensions.get(id);
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getLibraries()
     */
    public Collection getLibraries() {
        return Collections.unmodifiableCollection(libraries.values());
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getLibrary(java.lang.String)
     */
    public Library getLibrary(String id) {
        return (Library) libraries.get(id);
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getRegistry()
     */
    public PluginRegistry getRegistry() {
        return registry;
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getPluginClassName()
     */
    public String getPluginClassName() {
        return model.getClassName();
    }

    /**
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return "{PluginDescriptor: uid=" + getUniqueId() + "}"; //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * @see org.java.plugin.registry.Documentable#getDocumentation()
     */
    public Documentation getDocumentation() {
        return doc;
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getFragments()
     */
    public Collection getFragments() {
        return Collections.unmodifiableCollection(fragments);
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getAttribute(java.lang.String)
     */
    public PluginAttribute getAttribute(final String id) {
        PluginAttributeImpl result = null;
        for (Iterator it = attributes.iterator(); it.hasNext();) {
            PluginAttributeImpl attr = (PluginAttributeImpl) it.next();
            if (attr.getId().equals(id)) {
                if (result == null) {
                    result = attr;
                } else {
                    throw new IllegalArgumentException(
                        "more than one attribute with ID " + id //$NON-NLS-1$
                        + " defined in plug-in " + getUniqueId()); //$NON-NLS-1$
                }
            }
        }
        return result;
    }
    
    /**
     * @see org.java.plugin.registry.PluginDescriptor#getAttributes()
     */
    public Collection getAttributes() {
        return Collections.unmodifiableCollection(attributes);
    }
    
    /**
     * @see org.java.plugin.registry.PluginDescriptor#getAttributes(java.lang.String)
     */
    public Collection getAttributes(final String id) {
        List result = new LinkedList();
        for (Iterator it = attributes.iterator(); it.hasNext();) {
            PluginAttributeImpl param = (PluginAttributeImpl) it.next();
            if (param.getId().equals(id)) {
                result.add(param);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getDocsPath()
     */
    public String getDocsPath() {
        return model.getDocsPath();
    }

    /**
     * @see org.java.plugin.registry.PluginDescriptor#getLocation()
     */
    public URL getLocation() {
        return model.getLocation();
    }

    /**
     * @see org.java.plugin.registry.xml.IdentityImpl#isEqualTo(
     *      org.java.plugin.registry.Identity)
     */
    protected boolean isEqualTo(final Identity idt) {
        if (!(idt instanceof PluginDescriptorImpl)) {
            return false;
        }
        PluginDescriptorImpl other = (PluginDescriptorImpl) idt;
        return getUniqueId().equals(other.getUniqueId())
            && getLocation().toExternalForm().equals(
                    other.getLocation().toExternalForm());
    }
}
