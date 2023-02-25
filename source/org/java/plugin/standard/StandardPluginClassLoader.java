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
package org.java.plugin.standard;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
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
import org.java.plugin.Plugin;
import org.java.plugin.PluginClassLoader;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Library;
import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.registry.PluginPrerequisite;
import org.java.plugin.registry.PluginRegistry;
import org.java.plugin.util.IoUtil;

/**
 * Standard implementation of plug-in class loader.
 * @version $Id: StandardPluginClassLoader.java,v 1.25 2007/04/07 12:43:21 ddimon Exp $
 */
public class StandardPluginClassLoader extends PluginClassLoader {
    static Log log = LogFactory.getLog(StandardPluginClassLoader.class);
    
    private static File libCacheFolder;
    private static boolean libCacheFolderInitialized = false;
    
    private static URL getClassBaseUrl(final Class cls) {
        ProtectionDomain pd = cls.getProtectionDomain();
        if (pd != null) {
            CodeSource cs = pd.getCodeSource();
            if (cs != null) {
                return cs.getLocation();
            }
        }
        return null;
    }

    private static URL[] getUrls(final PluginManager manager,
            final PluginDescriptor descr) {
        List result = new LinkedList();
        for (Iterator it = descr.getLibraries().iterator(); it.hasNext();) {
            Library lib = (Library) it.next();
            if (!lib.isCodeLibrary()) {
                continue;
            }
            result.add(manager.getPathResolver().resolvePath(lib,
                    lib.getPath()));
        }
        if (log.isDebugEnabled()) {
            StringBuffer buf = new StringBuffer();
            buf.append("Code URL's populated for plug-in " //$NON-NLS-1$
                    + descr + ":\r\n"); //$NON-NLS-1$
            for (Iterator it = result.iterator(); it.hasNext();) {
                buf.append("\t"); //$NON-NLS-1$
                buf.append(it.next());
                buf.append("\r\n"); //$NON-NLS-1$
            }
            log.debug(buf.toString());
        }
        return (URL[]) result.toArray(new URL[result.size()]);
    }
    
    private static URL[] getUrls(final PluginManager manager,
            final PluginDescriptor descr, final URL[] existingUrls) {
        List urls = Arrays.asList(existingUrls);
        List result = new LinkedList();
        for (Iterator it = descr.getLibraries().iterator(); it.hasNext();) {
            Library lib = (Library) it.next();
            if (!lib.isCodeLibrary()) {
                continue;
            }
            URL url = manager.getPathResolver().resolvePath(lib, lib.getPath());
            if (!urls.contains(url)) {
                result.add(url);
            }
        }
        return (URL[]) result.toArray(new URL[result.size()]);
    }
    
    private static File getLibCacheFolder() {
        if (libCacheFolder != null) {
            return libCacheFolderInitialized ? libCacheFolder : null;
        }
        synchronized (StandardPluginClassLoader.class) {
            libCacheFolder = new File(System.getProperty("java.io.tmpdir"), //$NON-NLS-1$
                    System.currentTimeMillis() + ".jpf-lib-cache"); //$NON-NLS-1$
            log.debug("libraries cache folder is " + libCacheFolder); //$NON-NLS-1$
            File lockFile = new File(libCacheFolder, "lock"); //$NON-NLS-1$
            if (lockFile.exists()) {
                log.error("can't initialize libraries cache folder " //$NON-NLS-1$
                        + libCacheFolder + " as lock file indicates that it" //$NON-NLS-1$
                        + " is owned by another JPF instance"); //$NON-NLS-1$
                return null;
            }
            if (libCacheFolder.exists()) {
                // clean up folder
                IoUtil.emptyFolder(libCacheFolder);
            } else {
                libCacheFolder.mkdirs();
            }
            try {
                if (!lockFile.createNewFile()) {
                    log.error("can\'t create lock file in JPF libraries cache" //$NON-NLS-1$
                            + " folder " + libCacheFolder); //$NON-NLS-1$
                    return null;
                }
            } catch (IOException ioe) {
                log.error("can\'t create lock file in JPF libraries cache" //$NON-NLS-1$
                        + " folder " + libCacheFolder, ioe); //$NON-NLS-1$
                return null;
            }
            lockFile.deleteOnExit();
            libCacheFolder.deleteOnExit();
            libCacheFolderInitialized = true;
        }
        return libCacheFolder;
    }
    
