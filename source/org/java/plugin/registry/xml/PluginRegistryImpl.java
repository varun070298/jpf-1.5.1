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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.java.plugin.PathResolver;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;
import org.java.plugin.registry.IntegrityCheckReport;
import org.java.plugin.registry.ManifestProcessingException;
import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.registry.PluginFragment;
import org.java.plugin.registry.PluginPrerequisite;
import org.java.plugin.registry.PluginRegistry;
import org.java.plugin.registry.Version;
import org.java.plugin.registry.IntegrityCheckReport.ReportItem;
import org.java.plugin.registry.xml.IntegrityChecker.ReportItemImpl;
import org.java.plugin.util.ExtendedProperties;

/**
 * This is an implementation of plug-in registry of XML syntax plug-in
 * manifests. Manifests should be prepared according to
 * <a href="{@docRoot}/../plugin_1_0.dtd">plug-in DTD</a>.
 * <p>
 * <b>Configuration parameters</b>
 * <p>
 * This registry implementation supports following configuration parameters:
 * <dl>
 *   <dt>isValidating</dt>
 *   <dd>Regulates is registry should use validating parser when loading
 *     plug-in manifests. The default parameter value is <code>true</code>.</dd>
 *   <dt>stopOnError</dt>
 *   <dd>Regulates is registry should stop and throw RuntimeException if an
 *     error occurred while {@link PluginRegistry#register(URL[]) registering}
 *     or {@link PluginRegistry#unregister(String[]) un-registering} plug-ins.
 *     If this is <code>false</code>, the registration errors will be stored
 *     in the internal report that is available with
 *     {@link PluginRegistry#checkIntegrity(PathResolver)} method.
 *     The default parameter value is <code>false</code>.</dd>
 * </dl>
 * 
 * @see org.java.plugin.ObjectFactory#createRegistry()
 * 
 * @version $Id: PluginRegistryImpl.java,v 1.18 2007/03/03 17:16:59 ddimon Exp $
 */
public final class PluginRegistryImpl implements PluginRegistry {
    static final String PACKAGE_NAME = "org.java.plugin.registry.xml"; //$NON-NLS-1$
    private static final char UNIQUE_SEPARATOR = '@';
    private static final Log log = LogFactory.getLog(PluginRegistryImpl.class);

    private final List registrationReport = new LinkedList(); // <ReportItemImpl>
    private final Map registeredPlugins = new HashMap(); // <pluginId, PluginDescriptorImpl>
    private final Map registeredFragments = new HashMap(); // <fragmentId, PluginFragmentImpl>
    private final List listeners = Collections.synchronizedList(new LinkedList()); // <EventListener>
    private ManifestParser manifestParser;
    private boolean stopOnError = false;
    
    /**
     * Creates plug-in registry object.
     */
    public PluginRegistryImpl() {
        registrationReport.add(new ReportItemImpl(ReportItem.SEVERITY_INFO,
                null, ReportItem.ERROR_NO_ERROR, "registryStart", null)); //$NON-NLS-1$
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#configure(
     *      ExtendedProperties)
     */
    public void configure(final ExtendedProperties config) {
        stopOnError = "true".equalsIgnoreCase( //$NON-NLS-1$
                config.getProperty("stopOnError", "false")); //$NON-NLS-1$ //$NON-NLS-2$
        boolean isValidating = !"false".equalsIgnoreCase( //$NON-NLS-1$
                config.getProperty("isValidating", "true")); //$NON-NLS-1$ //$NON-NLS-2$
        manifestParser = new ManifestParser(isValidating);
        log.info("configured, stopOnError=" + stopOnError //$NON-NLS-1$
                + ", isValidating=" + isValidating); //$NON-NLS-1$
    }
    
    /**
     * @see org.java.plugin.registry.PluginRegistry#readManifestInfo(
     *      java.net.URL)
     */
    public ManifestInfo readManifestInfo(final URL url)
            throws ManifestProcessingException {
        try {
            return new ManifestInfoImpl(manifestParser.parseManifestInfo(url));
        } catch (Exception e) {
            throw new ManifestProcessingException(PACKAGE_NAME,
                        "manifestParsingError", url, e); //$NON-NLS-1$
        }
    }