    private PluginDescriptor[] publicImports;
    private PluginDescriptor[] privateImports;
    private PluginDescriptor[] reverseLookups;
    private PluginResourceLoader resourceLoader;
    private Map resourceFilters; // <lib URL, ResourceFilter>
    private Map libraryCache; // <lib URL, File>
    private boolean probeParentLoaderLast;
    private boolean stickySynchronizing;
    private boolean localClassLoadingOptimization = true;
    private boolean foreignClassLoadingOptimization = true;
    private final Set localPackages = new HashSet(); //<String>
    private final Map pluginPackages = new HashMap(); //<String, PluginDescriptor>

    /**
     * Creates class instance configured to load classes and resources for
     * given plug-in.
     * @param aManager plug-in manager instance
     * @param descr plug-in descriptor
     * @param parent parent class loader, usually this is JPF "host"
     *        application class loader
     */
    public StandardPluginClassLoader(final PluginManager aManager,
            final PluginDescriptor descr, final ClassLoader parent) {
        super(aManager, descr, getUrls(aManager, descr), parent);
        collectImports();
        resourceLoader = PluginResourceLoader.get(aManager, descr);
        collectFilters();
        libraryCache = new HashMap();
    }
    
    protected void collectImports() {
        // collect imported plug-ins (exclude duplicates)
        Map publicImportsMap = new HashMap(); //<plug-in ID, PluginDescriptor>
        Map privateImportsMap = new HashMap(); //<plug-in ID, PluginDescriptor>
        PluginRegistry registry = getPluginDescriptor().getRegistry();
        for (Iterator it = getPluginDescriptor().getPrerequisites().iterator();
                it.hasNext();) {
            PluginPrerequisite pre = (PluginPrerequisite) it.next();
            if (!pre.matches()) {
                continue;
            }
            PluginDescriptor preDescr =
                registry.getPluginDescriptor(pre.getPluginId());
            if (pre.isExported()) {
                publicImportsMap.put(preDescr.getId(), preDescr);
            } else {
                privateImportsMap.put(preDescr.getId(), preDescr);
            }
        }
        publicImports = (PluginDescriptor[]) publicImportsMap.values().toArray(
                new PluginDescriptor[publicImportsMap.size()]);
        privateImports =
            (PluginDescriptor[]) privateImportsMap.values().toArray(
                new PluginDescriptor[privateImportsMap.size()]);
        // collect reverse look up plug-ins (exclude duplicates)
        Map reverseLookupsMap = new HashMap(); //<plug-in ID, PluginDescriptor>
        for (Iterator it = registry.getPluginDescriptors().iterator();
                it.hasNext();) {
            PluginDescriptor descr = (PluginDescriptor) it.next();
            if (descr.equals(getPluginDescriptor())
                    || publicImportsMap.containsKey(descr.getId())
                    || privateImportsMap.containsKey(descr.getId())) {
                continue;
            }
            for (Iterator it2 = descr.getPrerequisites().iterator();
                    it2.hasNext();) {
                PluginPrerequisite pre = (PluginPrerequisite) it2.next();
                if (!pre.getPluginId().equals(getPluginDescriptor().getId())
                        || !pre.isReverseLookup()) {
                    continue;
                }
                if (!pre.matches()) {
                    continue;
                }
                reverseLookupsMap.put(descr.getId(), descr);
                break;
            }
        }
        reverseLookups =
            (PluginDescriptor[]) reverseLookupsMap.values().toArray(
                new PluginDescriptor[reverseLookupsMap.size()]);
    }
    
    protected void collectFilters() {
        if (resourceFilters == null) {
            resourceFilters = new HashMap();
        } else {
            resourceFilters.clear();
        }
        for (Iterator it = getPluginDescriptor().getLibraries().iterator();
                it.hasNext();) {
            Library lib = (Library) it.next();
            resourceFilters.put(
                    getPluginManager().getPathResolver().resolvePath(lib,
                            lib.getPath()).toExternalForm(),
                            new ResourceFilter(lib));
        }
    }
    