    /**
     * General algorithm:
     * <ol>
     *   <li>Collect all currently registered extension points.</li>
     *   <li>Parse given URL's as XML content files and separate them on plug-in
     *       and plug-in fragment descriptors.</li>
     *   <li>Process new plug-in descriptors first:
     *     <ol>
     *       <li>Instantiate new PluginDescriptorImpl object.</li>
     *       <li>Handle versions correctly - register new descriptor as most
     *           recent version or as an old version.</li>
     *       <li>If other versions of the same plug-in already registered, take
     *           their fragments and register them with this version.</li>
     *     </ol>
     *   </li>
     *   <li>Process new plug-in fragments next:
     *     <ol>
     *       <li>Instantiate new PluginFragmentImpl object.</li>
     *       <li>Check if older version of the same fragment already registered.
     *           If yes, un-register it and move to old plug-in fragments
     *           collection.</li>
     *       <li>Register new fragment with all matches plug-in descriptors (if
     *           this fragment is of most recent version).</li>
     *      </ol>
     *    </li>
     *    <li>Notify collected extension points about potential changes in
     *        extensions set.</li>
     *    <li>Propagate events about registry changes.</li>
     * </ol>
     * @see org.java.plugin.registry.PluginRegistry#register(java.net.URL[])
     */
    public Map register(final URL[] manifests)
            throws ManifestProcessingException {
        // collecting registered extension points and extensions
        List registeredPoints = new LinkedList(); //<ExtensionPointImpl>
        Map registeredExtensions = new HashMap(); //<extensionUid, ExtensionImpl>
        for (Iterator it = registeredPlugins.values().iterator();
                it.hasNext();) {
            for (Iterator it2 = ((PluginDescriptor) it.next())
                    .getExtensionPoints().iterator();
                    it2.hasNext();) {
                ExtensionPoint point = (ExtensionPoint) it2.next();
                registeredPoints.add(point);
                for (Iterator it3 = point.getConnectedExtensions().iterator();
                        it3.hasNext();) {
                    Extension ext = (Extension) it3.next();
                    registeredExtensions.put(ext.getUniqueId(), ext);
                }
            }
        }
        Map result = new HashMap(manifests.length); //<URL, PluginDescriptor or PluginFragment>
        Map plugins = new HashMap(); //<URL, ModelPluginDescriptor>
        Map fragments = new HashMap(); //<URL, ModelPluginFrafment>
        // parsing given manifests
        registrationReport.add(new ReportItemImpl(ReportItem.SEVERITY_INFO,
                null, ReportItem.ERROR_NO_ERROR, "manifestsParsingStart", null)); //$NON-NLS-1$
        for (int i = 0; i < manifests.length; i++) {
            URL url = manifests[i];
            ModelPluginManifest model;
            try {
                model = manifestParser.parseManifest(url);
            } catch (Exception e) {
                log.error("can't parse manifest file " + url, e); //$NON-NLS-1$
                if (stopOnError) {
                    throw new ManifestProcessingException(PACKAGE_NAME,
                            "manifestParsingError", url, e); //$NON-NLS-1$
                }
                registrationReport.add(new ReportItemImpl(
                        ReportItem.SEVERITY_ERROR, null,
                        ReportItem.ERROR_MANIFEST_PROCESSING_FAILED,
                        "manifestParsingError", new Object[] {url, e})); //$NON-NLS-1$
                continue;
            }
            if (model instanceof ModelPluginFragment) {
                fragments.put(url, model);
                continue;
            }
            if (!(model instanceof ModelPluginDescriptor)) {
                log.warn("URL " + url //$NON-NLS-1$
                        + " points to XML document of unknown type"); //$NON-NLS-1$
                continue;
            }
            plugins.put(url, model);
        }
        if (log.isDebugEnabled()) {
            log.debug("manifest files parsed, plugins.size=" + plugins.size() //$NON-NLS-1$
                    + ", fragments.size=" + fragments.size()); //$NON-NLS-1$
        }
        registrationReport.add(new ReportItemImpl(ReportItem.SEVERITY_INFO,
                null, ReportItem.ERROR_NO_ERROR, "manifestsParsingFinish", //$NON-NLS-1$
                new Object[] {new Integer(plugins.size()),
                new Integer(fragments.size())}));
        checkVersions(plugins);
        if (log.isDebugEnabled()) {
            log.debug("plug-ins versions checked, plugins.size=" //$NON-NLS-1$
                    + plugins.size());
        }
        checkVersions(fragments);
        if (log.isDebugEnabled()) {
            log.debug("plug-in fragments versions checked, fragments.size=" //$NON-NLS-1$
                    + fragments.size());
        }
        RegistryChangeDataImpl registryChangeData =
            new RegistryChangeDataImpl();
        // registering new plug-ins
        registrationReport.add(new ReportItemImpl(ReportItem.SEVERITY_INFO,
                null, ReportItem.ERROR_NO_ERROR,
                "registeringPluginsStart", null)); //$NON-NLS-1$
        for (Iterator it = plugins.values().iterator(); it.hasNext();) {
            PluginDescriptor descr = registerPlugin(
                    (ModelPluginDescriptor) it.next(),
                    registryChangeData);
            if (descr != null) {
                result.put(descr.getLocation(), descr);
            }
        }
        plugins.clear();
        // registering new plug-in fragments
        registrationReport.add(new ReportItemImpl(ReportItem.SEVERITY_INFO,
                null, ReportItem.ERROR_NO_ERROR,
                "registeringFragmentsStart", null)); //$NON-NLS-1$
        for (Iterator it = fragments.values().iterator(); it.hasNext();) {
            PluginFragment fragment = registerFragment(
                    (ModelPluginFragment) it.next(), registryChangeData);
            if (fragment != null) {
                result.put(fragment.getLocation(), fragment);
            }
        }
        fragments.clear();
        registrationReport.add(new ReportItemImpl(ReportItem.SEVERITY_INFO,
                null, ReportItem.ERROR_NO_ERROR,
                "registeringPluginsFinish", //$NON-NLS-1$
                new Integer(registeredPlugins.size())));
        registrationReport.add(new ReportItemImpl(ReportItem.SEVERITY_INFO,
                null, ReportItem.ERROR_NO_ERROR,
                "registeringFragmentsFinish", //$NON-NLS-1$
                new Integer(registeredFragments.size())));
        log.info("plug-in and fragment descriptors registered - " //$NON-NLS-1$
                + result.size());
        dump();
        if (result.isEmpty()) {
            return result;
        }
        // notify all interested members that plug-ins set has been changed
        for (Iterator it = registeredPoints.iterator(); it.hasNext();) {
            ((ExtensionPointImpl) it.next()).registryChanged();
        }
        for (Iterator it = registeredExtensions.values().iterator();
                it.hasNext();) {
            ((ExtensionImpl) it.next()).registryChanged();
        }
        if (!listeners.isEmpty() || log.isDebugEnabled()) {
            // analyze changes in extensions set
            for (Iterator it = registeredPlugins.values().iterator();
                    it.hasNext();) {
                for (Iterator it2 = ((PluginDescriptor) it.next())
                        .getExtensionPoints().iterator();
                        it2.hasNext();) {
                    for (Iterator it3 = ((ExtensionPoint) it2.next())
                            .getConnectedExtensions().iterator();
                            it3.hasNext();) {
                        Extension ext = (Extension) it3.next();
                        if (!registeredExtensions.containsKey(
                                ext.getUniqueId())) {
                            registryChangeData.putAddedExtension(
                                    ext.getUniqueId(),
                                    makeUniqueId(ext.getExtendedPluginId(),
                                            ext.getExtendedPointId()));
                        } else {
                            registeredExtensions.remove(ext.getUniqueId());
                            if (registryChangeData.modifiedPlugins().contains(
                                    ext.getDeclaringPluginDescriptor().getId())
                                    || registryChangeData.modifiedPlugins()
                                        .contains(ext.getExtendedPluginId())) {
                                registryChangeData.putModifiedExtension(
                                        ext.getUniqueId(),
                                        makeUniqueId(ext.getExtendedPluginId(),
                                                ext.getExtendedPointId()));
                            }
                        }
                    }
                }
            }
            for (Iterator it = registeredExtensions.values().iterator();
                    it.hasNext();) {
                Extension ext = (Extension) it.next();
                registryChangeData.putRemovedExtension(ext.getUniqueId(),
                        makeUniqueId(ext.getExtendedPluginId(),
                                ext.getExtendedPointId()));
            }
            // fire event
            fireEvent(registryChangeData);
        }
        return result;
    }
    
    private void checkVersions(final Map plugins)
            throws ManifestProcessingException {
        Map versions = new HashMap(); //<ID, [Version, URL]>
        Set toBeRemovedUrls = new HashSet(); //<URL>
        for (Iterator it = plugins.entrySet().iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            URL url = (URL) entry.getKey();
            ModelPluginManifest model = (ModelPluginManifest) entry.getValue();
            if (registeredPlugins.containsKey(model.getId())) {
                if (stopOnError) {
                    throw new ManifestProcessingException(PACKAGE_NAME,
                            "duplicatePlugin", //$NON-NLS-1$
                            model.getId());
                }
                it.remove();
                registrationReport.add(new ReportItemImpl(
                        ReportItem.SEVERITY_ERROR, null,
                        ReportItem.ERROR_MANIFEST_PROCESSING_FAILED,
                        "duplicatedPluginId", model.getId())); //$NON-NLS-1$
                continue;
            }
            if (registeredFragments.containsKey(model.getId())) {
                if (stopOnError) {
                    throw new ManifestProcessingException(PACKAGE_NAME,
                            "duplicatePluginFragment", //$NON-NLS-1$
                            model.getId());
                }
                it.remove();
                registrationReport.add(new ReportItemImpl(
                        ReportItem.SEVERITY_ERROR, null,
                        ReportItem.ERROR_MANIFEST_PROCESSING_FAILED,
                        "duplicatedFragmentId", model.getId())); //$NON-NLS-1$
                continue;
            }
            Object[] version = (Object[]) versions.get(model.getId());
            if (version == null) {
                versions.put(model.getId(),
                        new Object[] {model.getVersion(), url});
                continue;
            }
            if (((Version) version[0]).compareTo(model.getVersion()) < 0) {
                toBeRemovedUrls.add(version[1]);
                versions.put(model.getId(),
                        new Object[] {model.getVersion(), url});
            } else {
                toBeRemovedUrls.add(url);
            }
        }
        versions.clear();
        for (Iterator it = toBeRemovedUrls.iterator(); it.hasNext();) {
            URL url = (URL) it.next();
            plugins.remove(url);
            log.warn("ignoring duplicated manifest " + url); //$NON-NLS-1$
        }
        toBeRemovedUrls.clear();
    }

    private PluginDescriptor registerPlugin(final ModelPluginDescriptor model,
            final RegistryChangeDataImpl registryChangeData)
            throws ManifestProcessingException {
        if (log.isDebugEnabled()) {
            log.debug("registering plug-in, URL - " + model.getLocation()); //$NON-NLS-1$
        }
        PluginDescriptorImpl result = null;
        try {
            result = new PluginDescriptorImpl(this, model);
            registryChangeData.addedPlugins().add(result.getId());
            // applying fragments to the new plug-in
            for (Iterator it = registeredFragments.values().iterator();
                    it.hasNext();) {
                PluginFragmentImpl fragment = (PluginFragmentImpl) it.next();
                if (fragment.matches(result)) {
                    result.registerFragment(fragment);
                }
            }
            registrationReport.add(new ReportItemImpl(
                    ReportItem.SEVERITY_INFO, null,
                    ReportItem.ERROR_NO_ERROR,
                    "pluginRegistered", result.getUniqueId())); //$NON-NLS-1$
        } catch (ManifestProcessingException mpe) {
            log.error("failed registering plug-in, URL - " //$NON-NLS-1$
                    + model.getLocation(), mpe);
            if (stopOnError) {
                throw mpe;
            }
            registrationReport.add(new ReportItemImpl(
                    ReportItem.SEVERITY_ERROR, null,
                    ReportItem.ERROR_MANIFEST_PROCESSING_FAILED,
                    "pluginRegistrationFailed", //$NON-NLS-1$
                    new Object[] {model.getLocation(), mpe}));
            return null;
        }
        registeredPlugins.put(result.getId(), result);
        return result;
    }