    /**
     * @see org.java.plugin.PluginClassLoader#pluginsSetChanged()
     */
    protected void pluginsSetChanged() {
        URL[] newUrls = getUrls(getPluginManager(), getPluginDescriptor(),
                getURLs());
        for (int i = 0; i < newUrls.length; i++) {
            addURL(newUrls[i]);
        }
        if (log.isDebugEnabled()) {
            StringBuffer buf = new StringBuffer();
            buf.append("New code URL's populated for plug-in " //$NON-NLS-1$
                    + getPluginDescriptor() + ":\r\n"); //$NON-NLS-1$
            for (int i = 0; i < newUrls.length; i++) {
                buf.append("\t"); //$NON-NLS-1$
                buf.append(newUrls[i]);
                buf.append("\r\n"); //$NON-NLS-1$
            }
            log.debug(buf.toString());
        }
        collectImports();
        // repopulate resource URLs
        resourceLoader =
            PluginResourceLoader.get(getPluginManager(), getPluginDescriptor());
        collectFilters();
        for (Iterator it = libraryCache.entrySet().iterator(); it.hasNext();) {
            if (((Map.Entry) it.next()).getValue() == null) {
                it.remove();
            }
        }
        synchronized (localPackages) {
            localPackages.clear();
        }
        synchronized (pluginPackages) {
            pluginPackages.clear();
        }
    }
    
    /**
     * @see org.java.plugin.PluginClassLoader#dispose()
     */
    protected void dispose() {
        for (Iterator it = libraryCache.values().iterator(); it.hasNext();) {
            ((File) it.next()).delete();
        }
        libraryCache.clear();
        resourceFilters.clear();
        privateImports = null;
        publicImports = null;
        resourceLoader = null;
        synchronized (localPackages) {
            localPackages.clear();
        }
        synchronized (pluginPackages) {
            pluginPackages.clear();
        }
    }
    
    protected void setProbeParentLoaderLast(final boolean value) {
        probeParentLoaderLast = value;
    }
    
    protected void setStickySynchronizing(final boolean value) {
        stickySynchronizing = value;
    }
    
    protected void setLocalClassLoadingOptimization(final boolean value) {
        localClassLoadingOptimization = value;
    }
    
    protected void setForeignClassLoadingOptimization(final boolean value) {
        foreignClassLoadingOptimization = value;
    }

    /**
     * @see java.lang.ClassLoader#loadClass(java.lang.String, boolean)
     */
    protected Class loadClass(final String name, final boolean resolve)
            throws ClassNotFoundException {
        Class result;
        boolean tryLocal = true;
        if (isLocalClass(name)) {
            if (log.isDebugEnabled()) {
                log.debug("loadClass: trying local class guess, name=" //$NON-NLS-1$
                        + name + ", this=" + this); //$NON-NLS-1$
            }
            result = loadLocalClass(name, resolve, this);
            if (result != null) {
                if (log.isDebugEnabled()) {
                    log.debug("loadClass: local class guess succeeds, name=" //$NON-NLS-1$
                            + name + ", this=" + this); //$NON-NLS-1$
                }
                checkClassVisibility(result, this);
                return result;
            }
            tryLocal = false;
        }
        if (probeParentLoaderLast) {
            try {
                result = loadPluginClass(name, resolve, tryLocal, this, null);
            } catch (ClassNotFoundException cnfe) {
                result = getParent().loadClass(name);
            }
            if (result == null) {
                result = getParent().loadClass(name);
            }
        } else {
            try {
                result = getParent().loadClass(name);
            } catch (ClassNotFoundException cnfe) {
                result = loadPluginClass(name, resolve, tryLocal, this, null);
            }
        }
        if (result != null) {
            return result;
        }
        throw new ClassNotFoundException(name);
    }
    
    private Class loadLocalClass(final String name, final boolean resolve,
            final StandardPluginClassLoader requestor) {
        boolean debugEnabled = log.isDebugEnabled();
        synchronized (stickySynchronizing ? requestor : this) {
            Class result = findLoadedClass(name);
            if (result != null) {
                if (debugEnabled) {
                    log.debug("loadLocalClass: found loaded class, class=" //$NON-NLS-1$
                            + result + ", this=" //$NON-NLS-1$
                            + this + ", requestor=" + requestor); //$NON-NLS-1$
                }
                return result; // found already loaded class in this plug-in
            }
            try {
                result = findClass(name);
            } catch (LinkageError le) {
                if (debugEnabled) {
                    log.debug("loadLocalClass: class loading failed," //$NON-NLS-1$
                            + " name=" + name + ", this=" //$NON-NLS-1$ //$NON-NLS-2$
                            + this + ", requestor=" + requestor, le); //$NON-NLS-1$
                }
                throw le;
            } catch (ClassNotFoundException cnfe) {
                // ignore
            }
            if (result != null) {
                if (debugEnabled) {
                    log.debug("loadLocalClass: found class, class=" //$NON-NLS-1$
                            + result + ", this=" //$NON-NLS-1$
                            + this + ", requestor=" + requestor); //$NON-NLS-1$
                }
                if (resolve) {
                    resolveClass(result);
                }
                registerLocalPackage(result);
                return result; // found class in this plug-in
            }
        }
        return null;
    }
    
    protected Class loadPluginClass(final String name, final boolean resolve,
            final boolean tryLocal, final StandardPluginClassLoader requestor,
            final Set seenPlugins) throws ClassNotFoundException {
        Set seen = seenPlugins;
        if ((seen != null) && seen.contains(getPluginDescriptor().getId())) {
            return null;
        }
        if (seen == null) {
            seen = new HashSet();
        }
        seen.add(getPluginDescriptor().getId());
        if ((this != requestor)
                && !getPluginManager().isPluginActivated(getPluginDescriptor())
                && !getPluginManager().isPluginActivating(
                        getPluginDescriptor())) {
            String msg = "can't load class " + name + ", plug-in " //$NON-NLS-1$ //$NON-NLS-2$
                + getPluginDescriptor() + " is not activated yet"; //$NON-NLS-1$
            log.warn(msg);
            throw new ClassNotFoundException(msg);
        }
        Class result = null;
        boolean debugEnabled = log.isDebugEnabled();
        PluginDescriptor descr = guessPlugin(name);
        if ((descr != null) && !seen.contains(descr.getId())) {
            if (debugEnabled) {
                log.debug("loadPluginClass: trying plug-in guess, name=" //$NON-NLS-1$
                        + name + ", this=" //$NON-NLS-1$
                        + this + ", requestor=" + requestor); //$NON-NLS-1$
            }
            result = ((StandardPluginClassLoader) getPluginManager()
                    .getPluginClassLoader(descr)).loadPluginClass(name, resolve,
                            true, requestor, seen);
            if (result != null) {
                if (debugEnabled) {
                    log.debug("loadPluginClass: plug-in guess succeeds, name=" //$NON-NLS-1$
                            + name + ", this=" //$NON-NLS-1$
                            + this + ", requestor=" + requestor); //$NON-NLS-1$
                }
                return result;
            }
        }
        if (tryLocal) {
            result = loadLocalClass(name, resolve, requestor);
            if (result != null) {
                checkClassVisibility(result, requestor);
                return result;
            }
        }
        for (int i = 0; i < publicImports.length; i++) {
            if (seen.contains(publicImports[i].getId())) {
                continue;
            }
            result = ((StandardPluginClassLoader) getPluginManager()
                    .getPluginClassLoader(publicImports[i]))
                    .loadPluginClass(name, resolve, true, requestor, seen);
            if (result != null) {
                break; // found class in publicly imported plug-in
            }
        }
        if ((this == requestor) && (result == null)) {
            for (int i = 0; i < privateImports.length; i++) {
                if (seen.contains(privateImports[i].getId())) {
                    continue;
                }
                result = ((StandardPluginClassLoader) getPluginManager()
                        .getPluginClassLoader(privateImports[i]))
                        .loadPluginClass(name, resolve, true, requestor, seen);
                if (result != null) {
                    break; // found class in privately imported plug-in
                }
            }
        }
        if (result == null) {
            for (int i = 0; i < reverseLookups.length; i++) {
                if (seen.contains(reverseLookups[i].getId())) {
                    continue;
                }
                if (!getPluginManager().isPluginActivated(reverseLookups[i])
                        && !getPluginManager()
                        .isPluginActivating(reverseLookups[i])) {
                    continue;
                }
                result = ((StandardPluginClassLoader) getPluginManager()
                        .getPluginClassLoader(reverseLookups[i]))
                        .loadPluginClass(name, resolve, true, requestor, seen);
                if (result != null) {
                    break; // found class in plug-in that marks itself as
                           // allowed reverse look up
                }
            }
        }
        registerPluginPackage(result);
        return result;
    }
    
    private boolean isLocalClass(final String className) {
        if (!localClassLoadingOptimization) {
            return false;
        }
        String pkgName = getPackageName(className);
        if (pkgName == null) {
            return false;
        }
        return localPackages.contains(pkgName);
    }
    