    private PluginFragment registerFragment(final ModelPluginFragment model,
            final RegistryChangeDataImpl registryChangeData)
            throws ManifestProcessingException {
        if (log.isDebugEnabled()) {
            log.debug("registering plug-in fragment descriptor, URL - " //$NON-NLS-1$
                    + model.getLocation());
        }
        PluginFragmentImpl result = null;
        try {
            result = new PluginFragmentImpl(this, model);
            // register fragment with all matches plug-ins
            boolean isRegistered = false;
            PluginDescriptorImpl descr =
                (PluginDescriptorImpl) getPluginDescriptor(
                        result.getPluginId());
            if (result.matches(descr)) {
                descr.registerFragment(result);
                if (!registryChangeData.addedPlugins().contains(
                        descr.getId())) {
                    registryChangeData.modifiedPlugins().add(descr.getId());
                }
                isRegistered = true;
            }
            if (!isRegistered) {
                log.warn("no matching plug-ins found for fragment " //$NON-NLS-1$
                        + result.getUniqueId());
                registrationReport.add(new ReportItemImpl(
                        ReportItem.SEVERITY_WARNING, null,
                        ReportItem.ERROR_NO_ERROR,
                        "noMatchingPluginFound", result.getUniqueId())); //$NON-NLS-1$
            }
            registrationReport.add(new ReportItemImpl(
                    ReportItem.SEVERITY_INFO, null,
                    ReportItem.ERROR_NO_ERROR,
                    "fragmentRegistered", result.getUniqueId())); //$NON-NLS-1$
        } catch (ManifestProcessingException mpe) {
            log.error("failed registering plug-in fragment descriptor, URL - " //$NON-NLS-1$
                    + model.getLocation(), mpe);
            if (stopOnError) {
                throw mpe;
            }
            registrationReport.add(new ReportItemImpl(
                    ReportItem.SEVERITY_ERROR, null,
                    ReportItem.ERROR_MANIFEST_PROCESSING_FAILED,
                    "fragmentRegistrationFailed", //$NON-NLS-1$
                    new Object[] {model.getLocation(), mpe}));
            return null;
        }
        registeredFragments.put(result.getId(), result);
        return result;
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#unregister(java.lang.String[])
     */
    public Collection unregister(final String[] ids) {
        // collecting registered extension points and extensions
        List registeredPoints = new LinkedList(); //<ExtensionPointImpl>
        Map registeredExtensions = new HashMap(); //<extensionUid, ExtensionImpl>
        for (Iterator it = registeredPlugins.values().iterator();
                it.hasNext();) {
            for (Iterator it2 = ((PluginDescriptor) it.next())
                    .getExtensionPoints().iterator();
                    it2.hasNext();) {
                ExtensionPoint point = (ExtensionPoint) it2.next();
                registeredPoints.add(point);
                for (Iterator it3 = point.getConnectedExtensions().iterator();
                        it3.hasNext();) {
                    Extension ext = (Extension) it3.next();
                    registeredExtensions.put(ext.getUniqueId(), ext);
                }
            }
        }
        Set result = new HashSet();
        RegistryChangeDataImpl registryChangeData =
            new RegistryChangeDataImpl();
        // collect objects to be unregistered
        registrationReport.add(new ReportItemImpl(
                ReportItem.SEVERITY_INFO, null,
                ReportItem.ERROR_NO_ERROR, "unregisteringPrepare", null)); //$NON-NLS-1$
        Map removingPlugins = new HashMap();
        Map removingFragments = new HashMap();
        for (int i = 0; i < ids.length; i++) {
            PluginDescriptor descr =
                (PluginDescriptor) registeredPlugins.get(ids[i]);
            if (descr != null) {
                for (Iterator it = getDependingPlugins(descr).iterator();
                        it.hasNext();) {
                    PluginDescriptor depDescr = (PluginDescriptor) it.next();
                    removingPlugins.put(depDescr.getId(), depDescr);
                    registryChangeData.removedPlugins().add(depDescr.getId());
                }
                removingPlugins.put(descr.getId(), descr);
                registryChangeData.removedPlugins().add(descr.getId());
                continue;
            }
            PluginFragment fragment =
                (PluginFragment) registeredFragments.get(ids[i]);
            if (fragment != null) {
                removingFragments.put(fragment.getId(), fragment);
                continue;
            }
            registrationReport.add(new ReportItemImpl(
                    ReportItem.SEVERITY_WARNING, null,
                    ReportItem.ERROR_NO_ERROR,
                    "pluginToUngregisterNotFound", ids[i])); //$NON-NLS-1$
        }
        // collect fragments for removing plug-ins
        for (Iterator it = removingPlugins.values().iterator(); it.hasNext();) {
            PluginDescriptor descr = (PluginDescriptor) it.next();
            for (Iterator it2 = descr.getFragments().iterator();
                    it2.hasNext();) {
                PluginFragment fragment = (PluginFragment) it2.next();
                if (removingFragments.containsKey(fragment.getId())) {
                    continue;
                }
                removingFragments.put(fragment.getId(), fragment);
            }
        }
        // notify about plug-ins removal first
        fireEvent(registryChangeData);
        registrationReport.add(new ReportItemImpl(ReportItem.SEVERITY_INFO,
                null, ReportItem.ERROR_NO_ERROR,
                "unregisteringFragmentsStart", null)); //$NON-NLS-1$
        for (Iterator it = removingFragments.values().iterator();
                it.hasNext();) {
            PluginFragmentImpl fragment = (PluginFragmentImpl) it.next();
            unregisterFragment(fragment);
            if (!removingPlugins.containsKey(fragment.getPluginId())) {
                registryChangeData.modifiedPlugins().add(
                        fragment.getPluginId());
            }
            result.add(fragment.getUniqueId());
        }
        removingFragments.clear();
        registrationReport.add(new ReportItemImpl(ReportItem.SEVERITY_INFO,
                null, ReportItem.ERROR_NO_ERROR,
                "unregisteringPluginsStart", null)); //$NON-NLS-1$
        for (Iterator it = removingPlugins.values().iterator(); it.hasNext();) {
            PluginDescriptorImpl descr = (PluginDescriptorImpl) it.next();
            unregisterPlugin(descr);
            result.add(descr.getUniqueId());
        }
        removingPlugins.clear();
        registrationReport.add(new ReportItemImpl(ReportItem.SEVERITY_INFO,
                null, ReportItem.ERROR_NO_ERROR,
                "unregisteringPluginsFinish", //$NON-NLS-1$
                new Integer(registeredPlugins.size())));
        registrationReport.add(new ReportItemImpl(ReportItem.SEVERITY_INFO,
                null, ReportItem.ERROR_NO_ERROR,
                "unregisteringFragmentsFinish", //$NON-NLS-1$
                new Integer(registeredFragments.size())));
        log.info("plug-in and fragment descriptors unregistered - " //$NON-NLS-1$
                + result.size());
        dump();
        if (result.isEmpty()) {
            return result;
        }
        // notify all interested members that plug-ins set has been changed
        for (Iterator it = registeredPoints.iterator(); it.hasNext();) {
            ((ExtensionPointImpl) it.next()).registryChanged();
        }
        for (Iterator it = registeredExtensions.values().iterator();
                it.hasNext();) {
            ((ExtensionImpl) it.next()).registryChanged();
        }
        if (!listeners.isEmpty() || log.isDebugEnabled()) {
            // analyze changes in extensions set
            for (Iterator it = registeredPlugins.values().iterator();
                    it.hasNext();) {
                for (Iterator it2 = ((PluginDescriptor) it.next())
                        .getExtensionPoints().iterator();
                        it2.hasNext();) {
                    for (Iterator it3 = ((ExtensionPoint) it2.next())
                            .getConnectedExtensions().iterator();
                            it3.hasNext();) {
                        Extension ext = (Extension) it3.next();
                        if (!registeredExtensions.containsKey(
                                ext.getUniqueId())) {
                            registryChangeData.putAddedExtension(
                                    ext.getUniqueId(),
                                    makeUniqueId(ext.getExtendedPluginId(),
                                            ext.getExtendedPointId()));
                        } else {
                            registeredExtensions.remove(ext.getUniqueId());
                            if (registryChangeData.modifiedPlugins().contains(
                                    ext.getDeclaringPluginDescriptor().getId())
                                    || registryChangeData.modifiedPlugins()
                                        .contains(ext.getExtendedPluginId())) {
                                registryChangeData.putModifiedExtension(
                                        ext.getUniqueId(),
                                        makeUniqueId(ext.getExtendedPluginId(),
                                                ext.getExtendedPointId()));
                            }
                        }
                    }
                }
            }
            for (Iterator it = registeredExtensions.values().iterator();
                    it.hasNext();) {
                Extension ext = (Extension) it.next();
                registryChangeData.putRemovedExtension(ext.getUniqueId(),
                        makeUniqueId(ext.getExtendedPluginId(),
                                ext.getExtendedPointId()));
            }
            // fire event
            fireEvent(registryChangeData);
        }
        return result;
    }
    
    private void unregisterPlugin(final PluginDescriptorImpl descr) {
        registeredPlugins.remove(descr.getId());
        registrationReport.add(new ReportItemImpl(
                ReportItem.SEVERITY_INFO, null,
                ReportItem.ERROR_NO_ERROR,
                "pluginUnregistered", descr.getUniqueId())); //$NON-NLS-1$
    }
    
    private void unregisterFragment(final PluginFragmentImpl fragment) {
        PluginDescriptorImpl descr =
            (PluginDescriptorImpl) registeredPlugins.get(
                    fragment.getPluginId());
        if (descr != null) {
            descr.unregisterFragment(fragment);
        }
        registeredFragments.remove(fragment.getId());
        registrationReport.add(new ReportItemImpl(
                ReportItem.SEVERITY_INFO, null,
                ReportItem.ERROR_NO_ERROR,
                "fragmentUnregistered", fragment.getUniqueId())); //$NON-NLS-1$
    }

    private void dump() {
        if (!log.isDebugEnabled()) {
            return;
        }
        StringBuffer buf = new StringBuffer();
        buf.append("PLUG-IN REGISTRY DUMP:\r\n") //$NON-NLS-1$
            .append("-------------- DUMP BEGIN -----------------\r\n") //$NON-NLS-1$
            .append("\tPlug-ins: " + registeredPlugins.size() //$NON-NLS-1$
                    + "\r\n"); //$NON-NLS-1$
        for (Iterator it = registeredPlugins.values().iterator();
                it.hasNext();) {
            buf.append("\t\t") //$NON-NLS-1$
                .append(it.next())
                .append("\r\n"); //$NON-NLS-1$
        }
        buf.append("\tFragments: " + registeredFragments.size() //$NON-NLS-1$
                + "\r\n"); //$NON-NLS-1$
        for (Iterator it = registeredFragments.values().iterator();
                it.hasNext();) {
            buf.append("\t\t") //$NON-NLS-1$
                .append(it.next())
                .append("\r\n"); //$NON-NLS-1$
        }
        buf.append("Memory TOTAL/FREE/MAX: ") //$NON-NLS-1$
            .append(Runtime.getRuntime().totalMemory())
            .append("/") //$NON-NLS-1$
            .append(Runtime.getRuntime().freeMemory())
            .append("/") //$NON-NLS-1$
            .append(Runtime.getRuntime().maxMemory())
            .append("\r\n"); //$NON-NLS-1$
        buf.append("-------------- DUMP END -----------------\r\n"); //$NON-NLS-1$
        log.debug(buf.toString());
    }
    
    /**
     * @see org.java.plugin.registry.PluginRegistry#getExtensionPoint(
     *      java.lang.String, java.lang.String)
     */
    public ExtensionPoint getExtensionPoint(final String pluginId,
            final String pointId) {
        PluginDescriptor descriptor =
            (PluginDescriptor) registeredPlugins.get(pluginId);
        if (descriptor == null) {
            throw new IllegalArgumentException("unknown plug-in ID " //$NON-NLS-1$
                + pluginId + " provided for extension point " + pointId); //$NON-NLS-1$
        }
        for (Iterator it = descriptor.getExtensionPoints().iterator();
                it.hasNext();) {
            ExtensionPoint point = (ExtensionPoint) it.next();
            if (point.getId().equals(pointId)) {
                if (point.isValid()) {
                    return point;
                }
                log.warn("extension point " + point.getUniqueId() //$NON-NLS-1$
                        + " is invalid and ignored by registry"); //$NON-NLS-1$
                break;
            }
        }
        throw new IllegalArgumentException("unknown extension point ID - " //$NON-NLS-1$
                + makeUniqueId(pluginId, pointId));
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#getExtensionPoint(java.lang.String)
     */
    public ExtensionPoint getExtensionPoint(final String uniqueId) {
        return getExtensionPoint(extractPluginId(uniqueId),
                extractId(uniqueId));
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#isExtensionPointAvailable(
     *      java.lang.String, java.lang.String)
     */
    public boolean isExtensionPointAvailable(final String pluginId,
            final String pointId) {
        PluginDescriptor descriptor =
            (PluginDescriptor) registeredPlugins.get(pluginId);
        if (descriptor == null) {
            return false;
        }
        for (Iterator it = descriptor.getExtensionPoints().iterator();
                it.hasNext();) {
            ExtensionPoint point = (ExtensionPoint) it.next();
            if (point.getId().equals(pointId)) {
                return point.isValid();
            }
        }
        return false;
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#isExtensionPointAvailable(
     *      java.lang.String)
     */
    public boolean isExtensionPointAvailable(final String uniqueId) {
        return isExtensionPointAvailable(extractPluginId(uniqueId),
                extractId(uniqueId));
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#getPluginDescriptor(java.lang.String)
     */
    public PluginDescriptor getPluginDescriptor(final String pluginId) {
        PluginDescriptor result =
            (PluginDescriptor) registeredPlugins.get(pluginId);
        if (result == null) {
            throw new IllegalArgumentException("unknown plug-in ID - " //$NON-NLS-1$
                    + pluginId);
        }
        return result;
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#isPluginDescriptorAvailable(java.lang.String)
     */
    public boolean isPluginDescriptorAvailable(final String pluginId) {
        return registeredPlugins.containsKey(pluginId);
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#getPluginDescriptors()
     */
    public Collection getPluginDescriptors() {
        return registeredPlugins.isEmpty() ? Collections.EMPTY_LIST
                : Collections.unmodifiableCollection(
                        registeredPlugins.values());
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#getPluginFragments()
     */
    public Collection getPluginFragments() {
        return registeredFragments.isEmpty() ? Collections.EMPTY_LIST
                : Collections.unmodifiableCollection(
                        registeredFragments.values());
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#getDependingPlugins(
     *      org.java.plugin.registry.PluginDescriptor)
     */
    public Collection getDependingPlugins(final PluginDescriptor descr) {
        Map result = new HashMap();
        for (Iterator it = getPluginDescriptors().iterator();
                it.hasNext();) {
            PluginDescriptor dependedDescr = (PluginDescriptor) it.next();
            if (dependedDescr.getId().equals(descr.getId())) {
                continue;
            }
            for (Iterator it2 = dependedDescr.getPrerequisites().iterator();
                    it2.hasNext();) {
                PluginPrerequisite pre = (PluginPrerequisite) it2.next();
                if (!pre.getPluginId().equals(descr.getId())
                        || !pre.matches()) {
                    continue;
                }
                if (!result.containsKey(dependedDescr.getId())) {
                    result.put(dependedDescr.getId(), dependedDescr);
                    for (Iterator it3 =
                            getDependingPlugins(dependedDescr).iterator();
                            it3.hasNext();) {
                        PluginDescriptor descriptor =
                            (PluginDescriptor) it3.next();
                        if (!result.containsKey(descriptor.getId())) {
                            result.put(descriptor.getId(), descriptor);
                        }
                    }
                }
                break;
            }
        }
        return result.values();
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#checkIntegrity(
     *      org.java.plugin.PathResolver)
     */
    public IntegrityCheckReport checkIntegrity(
            final PathResolver pathResolver) {
        return checkIntegrity(pathResolver, false);
    }
    
    /**
     * @see org.java.plugin.registry.PluginRegistry#checkIntegrity(
     *      org.java.plugin.PathResolver, boolean)
     */
    public IntegrityCheckReport checkIntegrity(final PathResolver pathResolver,
            final boolean includeRegistrationReport) {
        IntegrityChecker intergityCheckReport = new IntegrityChecker(this,
                includeRegistrationReport ? registrationReport
                        : Collections.EMPTY_LIST);
        intergityCheckReport.doCheck(pathResolver);
        return intergityCheckReport;
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#getRegistrationReport()
     */
    public IntegrityCheckReport getRegistrationReport() {
        return new IntegrityChecker(this, registrationReport);
    }
    
    /**
     * @see org.java.plugin.registry.PluginRegistry#makeUniqueId(
     *      java.lang.String, java.lang.String)
     */
    public String makeUniqueId(final String pluginId, final String id) {
        return pluginId + UNIQUE_SEPARATOR + id;
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#makeUniqueId(
     *      java.lang.String, org.java.plugin.registry.Version)
     */
    public String makeUniqueId(final String pluginId, final Version version) {
        return pluginId + UNIQUE_SEPARATOR + version;
    }
    
    /**
     * @see org.java.plugin.registry.PluginRegistry#extractPluginId(java.lang.String)
     */
    public String extractPluginId(final String uniqueId) {
        int p = uniqueId.indexOf(UNIQUE_SEPARATOR);
        if ((p <= 0) || (p >= (uniqueId.length() - 1))) {
            throw new IllegalArgumentException("invalid unique ID - " //$NON-NLS-1$
                    + uniqueId);
        }
        return uniqueId.substring(0, p);
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#extractId(java.lang.String)
     */
    public String extractId(final String uniqueId) {
        int p = uniqueId.indexOf(UNIQUE_SEPARATOR);
        if ((p <= 0) || (p >= (uniqueId.length() - 1))) {
            throw new IllegalArgumentException("invalid unique ID - " //$NON-NLS-1$
                    + uniqueId);
        }
        return uniqueId.substring(p + 1);
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#extractVersion(java.lang.String)
     */
    public Version extractVersion(final String uniqueId) {
        int p = uniqueId.indexOf(UNIQUE_SEPARATOR);
        if ((p <= 0) || (p >= (uniqueId.length() - 1))) {
            throw new IllegalArgumentException("invalid unique ID - " //$NON-NLS-1$
                    + uniqueId);
        }
        return Version.parse(uniqueId.substring(p + 1));
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#registerListener(
     *      org.java.plugin.registry.PluginRegistry.RegistryChangeListener)
     */
    public void registerListener(final RegistryChangeListener listener) {
        if (listeners.contains(listener)) {
            throw new IllegalArgumentException("listener " + listener //$NON-NLS-1$
                    + " already registered"); //$NON-NLS-1$
        }
        listeners.add(listener);
    }

    /**
     * @see org.java.plugin.registry.PluginRegistry#unregisterListener(
     *      org.java.plugin.registry.PluginRegistry.RegistryChangeListener)
     */
    public void unregisterListener(final RegistryChangeListener listener) {
        if (!listeners.remove(listener)) {
            log.warn("unknown listener " + listener); //$NON-NLS-1$
        }
    }
    
    void fireEvent(final RegistryChangeDataImpl data) {
        data.dump();
        if (listeners.isEmpty()) {
            return;
        }
        // make local copy
        RegistryChangeListener[] arr =
            (RegistryChangeListener[]) listeners.toArray(
                new RegistryChangeListener[listeners.size()]);
        data.beforeEventFire();
        if (log.isDebugEnabled()) {
            log.debug("propagating registry change event"); //$NON-NLS-1$
        }
        for (int i = 0; i < arr.length; i++) {
            arr[i].registryChanged(data);
        }
        if (log.isDebugEnabled()) {
            log.debug("registry change event propagated"); //$NON-NLS-1$
        }
        data.afterEventFire();
    }
    
    private static final class RegistryChangeDataImpl
            implements RegistryChangeData {
        private Set addedPlugins;
        private Set removedPlugins;
        private Set modifiedPlugins;
        private Map addedExtensions;
        private Map removedExtensions;
        private Map modifiedExtensions;
        
        protected RegistryChangeDataImpl() {
            reset();
        }
        
        private void reset() {
            addedPlugins = new HashSet();
            removedPlugins = new HashSet();
            modifiedPlugins = new HashSet();
            addedExtensions = new HashMap();
            removedExtensions = new HashMap();
            modifiedExtensions = new HashMap();
        }
        
        protected void beforeEventFire() {
            addedPlugins = Collections.unmodifiableSet(addedPlugins);
            removedPlugins = Collections.unmodifiableSet(removedPlugins);
            modifiedPlugins = Collections.unmodifiableSet(modifiedPlugins);
            addedExtensions = Collections.unmodifiableMap(addedExtensions);
            removedExtensions = Collections.unmodifiableMap(removedExtensions);
            modifiedExtensions =
                Collections.unmodifiableMap(modifiedExtensions);
        }
        
        protected void afterEventFire() {
            reset();
        }
        
        protected void dump() {
            Log logger = LogFactory.getLog(getClass());
            if (!logger.isDebugEnabled()) {
                return;
            }
            StringBuffer buf = new StringBuffer();
            buf.append("PLUG-IN REGISTRY CHANGES DUMP:\r\n") //$NON-NLS-1$
                .append("-------------- DUMP BEGIN -----------------\r\n") //$NON-NLS-1$
                .append("\tAdded plug-ins: " + addedPlugins.size() //$NON-NLS-1$
                        + "\r\n"); //$NON-NLS-1$
            for (Iterator it = addedPlugins.iterator();
                    it.hasNext();) {
                buf.append("\t\t") //$NON-NLS-1$
                    .append(it.next())
                    .append("\r\n"); //$NON-NLS-1$
            }
            buf.append("\tRemoved plug-ins: " + removedPlugins.size() //$NON-NLS-1$
                    + "\r\n"); //$NON-NLS-1$
            for (Iterator it = removedPlugins.iterator();
                    it.hasNext();) {
                buf.append("\t\t") //$NON-NLS-1$
                .append(it.next())
                .append("\r\n"); //$NON-NLS-1$
            }
            buf.append("\tModified plug-ins: " + modifiedPlugins.size() //$NON-NLS-1$
                    + "\r\n"); //$NON-NLS-1$
            for (Iterator it = modifiedPlugins.iterator();
                    it.hasNext();) {
                buf.append("\t\t") //$NON-NLS-1$
                .append(it.next())
                .append("\r\n"); //$NON-NLS-1$
            }
            buf.append("\tAdded extensions: " + addedExtensions.size() //$NON-NLS-1$
                    + "\r\n"); //$NON-NLS-1$
            for (Iterator it = addedExtensions.entrySet().iterator();
                    it.hasNext();) {
                buf.append("\t\t") //$NON-NLS-1$
                    .append(it.next())
                    .append("\r\n"); //$NON-NLS-1$
            }
            buf.append("\tRemoved extensions: " + removedExtensions.size() //$NON-NLS-1$
                    + "\r\n"); //$NON-NLS-1$
            for (Iterator it = removedExtensions.entrySet().iterator();
                    it.hasNext();) {
                buf.append("\t\t") //$NON-NLS-1$
                .append(it.next())
                .append("\r\n"); //$NON-NLS-1$
            }
            buf.append("\tModified extensions: " + modifiedExtensions.size() //$NON-NLS-1$
                    + "\r\n"); //$NON-NLS-1$
            for (Iterator it = modifiedExtensions.entrySet().iterator();
                    it.hasNext();) {
                buf.append("\t\t") //$NON-NLS-1$
                .append(it.next())
                .append("\r\n"); //$NON-NLS-1$
            }
            buf.append("Memory TOTAL/FREE/MAX: ") //$NON-NLS-1$
                .append(Runtime.getRuntime().totalMemory())
                .append("/") //$NON-NLS-1$
                .append(Runtime.getRuntime().freeMemory())
                .append("/") //$NON-NLS-1$
                .append(Runtime.getRuntime().maxMemory())
                .append("\r\n"); //$NON-NLS-1$
            buf.append("-------------- DUMP END -----------------\r\n"); //$NON-NLS-1$
            logger.debug(buf.toString());
        }
        
        /**
         * @see org.java.plugin.registry.PluginRegistry.RegistryChangeData#addedPlugins()
         */
        public Set addedPlugins() {
            return addedPlugins;
        }
        
        /**
         * @see org.java.plugin.registry.PluginRegistry.RegistryChangeData#
         *      removedPlugins()
         */
        public Set removedPlugins() {
            return removedPlugins;
        }

        /**
         * @see org.java.plugin.registry.PluginRegistry.RegistryChangeData#
         *      modifiedPlugins()
         */
        public Set modifiedPlugins() {
            return modifiedPlugins;
        }
        
        void putAddedExtension(final String extensionUid,
                final String extensionPointUid) {
            addedExtensions.put(extensionUid, extensionPointUid);
        }

        /**
         * @see org.java.plugin.registry.PluginRegistry.RegistryChangeData#
         *      addedExtensions()
         */
        public Set addedExtensions() {
            return addedExtensions.keySet();
        }

        /**
         * @see org.java.plugin.registry.PluginRegistry.RegistryChangeData#
         *      addedExtensions(java.lang.String)
         */
        public Set addedExtensions(final String extensionPointUid) {
            Set result = new HashSet();
            for (Iterator it = addedExtensions.entrySet().iterator();
                    it.hasNext();) {
                Map.Entry entry = (Map.Entry) it.next();
                if (entry.getValue().equals(extensionPointUid)) {
                    result.add(entry.getKey());
                }
            }
            return Collections.unmodifiableSet(result);
        }
        
        void putRemovedExtension(final String extensionUid,
                final String extensionPointUid) {
            removedExtensions.put(extensionUid, extensionPointUid);
        }
        
        /**
         * @see org.java.plugin.registry.PluginRegistry.RegistryChangeData#
         *      removedExtensions()
         */
        public Set removedExtensions() {
            return removedExtensions.keySet();
        }

        /**
         * @see org.java.plugin.registry.PluginRegistry.RegistryChangeData#
         *      removedExtensions(java.lang.String)
         */
        public Set removedExtensions(final String extensionPointUid) {
            Set result = new HashSet();
            for (Iterator it = removedExtensions.entrySet().iterator();
                    it.hasNext();) {
                Map.Entry entry = (Map.Entry) it.next();
                if (entry.getValue().equals(extensionPointUid)) {
                    result.add(entry.getKey());
                }
            }
            return Collections.unmodifiableSet(result);
        }
        
        void putModifiedExtension(final String extensionUid,
                final String extensionPointUid) {
            modifiedExtensions.put(extensionUid, extensionPointUid);
        }

        /**
         * @see org.java.plugin.registry.PluginRegistry.RegistryChangeData#
         *      modifiedExtensions()
         */
        public Set modifiedExtensions() {
            return modifiedExtensions.keySet();
        }

        /**
         * @see org.java.plugin.registry.PluginRegistry.RegistryChangeData#
         *      modifiedExtensions(java.lang.String)
         */
        public Set modifiedExtensions(final String extensionPointUid) {
            Set result = new HashSet();
            for (Iterator it = modifiedExtensions.entrySet().iterator();
                    it.hasNext();) {
                Map.Entry entry = (Map.Entry) it.next();
                if (entry.getValue().equals(extensionPointUid)) {
                    result.add(entry.getKey());
                }
            }
            return Collections.unmodifiableSet(result);
        }
    }
    
    private static final class ManifestInfoImpl implements ManifestInfo {
        private final ModelManifestInfo model;

        ManifestInfoImpl(final ModelManifestInfo aModel) {
            model = aModel;
        }
        
        /**
         * @see org.java.plugin.registry.PluginRegistry.ManifestInfo#getId()
         */
        public String getId() {
            return model.getId();
        }

        /**
         * @see org.java.plugin.registry.PluginRegistry.ManifestInfo#
         *      getVersion()
         */
        public Version getVersion() {
            return model.getVersion();
        }

        /**
         * @see org.java.plugin.registry.PluginRegistry.ManifestInfo#
         *      getVendor()
         */
        public String getVendor() {
            return model.getVendor();
        }

        /**
         * @see org.java.plugin.registry.PluginRegistry.ManifestInfo#
         *      getPluginId()
         */
        public String getPluginId() {
            return model.getPluginId();
        }

        /**
         * @see org.java.plugin.registry.PluginRegistry.ManifestInfo#
         *      getPluginVersion()
         */
        public Version getPluginVersion() {
            return model.getPluginVersion();
        }

        /**
         * @see org.java.plugin.registry.PluginRegistry.ManifestInfo#
         *      getMatchingRule()
         */
        public String getMatchingRule() {
            return model.getMatch();
        }
    }
}