    private void registerLocalPackage(final Class cls) {
        if (!localClassLoadingOptimization) {
            return;
        }
        String pkgName = getPackageName(cls.getName());
        if ((pkgName == null) || localPackages.contains(pkgName)) {
            return;
        }
        synchronized (localPackages) {
            localPackages.add(pkgName);
        }
        if (log.isDebugEnabled()) {
            log.debug("registered local package: name=" + pkgName); //$NON-NLS-1$
        }
    }

    private PluginDescriptor guessPlugin(final String className) {
        if (!foreignClassLoadingOptimization) {
            return null;
        }
        String pkgName = getPackageName(className);
        if (pkgName == null) {
            return null;
        }
        return (PluginDescriptor) pluginPackages.get(pkgName);
    }
    
    private void registerPluginPackage(final Class cls) {
        if (!foreignClassLoadingOptimization) {
            return;
        }
        Plugin plugin = getPluginManager().getPluginFor(cls);
        if (plugin == null) {
            return;
        }
        String pkgName = getPackageName(cls.getName());
        if ((pkgName == null) || pluginPackages.containsKey(pkgName)) {
            return;
        }
        synchronized (pluginPackages) {
            pluginPackages.put(pkgName, plugin.getDescriptor());
        }
        if (log.isDebugEnabled()) {
            log.debug("registered plug-in package: name=" + pkgName //$NON-NLS-1$
                    + ", plugin=" + plugin.getDescriptor()); //$NON-NLS-1$
        }
    }
    
    private String getPackageName(final String className) {
        int p = className.lastIndexOf('.');
        if (p == -1) {
            return null;
        }
        return className.substring(0, p);
    }
    
    protected void checkClassVisibility(final Class cls,
            final StandardPluginClassLoader requestor)
            throws ClassNotFoundException {
        if (this == requestor) {
            return;
        }
        URL lib = getClassBaseUrl(cls);
        if (lib == null) {
            return; // cls is a system class
        }
        ClassLoader loader = cls.getClassLoader();
        if (!(loader instanceof StandardPluginClassLoader)) {
            return;
        }
        if (loader != this) {
            ((StandardPluginClassLoader) loader).checkClassVisibility(cls,
                    requestor);
        } else {
            ResourceFilter filter =
                (ResourceFilter) resourceFilters.get(lib.toExternalForm());
            if (filter == null) {
                log.warn("class not visible, no class filter found, lib=" + lib //$NON-NLS-1$
                        + ", class=" + cls + ", this=" + this //$NON-NLS-1$ //$NON-NLS-2$
                        + ", requestor=" + requestor); //$NON-NLS-1$
                throw new ClassNotFoundException("class " //$NON-NLS-1$
                        + cls.getName() + " is not visible for plug-in " //$NON-NLS-1$
                        + requestor.getPluginDescriptor().getId()
                        + ", no filter found for library " + lib); //$NON-NLS-1$
            }
            if (!filter.isClassVisible(cls.getName())) {
                log.warn("class not visible, lib=" + lib //$NON-NLS-1$
                        + ", class=" + cls + ", this=" + this //$NON-NLS-1$ //$NON-NLS-2$
                        + ", requestor=" + requestor); //$NON-NLS-1$
                throw new ClassNotFoundException("class " //$NON-NLS-1$
                        + cls.getName() + " is not visible for plug-in " //$NON-NLS-1$
                        + requestor.getPluginDescriptor().getId());
            }
        }
    }

    /**
     * @see java.lang.ClassLoader#findLibrary(java.lang.String)
     */
    protected String findLibrary(final String name) {
        if ((name == null) || "".equals(name.trim())) { //$NON-NLS-1$
            return null;
        }
        if (log.isDebugEnabled()) {
            log.debug("findLibrary(String): name=" + name //$NON-NLS-1$
                    + ", this=" + this); //$NON-NLS-1$
        }
        String libname = System.mapLibraryName(name);
        String result = null;
        PathResolver pathResolver = getPluginManager().getPathResolver();
        for (Iterator it = getPluginDescriptor().getLibraries().iterator();
                it.hasNext();) {
            Library lib = (Library) it.next();
            if (lib.isCodeLibrary()) {
                continue;
            }
            URL libUrl = pathResolver.resolvePath(lib, lib.getPath() + libname);
            if (log.isDebugEnabled()) {
                log.debug("findLibrary(String): trying URL " + libUrl); //$NON-NLS-1$
            }
            File libFile = IoUtil.url2file(libUrl);
            if (libFile != null) {
                if (log.isDebugEnabled()) {
                    log.debug("findLibrary(String): URL " + libUrl //$NON-NLS-1$
                            + " resolved as local file " + libFile); //$NON-NLS-1$
                }
                if (libFile.isFile()) {
                    result = libFile.getAbsolutePath();
                    break;
                }
                continue;
            }
            // we have some kind of non-local URL
            // try to copy it to local temporary file
            libFile = (File) libraryCache.get(libUrl.toExternalForm());
            if (libFile != null) {
                if (libFile.isFile()) {
                    result = libFile.getAbsolutePath();
                    break;
                }
                libraryCache.remove(libUrl.toExternalForm());
            }
            if (libraryCache.containsKey(libUrl.toExternalForm())) {
                // already tried to cache this library
                break;
            }
            libFile = cacheLibrary(libUrl, libname);
            if (libFile != null) {
                result = libFile.getAbsolutePath();
                break;
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("findLibrary(String): name=" + name //$NON-NLS-1$
                    + ", libname=" + libname //$NON-NLS-1$
                    + ", result=" + result //$NON-NLS-1$
                    + ", this=" + this); //$NON-NLS-1$
        }
        return result;
    }

    protected synchronized File cacheLibrary(final URL libUrl,
            final String libname) {
        File cacheFolder = getLibCacheFolder();
        String libUrlStr = libUrl.toExternalForm();
        if (libraryCache.containsKey(libUrlStr)) {
            return (File) libraryCache.get(libUrlStr);
        }
        File result = null;
        try {
            if (cacheFolder == null) {
                throw new IOException(
                        "can't initialize libraries cache folder"); //$NON-NLS-1$
            }
            File libCachePluginFolder = new File(cacheFolder,
                    getPluginDescriptor().getUniqueId());
            if (!libCachePluginFolder.exists()
                    && !libCachePluginFolder.mkdirs()) {
                throw new IOException("can't create cache folder " //$NON-NLS-1$
                        + libCachePluginFolder);
            }
            result = new File(libCachePluginFolder, libname);
            InputStream in = IoUtil.getResourceInputStream(libUrl);
            try {
                OutputStream out = new BufferedOutputStream(
                        new FileOutputStream(result));
                try {
                    IoUtil.copyStream(in, out, 512);
                } finally {
                    out.close();
                }
            } finally {
                in.close();
            }
            libraryCache.put(libUrlStr, result);
            if (log.isDebugEnabled()) {
                log.debug("library " + libname //$NON-NLS-1$
                        + " successfully cached from URL " + libUrl //$NON-NLS-1$
                        + " and saved to local file " + result); //$NON-NLS-1$
            }
        } catch (IOException ioe) {
            log.error("can't cache library " + libname //$NON-NLS-1$
                    + " from URL " + libUrl, ioe); //$NON-NLS-1$
            libraryCache.put(libUrlStr, null);
            result = null;
        }
        return result;
    }

    /**
     * @see java.lang.ClassLoader#findResource(java.lang.String)
     */
    public URL findResource(final String name) {
        URL result = findResource(name, this, null);
        return result;
    }

    /**
     * @see java.lang.ClassLoader#findResources(java.lang.String)
     */
    public Enumeration findResources(final String name) throws IOException {
        List result = new LinkedList();
        findResources(result, name, this, null);
        return Collections.enumeration(result);
    }

    protected URL findResource(final String name,
            final StandardPluginClassLoader requestor, final Set seenPlugins) {
        Set seen = seenPlugins;
        if ((seen != null) && seen.contains(getPluginDescriptor().getId())) {
            return null;
        }
        URL result = super.findResource(name);
        if (result != null) { // found resource in this plug-in class path
            if (log.isDebugEnabled()) {
                log.debug("findResource(...): resource found in classpath, name=" //$NON-NLS-1$
                        + name + " URL=" + result + ", this=" //$NON-NLS-1$ //$NON-NLS-2$
                        + this + ", requestor=" + requestor); //$NON-NLS-1$
            }
            if (isResourceVisible(name, result, requestor)) {
                return result;
            }
            return null;
        }
        if (resourceLoader != null) {
            result = resourceLoader.findResource(name);
            if (result != null) { // found resource in this plug-in resource libraries
                if (log.isDebugEnabled()) {
                    log.debug("findResource(...): resource found in libraries, name=" //$NON-NLS-1$
                            + name + ", URL=" + result + ", this=" //$NON-NLS-1$ //$NON-NLS-2$
                            + this + ", requestor=" + requestor); //$NON-NLS-1$
                }
                if (isResourceVisible(name, result, requestor)) {
                    return result;
                }
                return null;
            }
        }
        if (seen == null) {
            seen = new HashSet();
        }
        if (log.isDebugEnabled()) {
            log.debug("findResource(...): resource not found, name=" //$NON-NLS-1$
                    + name + ", this=" //$NON-NLS-1$
                    + this + ", requestor=" + requestor); //$NON-NLS-1$
        }
        seen.add(getPluginDescriptor().getId());
        for (int i = 0; i < publicImports.length; i++) {
            if (seen.contains(publicImports[i].getId())) {
                continue;
            }
            result = ((StandardPluginClassLoader) getPluginManager()
                    .getPluginClassLoader(publicImports[i])).findResource(
                            name, requestor, seen);
            if (result != null) {
                break; // found resource in publicly imported plug-in
            }
        }
        if ((this == requestor) && (result == null)) {
            for (int i = 0; i < privateImports.length; i++) {
                if (seen.contains(privateImports[i].getId())) {
                    continue;
                }
                result = ((StandardPluginClassLoader) getPluginManager()
                        .getPluginClassLoader(privateImports[i])).findResource(
                                name, requestor, seen);
                if (result != null) {
                    break; // found resource in privately imported plug-in
                }
            }
        }
        if (result == null) {
            for (int i = 0; i < reverseLookups.length; i++) {
                if (seen.contains(reverseLookups[i].getId())) {
                    continue;
                }
                result = ((StandardPluginClassLoader) getPluginManager()
                        .getPluginClassLoader(reverseLookups[i])).findResource(
                                name, requestor, seen);
                if (result != null) {
                    break; // found resource in plug-in that marks itself as
                           // allowed reverse look up
                }
            }
        }
        return result;
    }

    protected void findResources(final List result, final String name,
            final StandardPluginClassLoader requestor, final Set seenPlugins)
            throws IOException {
        Set seen = seenPlugins;
        if ((seen != null) && seen.contains(getPluginDescriptor().getId())) {
            return;
        }
        for (Enumeration enm = super.findResources(name);
                enm.hasMoreElements();) {
            URL url = (URL) enm.nextElement();
            if (isResourceVisible(name, url, requestor)) {
                result.add(url);
            }
        }
        if (resourceLoader != null) {
            for (Enumeration enm = resourceLoader.findResources(name);
                    enm.hasMoreElements();) {
                URL url = (URL) enm.nextElement();
                if (isResourceVisible(name, url, requestor)) {
                    result.add(url);
                }
            }
        }
        if (seen == null) {
            seen = new HashSet();
        }
        seen.add(getPluginDescriptor().getId());
        for (int i = 0; i < publicImports.length; i++) {
            if (seen.contains(publicImports[i].getId())) {
                continue;
            }
            ((StandardPluginClassLoader) getPluginManager().getPluginClassLoader(
                    publicImports[i])).findResources(result, name,
                            requestor, seen);
        }
        if (this == requestor) {
            for (int i = 0; i < privateImports.length; i++) {
                if (seen.contains(privateImports[i].getId())) {
                    continue;
                }
                ((StandardPluginClassLoader) getPluginManager().getPluginClassLoader(
                        privateImports[i])).findResources(result, name,
                                requestor, seen);
            }
        }
        for (int i = 0; i < reverseLookups.length; i++) {
            if (seen.contains(reverseLookups[i].getId())) {
                continue;
            }
            ((StandardPluginClassLoader) getPluginManager().getPluginClassLoader(
                    reverseLookups[i])).findResources(result, name,
                            requestor, seen);
        }
    }
    
    protected boolean isResourceVisible(final String name, final URL url,
            final StandardPluginClassLoader requestor) {
        if (this == requestor) {
            return true;
        }
        URL lib;
        try {
            String file = url.getFile();
            lib = new URL(url.getProtocol(), url.getHost(),
                    file.substring(0, file.length() - name.length()));
        } catch (MalformedURLException mue) {
            log.error("can't get resource library URL", mue); //$NON-NLS-1$
            return false;
        }
        ResourceFilter filter =
            (ResourceFilter) resourceFilters.get(lib.toExternalForm());
        if (filter == null) {
            log.warn("no resource filter found for library " //$NON-NLS-1$
                    + lib + ", name=" + name //$NON-NLS-1$
                    + ", URL=" + url + ", this=" + this //$NON-NLS-1$ //$NON-NLS-2$
                    + ", requestor=" + requestor); //$NON-NLS-1$
            return false;
        }
        if (!filter.isResourceVisible(name)) {
            log.warn("resource not visible, name=" + name //$NON-NLS-1$
                    + ", URL=" + url + ", this=" + this //$NON-NLS-1$ //$NON-NLS-2$
                    + ", requestor=" + requestor); //$NON-NLS-1$
            return false;
        }
        return true;
    }
    
    protected static final class ResourceFilter {
        private boolean isPublic;
        private Set entries;

        protected ResourceFilter(final Library lib) {
            entries = new HashSet();
            for (Iterator it = lib.getExports().iterator(); it.hasNext();) {
                String exportPrefix = (String) it.next();
                if ("*".equals(exportPrefix)) { //$NON-NLS-1$
                    isPublic = true;
                    entries.clear();
                    break;
                }
                if (!lib.isCodeLibrary()) {
                    exportPrefix = exportPrefix.replace('\\', '.')
                        .replace('/', '.');
                    if (exportPrefix.startsWith(".")) { //$NON-NLS-1$
                        exportPrefix = exportPrefix.substring(1);
                    }
                }
                entries.add(exportPrefix);
            }
        }
        
        protected boolean isClassVisible(final String className) {
            if (isPublic) {
                return true;
            }
            if (entries.isEmpty()) {
                return false;
            }
            if (entries.contains(className)) {
                return true;
            }
            int p = className.lastIndexOf('.');
            if (p == -1) {
                return false;
            }
            return entries.contains(className.substring(0, p) + ".*"); //$NON-NLS-1$
        }

        protected boolean isResourceVisible(final String resPath) {
            // quick check
            if (isPublic) {
                return true;
            }
            if (entries.isEmpty()) {
                return false;
            }
            // translate "path spec" -> "full class name"
            String str = resPath.replace('\\', '.').replace('/', '.');
            if (str.startsWith(".")) { //$NON-NLS-1$
                str = str.substring(1);
            }
            if (str.endsWith(".")) { //$NON-NLS-1$
                str = str.substring(0, str.length() - 1);
            }
            return isClassVisible(str);
        }
    }

    protected static class PluginResourceLoader extends URLClassLoader {
        private static Log logger =
            LogFactory.getLog(PluginResourceLoader.class);

        static PluginResourceLoader get(final PluginManager manager,
                final PluginDescriptor descr) {
            final List urls = new LinkedList();
            for (Iterator it = descr.getLibraries().iterator(); it.hasNext();) {
                Library lib = (Library) it.next();
                if (lib.isCodeLibrary()) {
                    continue;
                }
                urls.add(manager.getPathResolver().resolvePath(lib,
                        lib.getPath()));
            }
            if (logger.isDebugEnabled()) {
                StringBuffer buf = new StringBuffer();
                buf.append("Resource URL's populated for plug-in " + descr //$NON-NLS-1$
                        + ":\r\n"); //$NON-NLS-1$
                for (Iterator it = urls.iterator(); it.hasNext();) {
                    buf.append("\t"); //$NON-NLS-1$
                    buf.append(it.next());
                    buf.append("\r\n"); //$NON-NLS-1$
                }
                logger.trace(buf.toString());
            }
            if (urls.isEmpty()) {
                return null;
            }
            return (PluginResourceLoader) AccessController.doPrivileged(
                    new PrivilegedAction() {
                public Object run() {
                    return new PluginResourceLoader(
                            (URL[]) urls.toArray(new URL[urls.size()]));
                }
            });
        }

        /**
         * Creates loader instance configured to load resources only from given
         * URLs.
         * @param urls array of resource URLs
         */
        PluginResourceLoader(final URL[] urls) {
            super(urls);
        }

        /**
         * @see java.lang.ClassLoader#findClass(java.lang.String)
         */
        protected Class findClass(final String name)
                throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }

        /**
         * @see java.lang.ClassLoader#loadClass(java.lang.String, boolean)
         */
        protected Class loadClass(final String name, final boolean resolve)
                throws ClassNotFoundException {
            throw new ClassNotFoundException(name);
        }
    }
}
